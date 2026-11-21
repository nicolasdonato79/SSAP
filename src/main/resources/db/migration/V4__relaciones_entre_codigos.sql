-- Relaciones entre números de pieza (códigos normalizados).
--   REEMPLAZO:   codigo_a (anterior) fue reemplazado por codigo_b (nuevo). Con dirección:
--                quien busca el anterior puede usar el nuevo, no necesariamente al revés.
--   EQUIVALENTE: codigo_a y codigo_b son intercambiables (se guarda con codigo_a < codigo_b).
-- Las relaciones sobreviven a las cargas de stock: son conocimiento sobre las piezas, no stock.
CREATE TABLE relacion_codigo (
    id                  BIGSERIAL PRIMARY KEY,
    codigo_a            VARCHAR(80)  NOT NULL,
    codigo_b            VARCHAR(80)  NOT NULL,
    codigo_a_original   VARCHAR(80)  NOT NULL,
    codigo_b_original   VARCHAR(80)  NOT NULL,
    tipo                VARCHAR(20)  NOT NULL CHECK (tipo IN ('REEMPLAZO', 'EQUIVALENTE')),
    usuario_id          BIGINT       REFERENCES usuario(id),
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_relacion_codigo UNIQUE (codigo_a, codigo_b, tipo),
    CONSTRAINT ck_relacion_distintos CHECK (codigo_a <> codigo_b)
);

CREATE INDEX ix_relacion_codigo_a ON relacion_codigo (codigo_a);
CREATE INDEX ix_relacion_codigo_b ON relacion_codigo (codigo_b);
