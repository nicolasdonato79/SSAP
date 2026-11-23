-- Filtros de búsqueda por marca y modelo del vehículo: buscan texto dentro de vehiculo y marca_repuesto.
CREATE INDEX IF NOT EXISTS ix_item_stock_vehiculo_trg ON item_stock USING gin (vehiculo gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_item_stock_marca_trg    ON item_stock USING gin (marca_repuesto gin_trgm_ops);
