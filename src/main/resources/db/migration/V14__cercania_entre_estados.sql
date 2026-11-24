-- Para ordenar los resultados del más cercano al más lejano (el envío sale más barato).
-- Ubicación aproximada de cada estado: la de su capital.
CREATE TABLE estado_ubicacion (
    estado VARCHAR(60) PRIMARY KEY,
    lat    DOUBLE PRECISION NOT NULL,
    lon    DOUBLE PRECISION NOT NULL
);

INSERT INTO estado_ubicacion (estado, lat, lon) VALUES
    ('Amazonas',         5.66, -67.62),  -- Puerto Ayacucho
    ('Anzoátegui',      10.13, -64.69),  -- Barcelona
    ('Apure',            7.89, -67.47),  -- San Fernando de Apure
    ('Aragua',          10.25, -67.60),  -- Maracay
    ('Barinas',          8.62, -70.21),  -- Barinas
    ('Bolívar',          8.12, -63.55),  -- Ciudad Bolívar
    ('Carabobo',        10.16, -68.00),  -- Valencia
    ('Cojedes',          9.66, -68.58),  -- San Carlos
    ('Delta Amacuro',    9.06, -62.05),  -- Tucupita
    ('Distrito Capital',10.49, -66.88),  -- Caracas
    ('Falcón',          11.40, -69.67),  -- Coro
    ('Guárico',          9.91, -67.35),  -- San Juan de los Morros
    ('La Guaira',       10.60, -66.93),  -- La Guaira
    ('Lara',            10.07, -69.32),  -- Barquisimeto
    ('Mérida',           8.59, -71.14),  -- Mérida
    ('Miranda',         10.34, -67.04),  -- Los Teques
    ('Monagas',          9.75, -63.18),  -- Maturín
    ('Nueva Esparta',   11.03, -63.86),  -- La Asunción
    ('Portuguesa',       9.04, -69.74),  -- Guanare
    ('Sucre',           10.46, -64.17),  -- Cumaná
    ('Táchira',          7.77, -72.22),  -- San Cristóbal
    ('Trujillo',         9.37, -70.44),  -- Trujillo
    ('Yaracuy',         10.34, -68.74),  -- San Felipe
    ('Zulia',           10.65, -71.64);  -- Maracaibo

-- Distancia en línea recta (km) entre dos puntos. Con algún dato NULL devuelve NULL.
CREATE OR REPLACE FUNCTION distancia_km(lat1 DOUBLE PRECISION, lon1 DOUBLE PRECISION,
                                        lat2 DOUBLE PRECISION, lon2 DOUBLE PRECISION)
    RETURNS DOUBLE PRECISION LANGUAGE sql IMMUTABLE STRICT AS $$
SELECT 2 * 6371 * asin(sqrt(power(sin(radians(lat2 - lat1) / 2), 2)
        + cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lon2 - lon1) / 2), 2)))
$$;
