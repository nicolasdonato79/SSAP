-- Marcas de vehículos que trabaja cada vendedor, horarios de atención y email.

CREATE TABLE marca (
    id      SERIAL PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL,
    tipo    VARCHAR(10) NOT NULL CHECK (tipo IN ('AUTO', 'CAMION', 'MOTO')),
    activa  BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_marca UNIQUE (nombre, tipo)
);

CREATE TABLE usuario_marca (
    usuario_id  BIGINT  NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    marca_id    INTEGER NOT NULL REFERENCES marca(id),
    PRIMARY KEY (usuario_id, marca_id)
);
CREATE INDEX ix_usuario_marca_marca ON usuario_marca (marca_id);

-- Un rango horario por día (1 = lunes ... 7 = domingo).
CREATE TABLE usuario_horario (
    usuario_id  BIGINT   NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    dia         SMALLINT NOT NULL CHECK (dia BETWEEN 1 AND 7),
    desde       TIME     NOT NULL,
    hasta       TIME     NOT NULL,
    PRIMARY KEY (usuario_id, dia),
    CHECK (hasta > desde)
);

ALTER TABLE usuario ADD COLUMN email VARCHAR(150);
CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email)) WHERE email IS NOT NULL;

-- Catálogo inicial. Las que falten las agrega el administrador.
INSERT INTO marca (nombre, tipo) VALUES
-- Autos, camionetas y pickups
('Acura','AUTO'),('Alfa Romeo','AUTO'),('Aston Martin','AUTO'),('Audi','AUTO'),('BAIC','AUTO'),
('Bentley','AUTO'),('BMW','AUTO'),('Brilliance','AUTO'),('Buick','AUTO'),('BYD','AUTO'),
('Cadillac','AUTO'),('Changan','AUTO'),('Chery','AUTO'),('Chevrolet','AUTO'),('Chrysler','AUTO'),
('Citroën','AUTO'),('Cupra','AUTO'),('Dacia','AUTO'),('Daewoo','AUTO'),('Daihatsu','AUTO'),
('Datsun','AUTO'),('DFSK','AUTO'),('Dodge','AUTO'),('DS','AUTO'),('Exeed','AUTO'),
('FAW','AUTO'),('Ferrari','AUTO'),('Fiat','AUTO'),('Ford','AUTO'),('Foton','AUTO'),
('GAC','AUTO'),('Geely','AUTO'),('Genesis','AUTO'),('GMC','AUTO'),('Great Wall','AUTO'),
('Haval','AUTO'),('Honda','AUTO'),('Hongqi','AUTO'),('Hummer','AUTO'),('Hyundai','AUTO'),
('Infiniti','AUTO'),('Iran Khodro','AUTO'),('Isuzu','AUTO'),('JAC','AUTO'),('Jaguar','AUTO'),
('Jeep','AUTO'),('Jetour','AUTO'),('JMC','AUTO'),('Kia','AUTO'),('Lada','AUTO'),
('Lamborghini','AUTO'),('Lancia','AUTO'),('Land Rover','AUTO'),('Lexus','AUTO'),('Lifan','AUTO'),
('Lincoln','AUTO'),('Lotus','AUTO'),('Mahindra','AUTO'),('Maserati','AUTO'),('Mazda','AUTO'),
('McLaren','AUTO'),('Mercedes-Benz','AUTO'),('Mercury','AUTO'),('MG','AUTO'),('Mini','AUTO'),
('Mitsubishi','AUTO'),('Nissan','AUTO'),('Oldsmobile','AUTO'),('Opel','AUTO'),('Peugeot','AUTO'),
('Plymouth','AUTO'),('Polestar','AUTO'),('Pontiac','AUTO'),('Porsche','AUTO'),('Proton','AUTO'),
('RAM','AUTO'),('Renault','AUTO'),('Rolls-Royce','AUTO'),('Rover','AUTO'),('Saab','AUTO'),
('Saturn','AUTO'),('Scion','AUTO'),('Seat','AUTO'),('Skoda','AUTO'),('Smart','AUTO'),
('SsangYong / KGM','AUTO'),('Subaru','AUTO'),('Suzuki','AUTO'),('Tata','AUTO'),('Tesla','AUTO'),
('Toyota','AUTO'),('UAZ','AUTO'),('Venirauto','AUTO'),('Volkswagen','AUTO'),('Volvo','AUTO'),
('Zotye','AUTO'),('ZX Auto','AUTO'),
-- Camiones y buses
('Blue Bird','CAMION'),('DAF','CAMION'),('Dongfeng','CAMION'),('Encava','CAMION'),('Freightliner','CAMION'),
('Higer','CAMION'),('Hino','CAMION'),('International','CAMION'),('Iveco','CAMION'),('Kenworth','CAMION'),
('King Long','CAMION'),('Mack','CAMION'),('MAN','CAMION'),('Marcopolo','CAMION'),('Mercedes-Benz Camiones','CAMION'),
('Peterbilt','CAMION'),('Renault Trucks','CAMION'),('Scania','CAMION'),('Shacman','CAMION'),('Sinotruk','CAMION'),
('UD Trucks','CAMION'),('Volvo Trucks','CAMION'),('Western Star','CAMION'),('Yutong','CAMION'),
-- Motos
('Bajaj','MOTO'),('Benelli','MOTO'),('Bera','MOTO'),('CFMoto','MOTO'),('Ducati','MOTO'),
('Empire Keeway','MOTO'),('Haojue','MOTO'),('Harley-Davidson','MOTO'),('Hero','MOTO'),('Honda','MOTO'),
('Kawasaki','MOTO'),('KTM','MOTO'),('Kymco','MOTO'),('Loncin','MOTO'),('Royal Enfield','MOTO'),
('Skygo','MOTO'),('Suzuki','MOTO'),('SYM','MOTO'),('Triumph','MOTO'),('TVS','MOTO'),
('UM','MOTO'),('Vespa','MOTO'),('Yamaha','MOTO'),('Zongshen','MOTO');
