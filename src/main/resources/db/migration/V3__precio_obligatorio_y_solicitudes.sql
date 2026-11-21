-- La descripción pasa a ser opcional (muchos listados solo traen código, cantidad y precio).
ALTER TABLE item_stock ALTER COLUMN descripcion DROP NOT NULL;

-- El precio pasa a ser obligatorio.
UPDATE item_stock SET precio_usd = 0 WHERE precio_usd IS NULL;
ALTER TABLE item_stock ALTER COLUMN precio_usd SET NOT NULL;

-- Pedido de un comprador a un vendedor por un ítem concreto.
-- Guarda una copia de los datos del ítem porque el stock se reemplaza entero en cada carga.
CREATE TABLE solicitud (
    id             BIGSERIAL PRIMARY KEY,
    comprador_id   BIGINT        NOT NULL REFERENCES usuario(id),
    vendedor_id    BIGINT        NOT NULL REFERENCES usuario(id),
    codigo         VARCHAR(80)   NOT NULL,
    descripcion    VARCHAR(300),
    precio_usd     NUMERIC(12,2) NOT NULL,
    cantidad       INTEGER       NOT NULL CHECK (cantidad > 0),
    estado         VARCHAR(20)   NOT NULL,
    creada_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    respondida_en  TIMESTAMPTZ
);

CREATE INDEX ix_solicitud_comprador ON solicitud (comprador_id, creada_en DESC);
CREATE INDEX ix_solicitud_vendedor  ON solicitud (vendedor_id, creada_en DESC);
