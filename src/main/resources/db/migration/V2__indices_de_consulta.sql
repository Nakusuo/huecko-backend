-- Índices para las consultas frecuentes que el esquema inicial no cubría.
-- Postgres no indexa las claves foráneas por sí solo, y las claves primarias
-- compuestas solo sirven para buscar por su primera columna.

-- "Mis grupos": se filtra por usuario, y la PK empieza por grupo_id.
create index idx_miembros_grupo_usuario on miembros_grupo (usuario_id);

-- Planes de un grupo.
create index idx_planes_grupo on planes (grupo_id);

-- Barrido de cierre (cada 60 s): planes PROPUESTO con el plazo vencido.
create index idx_planes_estado_plazo on planes (estado, plazo_votacion);

-- Ventanas de un plan.
create index idx_ventanas_plan_plan on ventanas_plan (plan_id);

-- Votos de una ventana: la PK empieza por usuario_id.
create index idx_votos_ventana_ventana on votos_ventana (ventana_id);
