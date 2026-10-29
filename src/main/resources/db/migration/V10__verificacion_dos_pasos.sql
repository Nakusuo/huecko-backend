-- Verificación en dos pasos (TOTP, RFC 6238): tras la contraseña se pide el
-- código de 6 dígitos de una app como Google Authenticator.
--
-- dos_pasos_secreto va cifrado (AES-GCM): quien lea la base no puede generar
-- códigos. Se guarda al iniciar la configuración y solo cuenta cuando
-- dos_pasos_activo es TRUE, después de comprobar un primer código.
-- dos_pasos_ultimo_paso es el último intervalo de 30 s aceptado: un código ya
-- usado no vale una segunda vez.
ALTER TABLE usuarios ADD COLUMN dos_pasos_secreto VARCHAR(200);
ALTER TABLE usuarios ADD COLUMN dos_pasos_activo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN dos_pasos_ultimo_paso BIGINT;
ALTER TABLE usuarios ADD COLUMN dos_pasos_activado_en TIMESTAMP(6) WITH TIME ZONE;

-- Códigos de respaldo de un solo uso, para entrar sin el móvil. Solo se
-- guarda su SHA-256: se enseñan una vez, al crearlos.
CREATE TABLE codigos_respaldo (
    id          UUID                        NOT NULL,
    usuario_id  UUID                        NOT NULL,
    codigo_hash VARCHAR(64)                 NOT NULL,
    usado_en    TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_codigos_respaldo PRIMARY KEY (id),
    CONSTRAINT fk_codigos_respaldo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios ON DELETE CASCADE
);
CREATE INDEX idx_codigos_respaldo_usuario ON codigos_respaldo (usuario_id);
