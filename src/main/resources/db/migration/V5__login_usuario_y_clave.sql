-- Login con usuario y contraseña (el login por código de WhatsApp queda para más adelante).
ALTER TABLE usuario ADD COLUMN username      VARCHAR(50);
ALTER TABLE usuario ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE usuario ADD COLUMN rol           VARCHAR(20) NOT NULL DEFAULT 'USUARIO'
    CHECK (rol IN ('USUARIO', 'ADMIN'));
ALTER TABLE usuario ADD COLUMN activo        BOOLEAN     NOT NULL DEFAULT TRUE;

-- Teléfono, estado y ciudad se completan en "Mi perfil" después del primer ingreso.
ALTER TABLE usuario ALTER COLUMN telefono DROP NOT NULL;

-- Usuarios que hayan quedado de las pruebas con código: usuario = su teléfono, sin contraseña (no pueden entrar).
UPDATE usuario SET username = regexp_replace(telefono, '\D', '', 'g') WHERE username IS NULL;
ALTER TABLE usuario ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX ux_usuario_username ON usuario (lower(username));

-- Usuarios iniciales (contraseñas con BCrypt). Conviene cambiarlas desde Administración.
INSERT INTO usuario (username, password_hash, rol, nombre) VALUES
    ('admin',  '$2a$10$X2VDGLk8AnKyngG5TIj31uP415P7mFE6htWdPQGVI0/1LRk8fjn4m', 'ADMIN',   'Administrador'),
    ('jean',   '$2a$10$yprOS/f2XRI1oDbrlz3JI.z1RrOWqAbiyvn82SzktwTSMlmjDQbzy', 'USUARIO', 'Jean'),
    ('carlos', '$2a$10$HmN9al156wEAtMeVzb1s3eahvvVc12p5IwR6yYyvjnFriwaE6ylZu', 'USUARIO', 'Carlos');
