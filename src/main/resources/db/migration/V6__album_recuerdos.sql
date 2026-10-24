-- Álbum de recuerdos: fotos de un plan, subidas por la gente del grupo una
-- vez que el plan empezó. Cada foto llega rehecha por el servidor (JPEG de
-- 1280 px como mucho, sin metadatos) y con su miniatura de 320 px, que es lo
-- que pinta la cuadrícula. Viven en Postgres con un tope por grupo (ver
-- AlbumService): el disco del plan gratuito de Render se borra al reiniciar.
CREATE TABLE fotos_plan (
    id         UUID                        NOT NULL,
    plan_id    UUID                        NOT NULL,
    grupo_id   UUID                        NOT NULL,
    autor_id   UUID                        NOT NULL,
    contenido  BYTEA                       NOT NULL,
    miniatura  BYTEA                       NOT NULL,
    ancho      INTEGER                     NOT NULL,
    alto       INTEGER                     NOT NULL,
    subida_en  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_fotos_plan PRIMARY KEY (id),
    CONSTRAINT fk_fotos_plan_plan FOREIGN KEY (plan_id) REFERENCES planes ON DELETE CASCADE,
    CONSTRAINT fk_fotos_plan_grupo FOREIGN KEY (grupo_id) REFERENCES grupos ON DELETE CASCADE,
    CONSTRAINT fk_fotos_plan_autor FOREIGN KEY (autor_id) REFERENCES usuarios ON DELETE CASCADE
);

CREATE INDEX ix_fotos_plan_plan ON fotos_plan (plan_id, subida_en);
CREATE INDEX ix_fotos_plan_grupo ON fotos_plan (grupo_id, subida_en);
