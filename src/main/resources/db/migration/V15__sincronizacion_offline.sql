-- Issue #9: la app funciona sin conexión (PWA) y sincroniza con el servidor cuando vuelve.
--
-- Cada fila que se sincroniza lleva:
--   uuid           id que puede generar el equipo sin conexión (UUIDv7) y que no cambia nunca
--   version        número que cambia en cada modificación: el equipo manda la versión que vio
--                  (base_version) y así el servidor detecta si alguien la cambió mientras tanto
--   sync_xid       transacción que hizo el último cambio. El equipo baja "lo que cambió desde la
--                  transacción X": así no se saltea cambios de transacciones que terminaron tarde
--                  (con fechas o con una secuencia sí puede pasar)
--   actualizado_en hora del servidor del último cambio (para "gana el último cambio")
-- Los tres los completa el trigger: no hay que tocarlos desde la app.

CREATE SEQUENCE sync_version;

-- Pone version/sync_xid/actualizado_en. En un UPDATE que no cambia nada de lo que se sincroniza
-- (ej. solo cargado_en al confirmar el stock con un Excel igual) los deja como estaban, para que
-- los equipos no vuelvan a bajar esa fila. TG_ARGV = columnas extra que no cuentan como cambio.
CREATE OR REPLACE FUNCTION sync_marcar_cambio() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
    ignorar TEXT[] := ARRAY['version', 'sync_xid', 'actualizado_en'] || TG_ARGV;
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(NEW) - ignorar) = (to_jsonb(OLD) - ignorar) THEN
        NEW.version := OLD.version;
        NEW.sync_xid := OLD.sync_xid;
        NEW.actualizado_en := OLD.actualizado_en;
        RETURN NEW;
    END IF;
    NEW.version := nextval('sync_version');
    NEW.sync_xid := pg_current_xact_id();
    NEW.actualizado_en := now();
    RETURN NEW;
END
$$;

