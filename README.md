# Huecko Backend — Plataforma Inteligente de Coordinación de Horarios y Planes de Grupo
[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Relational%20DB-blue.svg)](https://www.postgresql.org/)
[![MongoDB](https://img.shields.io/badge/MongoDB-NoSQL%20Events-brightgreen.svg)](https://www.mongodb.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Hybrid%20Persistence-purple.svg)]()
**Huecko Backend** es una API REST empresarial diseñada para resolver el problema recurrente de coordinar agendas y organizar eventos en grupos. La plataforma combina ingestión de horarios híbrida (manual + OCR), cruce inteligente de disponibilidad mediante heatmaps y algoritmos de coincidencia por umbrales, votación consensuada de planes, seguimiento de puntualidad en tiempo real y resolución asistida de imprevistos según criticidad de roles.
---
## 📌 Tabla de Contenidos
1. [Visión General del Proyecto](#-visión-general-del-proyecto)
2. [Arquitectura e Híbrido de Persistencia](#-arquitectura-e-híbrido-de-persistencia)
3. [Módulos e Historias de Usuario (HU)](#-módulos-e-historias-de-usuario-hu)
4. [Requerimientos Funcionales (RF)](#-requerimientos-funcionales-rf)
5. [Estructura del Proyecto](#-estructura-del-proyecto)
6. [Instalación y Configuración](#-instalación-y-configuración)
---
## 🎯 Visión General del Proyecto
Organizar actividades grupales (reuniones de estudio, salidas, proyectos) suele verse frustrado por incompatibilidad de horarios, respuestas tardías y cancelaciones de último momento. 
**Huecko** automatiza y simplifica este proceso:
* **Ingreso Híbrido:** Permite registrar disponibilidad semanal, eventos puntuales e importar horarios universitarios/laborales mediante OCR.
* **Algoritmo de Cruce Flexible:** Calcula intersecciones de tiempo libre ajustando umbrales de quórum (ej. 70%–100% de disponibilidad).

---
## 🗂️ Estructura del Proyecto

Proyecto Maven estándar. Los paquetes se agrupan por módulo funcional, salvo la
capa de persistencia, que se separa por base de datos para que quede explícito
qué vive en Postgres y qué en Mongo.

```
huecko-backend/
├── pom.xml
├── docker-compose.yml            # Postgres + Mongo para desarrollo
├── .env.example                  # Credenciales y secreto JWT (copiar a .env)
├── docker/mongo/init/            # Creación de la colección (solo 1ª vez)
└── src/main/
    ├── resources/
    │   ├── application.yml       # Perfil por defecto (dev): con datos de demo
    │   └── application-prod.yml  # Sin seed, Hibernate en modo validate
    └── java/com/huecko/backend/
        ├── HueckoBackendApplication.java
        ├── auth/                 # Login, registro y JWT
        ├── usuario/              # Perfil del usuario autenticado (/api/me)
        ├── horario/              # Módulo 1: bloques de horario (RF-01…RF-04)
        ├── seed/                 # Carga de datos de demo
        ├── common/exception/     # Excepciones y formato único de error
        ├── config/               # Seguridad, CORS y separación Mongo/JPA
        ├── mongo/                # Documentos y repositorios de Mongo
        └── postgres/             # Entidades y repositorios de JPA
```

### Por qué dos paquetes de configuración

`MongoConfig` y `PostgresConfig` acotan cada tecnología a su propio paquete de
repositorios. Sin eso, Spring intenta tratar **todos** los `Repository` como si
fueran JPA y el arranque falla.

---
## ⚙️ Instalación y Configuración

> 📖 **¿Partes de cero?** [`docs/PUESTA_EN_MARCHA.md`](docs/PUESTA_EN_MARCHA.md)
> es el manual paso a paso: cómo instalar JDK, Maven y Docker en una máquina
> donde no hay nada, cómo probar el servicio junto al frontend y qué hacer
> cuando algo falla. Lo de abajo es el resumen para quien ya tiene el entorno.

### Requisitos

| Herramienta | Versión |
| --- | --- |
| JDK | 17 o superior |
| Maven | 3.9+ |
| Docker + Docker Compose | cualquiera reciente |

### 1. Variables de entorno

```bash
cp .env.example .env
```

Los valores por defecto sirven para desarrollo tal cual. Lo único obligatorio de
cambiar antes de desplegar es `HUECKO_JWT_SECRET`.

### 2. Levantar las bases

```bash
docker compose up -d
```

Arranca Postgres en el puerto 5432 y Mongo en el 27017. Los datos persisten en
volúmenes de Docker, así que sobreviven a un `docker compose down`. Para empezar
de cero: `docker compose down -v`.

### 3. Arrancar la API

```bash
mvn spring-boot:run
```

Queda escuchando en `http://localhost:8080`. Comprobación rápida:

```bash
curl http://localhost:8080/api/actuator/health
```

### 4. Datos de demo

En el perfil `dev` (el de por defecto), al arrancar se cargan dos usuarios, un
grupo y sus horarios. Las credenciales son **las mismas que usa el modo demo del
frontend**, así que la app se comporta igual conectada que desconectada:

| Correo | Contraseña |
| --- | --- |
| `alex.rodriguez@huecko.com` | `demo1234` |
| `diana.torres@huecko.com` | `demo1234` |

La carga es idempotente: si los usuarios ya existen no se duplica nada, y los
bloques de horario solo se siembran si el usuario no tiene ninguno. Se apaga con
`huecko.seed.enabled=false` (el perfil `prod` ya lo hace).

```bash
# Prueba de humo de punta a punta
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex.rodriguez@huecko.com","password":"demo1234"}'
```

### 5. Conectar el frontend

En `huecko-frontend`, dejar `.env.local` con:

```
VITE_API_URL=/api
VITE_BACKEND_PROXY=http://localhost:8080
```

Con el proxy de Vite el navegador habla con el mismo origen y **no hace falta
CORS**. Si se apunta directo a `http://localhost:8080`, hay que añadir el origen
del frontend a `HUECKO_CORS_ORIGINS`.

### Endpoints disponibles hoy

| Método | Ruta | Autenticación |
| --- | --- | --- |
| `POST` | `/api/auth/register` | pública |
| `POST` | `/api/auth/login` | pública |
| `GET` `PATCH` | `/api/me` | JWT |
| `GET` `POST` | `/api/usuarios/{usuarioId}/bloques-horario` | JWT |
| `GET` | `/api/usuarios/{usuarioId}/bloques-horario/borradores` | JWT |
| `PUT` `DELETE` | `/api/usuarios/{usuarioId}/bloques-horario/{bloqueId}` | JWT |
| `GET` | `/api/actuator/health` | pública |

El `{usuarioId}` de la ruta debe coincidir con el del token; si no, responde
`403`. Es un resto de cuando el módulo se probaba sin autenticación y está
previsto que desaparezca de la URL.

### Formato de error

Toda respuesta de error, incluidas las de Spring Security, sale igual:

```json
{ "timestamp": "2026-09-04T18:20:11Z", "error": "Solicitud inválida", "mensaje": "horaFin debe ser posterior a horaInicio" }
```
