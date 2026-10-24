-- Muro del grupo: cuándo entró cada persona. Es lo único del muro que no se
-- podía sacar de los datos que ya había (planes, fotos, llegadas). Las
-- membresías anteriores quedan en NULL: de ellas no se sabe la fecha y el muro
-- no las enseña.
ALTER TABLE miembros_grupo ADD COLUMN unido_en TIMESTAMP(6) WITH TIME ZONE;
