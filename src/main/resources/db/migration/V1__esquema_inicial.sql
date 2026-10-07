-- Esquema relacional inicial de Huecko (Postgres).
--
-- Es el mismo que generaba Hibernate con `ddl-auto` a partir de las entidades
-- de com.huecko.backend.postgres.entity. Desde aquí el esquema lo lleva Flyway:
-- cualquier cambio en una entidad necesita su propia migración V<n>__*.sql.
--
-- Los bloques de horario, ausencias, retrasos, votaciones exprés, fallos y
-- reportes viven en Mongo y no pasan por aquí.

create table usuarios (
    id              uuid         not null,
    nombre          varchar(120) not null,
    email           varchar(180) not null,
    password_hash   varchar(255) not null,
    creado_en       timestamp(6) with time zone not null,
    rol_sistema     varchar(20)  not null default 'USUARIO',
    suspendido      boolean      not null default false,
    constraint pk_usuarios primary key (id),
    constraint uk_usuarios_email unique (email),
    constraint ck_usuarios_rol_sistema check (rol_sistema in ('USUARIO', 'ADMIN'))
);

create table grupos (
    id                    uuid         not null,
    nombre                varchar(120) not null,
    descripcion           varchar(400),
    umbral_disponibilidad integer      not null,
    creado_por            uuid         not null,
    creado_en             timestamp(6) with time zone not null,
    constraint pk_grupos primary key (id),
    constraint fk_grupos_creado_por foreign key (creado_por) references usuarios
);

create table miembros_grupo (
    grupo_id          uuid        not null,
    usuario_id        uuid        not null,
    rol               varchar(20) not null,
    es_imprescindible boolean     not null,
    constraint pk_miembros_grupo primary key (grupo_id, usuario_id),
    constraint ck_miembros_grupo_rol check (rol in ('ORGANIZADOR', 'MIEMBRO')),
    constraint fk_miembros_grupo_grupo foreign key (grupo_id) references grupos,
    constraint fk_miembros_grupo_usuario foreign key (usuario_id) references usuarios
);

create table planes (
    id                    uuid         not null,
    grupo_id              uuid         not null,
    titulo                varchar(120) not null,
    lugar                 varchar(200),
    estado                varchar(20)  not null,
    votos_multiples       boolean      not null,
    plazo_votacion        timestamp(6) with time zone not null,
    ventana_confirmada_id uuid,
    creado_por            uuid         not null,
    creado_en             timestamp(6) with time zone not null,
    cerrado_en            timestamp(6) with time zone,
    constraint pk_planes primary key (id),
    constraint ck_planes_estado check (estado in ('PROPUESTO', 'CONFIRMADO', 'CANCELADO', 'EN_RECOORDINACION')),
    constraint fk_planes_grupo foreign key (grupo_id) references grupos,
    constraint fk_planes_creado_por foreign key (creado_por) references usuarios
);

create table ventanas_plan (
    id                        uuid    not null,
    plan_id                   uuid    not null,
    fecha                     date    not null,
    hora_inicio               time(6) not null,
    hora_fin                  time(6) not null,
    disponibilidad_porcentaje integer not null,
    constraint pk_ventanas_plan primary key (id),
    constraint fk_ventanas_plan_plan foreign key (plan_id) references planes
);

-- planes y ventanas_plan se referencian entre sí: esta va después de crear las dos.
alter table planes
    add constraint fk_planes_ventana_confirmada
    foreign key (ventana_confirmada_id) references ventanas_plan;

create table votos_ventana (
    usuario_id uuid not null,
    ventana_id uuid not null,
    creado_en  timestamp(6) with time zone not null,
    constraint pk_votos_ventana primary key (usuario_id, ventana_id),
    constraint fk_votos_ventana_usuario foreign key (usuario_id) references usuarios,
    constraint fk_votos_ventana_ventana foreign key (ventana_id) references ventanas_plan
);
