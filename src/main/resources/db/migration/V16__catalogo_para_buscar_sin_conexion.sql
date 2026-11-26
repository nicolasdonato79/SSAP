-- Issue #9: cada equipo baja también el stock de los OTROS vendedores (de las marcas que trabaja) para
-- poder buscar sin conexión. Ver SyncCatalogoService.

-- Las marcas de borrado de ítems guardan la marca del vehículo, para mandarle a cada equipo solo los
-- borrados de las marcas que bajó (y no los de todo el país).
ALTER TABLE sync_borrado ADD COLUMN marca_id INTEGER;
CREATE INDEX ix_sync_borrado_xid ON sync_borrado (sync_xid);

CREATE OR REPLACE FUNCTION sync_marcar_borrado_item() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO sync_borrado (entidad, uuid, usuario_id, marca_id) VALUES ('item', OLD.uuid, OLD.usuario_id, OLD.marca_id);
    RETURN OLD;
END
$$;

DROP TRIGGER tr_sync_borrado_item_stock ON item_stock;
CREATE TRIGGER tr_sync_borrado_item_stock
    AFTER DELETE ON item_stock
    FOR EACH ROW EXECUTE FUNCTION sync_marcar_borrado_item();

-- Para recorrer el stock de todos en orden de cambio (bajada en páginas).
CREATE INDEX ix_item_stock_sync_global ON item_stock (sync_xid, uuid);

-- Reemplazos y equivalencias: se bajan para buscar por código sin conexión. Solo se insertan (nunca se
-- modifican ni se borran), así que alcanza con la transacción que las insertó.
ALTER TABLE relacion_codigo ADD COLUMN sync_xid XID8 NOT NULL DEFAULT pg_current_xact_id();
CREATE INDEX ix_relacion_codigo_sync ON relacion_codigo (sync_xid);

-- Secreto para el alias de cada vendedor en el stock bajado. El nombre real no se baja (así nadie saca la
-- lista de vendedores para comprar por fuera); el alias sirve para agrupar los ítems de un mismo vendedor y
-- es distinto para cada usuario que baja, así no se pueden cruzar las listas de dos usuarios.
ALTER TABLE sync_estado ADD COLUMN alias_secreto TEXT NOT NULL
    DEFAULT md5(random()::text || clock_timestamp()::text) || md5(random()::text || clock_timestamp()::text);
