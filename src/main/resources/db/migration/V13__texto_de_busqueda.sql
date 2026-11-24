-- El buscador encuentra cada palabra en el código, la descripción, la marca del repuesto,
-- el vehículo/modelo o la marca del vehículo ("disco ranger", "pastillas toyota", "bujia civic").
-- texto_busqueda lo completa un trigger: no hay que tocarlo desde la app.

-- Minúsculas y sin acentos, para que "bujia" encuentre "BUJÍA".
CREATE OR REPLACE FUNCTION texto_para_buscar(t TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS $$
SELECT translate(lower(coalesce(t, '')), 'áàäâãéèëêíìïîóòöôõúùüûñç', 'aaaaaeeeeiiiiooooouuuunc')
$$;

ALTER TABLE item_stock ADD COLUMN texto_busqueda TEXT;

CREATE OR REPLACE FUNCTION item_stock_texto_busqueda() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.texto_busqueda := texto_para_buscar(concat_ws(' ', NEW.codigo_original, NEW.descripcion,
            NEW.marca_repuesto, NEW.vehiculo, (SELECT m.nombre FROM marca m WHERE m.id = NEW.marca_id)));
    RETURN NEW;
END
$$;

CREATE TRIGGER tr_item_stock_texto_busqueda
    BEFORE INSERT OR UPDATE OF codigo_original, descripcion, marca_repuesto, vehiculo, marca_id
    ON item_stock
    FOR EACH ROW EXECUTE FUNCTION item_stock_texto_busqueda();

-- Completar lo que ya está cargado (dispara el trigger)
UPDATE item_stock SET descripcion = descripcion;

CREATE INDEX ix_item_stock_texto_trg ON item_stock USING gin (texto_busqueda gin_trgm_ops);
