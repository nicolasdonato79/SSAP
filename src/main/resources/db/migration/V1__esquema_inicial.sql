CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Un usuario puede ser vendedor, comprador o ambos.
CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    nombre        VARCHAR(150) NOT NULL,
    telefono      VARCHAR(30)  NOT NULL UNIQUE,
    estado        VARCHAR(60),
    ciudad        VARCHAR(80),
    verificado    BOOLEAN      NOT NULL DEFAULT FALSE,
    ultima_carga  TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE item_stock (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL REFERENCES usuario(id),
    codigo_original     VARCHAR(80)  NOT NULL,
    codigo_normalizado  VARCHAR(80)  NOT NULL,
    descripcion         VARCHAR(300) NOT NULL,
    marca_repuesto      VARCHAR(80),
    vehiculo            VARCHAR(150),
    cantidad            INTEGER      NOT NULL CHECK (cantidad >= 0),
    precio_usd          NUMERIC(12,2),
    cargado_en          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_item_stock_codigo   ON item_stock (codigo_normalizado);
CREATE INDEX ix_item_stock_usuario  ON item_stock (usuario_id);
CREATE INDEX ix_item_stock_desc_trg ON item_stock USING gin (descripcion gin_trgm_ops);

CREATE TABLE importacion (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuario(id),
    nombre_archivo  VARCHAR(255) NOT NULL,
    estado          VARCHAR(20)  NOT NULL,
    filas_ok        INTEGER      NOT NULL DEFAULT 0,
    filas_error     INTEGER      NOT NULL DEFAULT 0,
    errores         TEXT,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    terminado_en    TIMESTAMPTZ
);

CREATE TABLE demanda (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL REFERENCES usuario(id),
    texto               VARCHAR(300) NOT NULL,
    codigo_normalizado  VARCHAR(80),
    estado              VARCHAR(20)  NOT NULL,
    creada_en           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_demanda_estado_codigo ON demanda (estado, codigo_normalizado);
