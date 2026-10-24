-- Unirse a un grupo con un código, como en la app móvil, además del alta por
-- correo. NULL = desactivado: con el código a NULL nadie entra por esa vía.
ALTER TABLE grupos ADD COLUMN codigo_invitacion VARCHAR(16);

-- Los grupos que ya existen empiezan con uno. md5 da 0-9 y a-f; el 0 y el 1
-- se cambian por 2 y 3 para no confundirlos con la O y la I al dictarlo. Solo
-- lo ve quien organiza, y puede cambiarlo o desactivarlo cuando quiera.
UPDATE grupos
SET codigo_invitacion = translate(upper(substr(md5(random()::text || id::text), 1, 8)), '01', '23');

-- Único entre los grupos que lo tienen (Postgres admite varios NULL).
CREATE UNIQUE INDEX ux_grupos_codigo_invitacion ON grupos (codigo_invitacion);
