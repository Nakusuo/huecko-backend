-- «Estoy libre»: cada persona decide avisar a sus grupos de que está libre y
-- hasta cuándo. Que alguien esté libre nunca se deduce de su horario para
-- enseñárselo a otros (RNF-02): solo sale si lo dice esa persona.
ALTER TABLE usuarios ADD COLUMN libre_hasta TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE usuarios ADD COLUMN libre_nota VARCHAR(60);
