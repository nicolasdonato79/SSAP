-- Códigos de un solo uso para entrar con el teléfono.
CREATE TABLE codigo_otp (
    id           BIGSERIAL PRIMARY KEY,
    telefono     VARCHAR(30)  NOT NULL,
    codigo_hash  VARCHAR(100) NOT NULL,
    expira_en    TIMESTAMPTZ  NOT NULL,
    intentos     INTEGER      NOT NULL DEFAULT 0,
    usado        BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_codigo_otp_telefono ON codigo_otp (telefono, creado_en DESC);
