-- Perfil social: lo que ve el resto de tus grupos al tocar tu nombre. Todo es
-- opcional; una cuenta sin rellenar sigue siendo válida.
ALTER TABLE usuarios ADD COLUMN carrera VARCHAR(80);
ALTER TABLE usuarios ADD COLUMN ciclo INTEGER;
ALTER TABLE usuarios ADD COLUMN bio VARCHAR(160);
-- Etiquetas separadas por comas: una etiqueta nunca lleva comas (ver
-- PerfilSocialRequest), así que no hace falta una tabla aparte.
ALTER TABLE usuarios ADD COLUMN intereses VARCHAR(300);
-- Cuándo cambió la foto por última vez; NULL = sin foto. Viaja con el usuario
-- y con cada integrante para que el frontend sepa si tiene que pedirla.
ALTER TABLE usuarios ADD COLUMN foto_actualizada_en TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_ciclo CHECK (ciclo BETWEEN 1 AND 14);

-- La foto va en su propia tabla para no cargar sus bytes cada vez que se lee
-- un usuario. Siempre es un JPEG de 256 px que rehace el servidor (unos 20 KB):
-- cabe en Postgres sin depender de un almacenamiento de archivos, que en el
-- plan gratuito de Render se borra en cada reinicio.
CREATE TABLE fotos_perfil (
    usuario_id     UUID                        NOT NULL,
    contenido      BYTEA                       NOT NULL,
    actualizada_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_fotos_perfil PRIMARY KEY (usuario_id),
    CONSTRAINT fk_fotos_perfil_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios ON DELETE CASCADE
);