-- ---------- Marcas de borrado ----------
-- Las tablas siguen borrando con DELETE (así ninguna consulta actual cambia), pero cada borrado
-- deja una marca acá para que los equipos se enteren. Se purgan a los 30 días (SyncLimpieza).
CREATE TABLE sync_borrado (
    id          BIGSERIAL   PRIMARY KEY,
    entidad     VARCHAR(20) NOT NULL,
    uuid        UUID        NOT NULL,
    usuario_id  BIGINT      NOT NULL,
    sync_xid    XID8        NOT NULL DEFAULT pg_current_xact_id(),
    borrado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sync_borrado_usuario ON sync_borrado (usuario_id, sync_xid);
CREATE INDEX ix_sync_borrado_uuid    ON sync_borrado (uuid);
CREATE INDEX ix_sync_borrado_fecha   ON sync_borrado (borrado_en);

-- TG_ARGV[0] = nombre de la entidad para el equipo ("item", "demanda").
CREATE OR REPLACE FUNCTION sync_marcar_borrado() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO sync_borrado (entidad, uuid, usuario_id) VALUES (TG_ARGV[0], OLD.uuid, OLD.usuario_id);
    RETURN OLD;
END
$$;

-- Hasta qué transacción se purgaron marcas de borrado: un equipo que no sincroniza desde antes
-- tiene que bajar todo de nuevo (no puede saber qué se borró).
CREATE TABLE sync_estado (
    id                SMALLINT PRIMARY KEY CHECK (id = 1),
    purgado_hasta_xid XID8     NOT NULL DEFAULT '0'
);
INSERT INTO sync_estado (id) VALUES (1);

-- ---------- item_stock ----------
ALTER TABLE item_stock ADD COLUMN uuid           UUID        NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE item_stock ADD COLUMN version        BIGINT      NOT NULL DEFAULT nextval('sync_version');
ALTER TABLE item_stock ADD COLUMN sync_xid       XID8        NOT NULL DEFAULT pg_current_xact_id();
ALTER TABLE item_stock ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE item_stock ADD CONSTRAINT uq_item_stock_uuid UNIQUE (uuid);
CREATE INDEX ix_item_stock_sync ON item_stock (usuario_id, sync_xid);

-- "tr_zz_" para que corra después de tr_item_stock_texto_busqueda (los BEFORE van por orden alfabético).
-- cargado_en y texto_busqueda no cuentan como cambio: cargado_en se toca en cada carga de Excel
-- aunque el ítem quede igual, y texto_busqueda sale de las otras columnas.
CREATE TRIGGER tr_zz_sync_item_stock
    BEFORE INSERT OR UPDATE ON item_stock
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_cambio('cargado_en', 'texto_busqueda');

CREATE TRIGGER tr_sync_borrado_item_stock
    AFTER DELETE ON item_stock
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_borrado('item');

-- ---------- solicitud (pedidos) ----------
ALTER TABLE solicitud ADD COLUMN uuid           UUID        NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE solicitud ADD COLUMN version        BIGINT      NOT NULL DEFAULT nextval('sync_version');
ALTER TABLE solicitud ADD COLUMN sync_xid       XID8        NOT NULL DEFAULT pg_current_xact_id();
ALTER TABLE solicitud ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE solicitud ADD CONSTRAINT uq_solicitud_uuid UNIQUE (uuid);
CREATE INDEX ix_solicitud_sync_comprador ON solicitud (comprador_id, sync_xid);
CREATE INDEX ix_solicitud_sync_vendedor  ON solicitud (vendedor_id, sync_xid);

CREATE TRIGGER tr_zz_sync_solicitud
    BEFORE INSERT OR UPDATE ON solicitud
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_cambio();

-- ---------- demanda ("avisarme cuando aparezca") ----------
ALTER TABLE demanda ADD COLUMN uuid           UUID        NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE demanda ADD COLUMN version        BIGINT      NOT NULL DEFAULT nextval('sync_version');
ALTER TABLE demanda ADD COLUMN sync_xid       XID8        NOT NULL DEFAULT pg_current_xact_id();
ALTER TABLE demanda ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE demanda ADD CONSTRAINT uq_demanda_uuid UNIQUE (uuid);
CREATE INDEX ix_demanda_sync ON demanda (usuario_id, sync_xid);

CREATE TRIGGER tr_zz_sync_demanda
    BEFORE INSERT OR UPDATE ON demanda
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_cambio();

CREATE TRIGGER tr_sync_borrado_demanda
    AFTER DELETE ON demanda
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_borrado('demanda');

-- ---------- Equipos (celular, PC del local) ----------
-- El id lo genera el equipo al instalar la app. El token se guarda como hash SHA-256, nunca el token
-- (mismo criterio que token_usuario). Vence a los 30 días sin uso; el admin o el usuario lo pueden revocar.
CREATE TABLE dispositivo (
    id           UUID         PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    nombre       VARCHAR(100),
    token_hash   CHAR(64)     NOT NULL UNIQUE,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ultimo_uso   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expira_en    TIMESTAMPTZ  NOT NULL,
    revocado_en  TIMESTAMPTZ
);
CREATE INDEX ix_dispositivo_usuario ON dispositivo (usuario_id);

-- ---------- Operaciones recibidas (para que reintentar no duplique nada) ----------
-- Cada cambio hecho en el equipo viaja con un op_id (UUID). Si llega dos veces, la segunda se
-- contesta con el resultado guardado de la primera. Se purgan a los 30 días.
CREATE TABLE sync_operacion (
    op_id          UUID         PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    dispositivo_id UUID,
    tipo           VARCHAR(40)  NOT NULL,
    entidad_uuid   UUID,
    resultado      VARCHAR(30)  NOT NULL,
    mensaje        VARCHAR(500),
    version        BIGINT,
    hecha_en       TIMESTAMPTZ,
    recibida_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_sync_operacion_fecha ON sync_operacion (recibida_en);
