-- Issue #1: alta automática de usuarios.
-- Un usuario que se registra solo queda "pendiente" hasta verificar el teléfono (código por SMS)
-- y el email (link). Mientras esté pendiente no puede entrar.
ALTER TABLE usuario ADD COLUMN telefono_verificado     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN email_verificado        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN pendiente_verificacion  BOOLEAN NOT NULL DEFAULT FALSE;

-- Links de un solo uso que se mandan por email: activar la cuenta y recuperar la contraseña.
-- Se guarda solo el hash (SHA-256) del token, nunca el token.
CREATE TABLE token_usuario (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tipo        VARCHAR(30) NOT NULL CHECK (tipo IN ('VERIFICAR_EMAIL', 'RECUPERAR_PASSWORD')),
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ NOT NULL,
    usado_en    TIMESTAMPTZ,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_token_usuario_usuario ON token_usuario (usuario_id, tipo, creado_en DESC);

-- (Para entrar con el email alcanza el índice único ux_usuario_email de V6.)
