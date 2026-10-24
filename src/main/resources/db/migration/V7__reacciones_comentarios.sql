-- Reacciones y comentarios en las fotos del álbum. Se borran con la foto.

-- Una persona puede dejar varias reacciones distintas en una foto, pero cada
-- una solo una vez: por eso la reacción forma parte de la clave.
CREATE TABLE reacciones_foto (
    foto_id    UUID                        NOT NULL,
    usuario_id UUID                        NOT NULL,
    reaccion   VARCHAR(12)                 NOT NULL,
    creada_en  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reacciones_foto PRIMARY KEY (foto_id, usuario_id, reaccion),
    CONSTRAINT fk_reacciones_foto_foto FOREIGN KEY (foto_id) REFERENCES fotos_plan ON DELETE CASCADE,
    CONSTRAINT fk_reacciones_foto_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios ON DELETE CASCADE,
    CONSTRAINT ck_reacciones_foto_reaccion CHECK (reaccion IN ('CORAZON', 'RISA', 'FUEGO', 'ASOMBRO', 'APLAUSO'))
);

CREATE TABLE comentarios_foto (
    id        UUID                        NOT NULL,
    foto_id   UUID                        NOT NULL,
    autor_id  UUID                        NOT NULL,
    texto     VARCHAR(300)                NOT NULL,
    creado_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_comentarios_foto PRIMARY KEY (id),
    CONSTRAINT fk_comentarios_foto_foto FOREIGN KEY (foto_id) REFERENCES fotos_plan ON DELETE CASCADE,
    CONSTRAINT fk_comentarios_foto_autor FOREIGN KEY (autor_id) REFERENCES usuarios ON DELETE CASCADE
);

CREATE INDEX ix_comentarios_foto_foto ON comentarios_foto (foto_id, creado_en);
