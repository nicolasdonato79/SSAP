-- Negociación de precio en los pedidos.
--   PENDIENTE    -> el vendedor responde: OFERTADA (tiene stock, con precio) o RECHAZADA (no lo tiene)
--   OFERTADA     -> el comprador: ACEPTADA, CONTRAOFERTA (otro precio) o CANCELADA
--   CONTRAOFERTA -> el vendedor: ACEPTADA (al precio del comprador) o RECHAZADA
-- precio_usd queda como el precio publicado al momento de pedir (puede ser NULL = a consultar).
ALTER TABLE solicitud ADD COLUMN precio_oferta        NUMERIC(12,2);
ALTER TABLE solicitud ADD COLUMN precio_contraoferta  NUMERIC(12,2);
ALTER TABLE solicitud ADD COLUMN actualizada_en       TIMESTAMPTZ;

-- Pedidos aceptados antes de este cambio: el precio acordado fue el publicado.
UPDATE solicitud SET precio_oferta = precio_usd WHERE estado = 'ACEPTADA' AND precio_oferta IS NULL;

CREATE INDEX ix_solicitud_vendedor_estado  ON solicitud (vendedor_id, estado);
CREATE INDEX ix_solicitud_comprador_estado ON solicitud (comprador_id, estado);
