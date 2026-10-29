# Huecko frente al sílabo de Desarrollo Full Stack

Curso **Desarrollo Full Stack (100000S71F)**, Ingeniería de Software, ciclo
2026-2 (UTP).

**Logro general:** «desarrolla soluciones de software full-stack empleando la
arquitectura cliente-servidor y considerando los requisitos y necesidades de
las organizaciones».

Este documento dice, tema por tema, **dónde se cumple** en el proyecto y
**cómo enseñarlo**. Huecko son tres repositorios:

| Repo | Qué es | Tecnología |
| --- | --- | --- |
| [huecko-frontend](https://github.com/Nakusuo/huecko-frontend) | Cliente web | React 19 + TypeScript + Vite |
| [huecko-backend](https://github.com/Nakusuo/huecko-backend) | API REST + tiempo real | Java 17 + Spring Boot 3.4 |
| [huecko-ai-service](https://github.com/Nakusuo/huecko-ai-service) | Servicio de IA | Python + FastAPI + Gemini |

En producción: https://huecko-web.onrender.com (web) ·
https://huecko-api.onrender.com/api/docs (Swagger de la API).

---

## Unidad 1 · Introducción al desarrollo Full Stack

| Tema | Dónde se cumple |
| --- | --- |
| Herramientas de gestión de proyectos | **Git Flow** en los tres repos: ramas `feat/*`, `fix/*` y `release/*` desde `develop`, merges `--no-ff` y versiones etiquetadas (`v0.1.0` → `v0.10.0`). 40 *pull requests* y más de 350 commits semánticos en español (`feat(auth): …`, `fix(admin): …`). |
| Entorno de desarrollo y pruebas | Backend: Maven Wrapper, perfiles `dev`/`prod`, `.env.example`. Frontend: Vite, `.env.development` / `.env.production` / `.env.demo`, `oxlint`, Vitest. **Pruebas:** 442 en el backend (JUnit 5 + Mockito), 310 en el frontend (Vitest) y 56 en la IA (pytest). |
| Control de versiones | Git + GitHub. **CI con GitHub Actions** en cada repo (`.github/workflows/ci.yml`): compila, prueba y construye la imagen Docker en cada push y PR. |
| Contenedores y máquinas virtuales | `Dockerfile` multi-etapa en el backend y en la IA. `docker-compose.yml` levanta Postgres 16 y MongoDB 7 en local. |
| Plataformas de despliegue en la nube | **Render** (PaaS, con *Infrastructure as Code* en `render.yaml`), **Supabase** (Postgres gestionado) y **MongoDB Atlas**. Ver [DESPLIEGUE.md](DESPLIEGUE.md). |
| Proyectos serverless | El frontend es un sitio estático servido desde CDN, sin servidor propio, y Supabase es un backend como servicio. La alternativa en Vercel (`vercel.json`) es igual de serverless. |
| Tecnologías web, móvil, bases de datos, contenedores | Web (React) responsive para móvil; bases relacional y NoSQL; contenedores. Existe además una app Android hermana con la que se alinearon funciones (IA, códigos de invitación, votaciones). |

## Unidad 2 · Desarrollo Front-end

| Tema | Dónde se cumple (`huecko-frontend`) |
| --- | --- |
| Framework de front-end | React 19 con TypeScript estricto, empaquetado con Vite 8. |
| Navegación | React Router 7 (`src/routes/AppRouter.tsx`): rutas públicas, rutas protegidas (`ProtectedRoute`) y zona de administración con su propio guard (`RutaAdmin`). |
| Gestión de estado | Zustand (`src/store/`): sesión persistida, grupos, horario, perfil, notificaciones. |
| Bibliotecas de estilos / framework CSS | Tailwind CSS 4 con tokens de diseño propios, animaciones y diseño responsive. |
| Reactividad | Componentes que reaccionan al tiempo real: votos, planes, fotos y avisos llegan por WebSocket y se pintan sin recargar (`hooks/useTiempoReal.ts`). |
| Consumo de APIs y formularios | Axios con interceptores (`lib/apiClient.ts`): token JWT, errores uniformes, sesión expirada (401). Formularios con **react-hook-form + Zod** (validación con mensajes en español). |
| Peticiones HTTP asíncronas | Estados de carga, error y reintento en todas las pantallas (`hooks/useCarga.ts`); subida de fotos *multipart*; el login espera si el servidor gratuito está despertando. |
| Autenticación en el front-end | Login, registro, sesión persistida, cierre por expiración, **login en dos pasos** (`pages/auth/PasoDosPasos.tsx`) y rutas protegidas por rol. |
| Despliegue del front-end | Build de producción (`npm run build`) publicado en Render como sitio estático, con cabeceras de seguridad: CSP, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`. |

## Unidad 3 · Desarrollo Back-end

| Tema | Dónde se cumple (`huecko-backend`) |
| --- | --- |
| Bases de datos relacionales y NoSQL | **PostgreSQL** (cuentas, grupos, planes, votos, fotos) con JPA/Hibernate y **migraciones Flyway** (`db/migration/V1…V10`). **MongoDB** (bloques de horario, imprevistos, reportes, fallos) con Spring Data MongoDB e índices TTL. |
| Back-end como servicio (BaaS) | Postgres alojado en **Supabase**, con su Data API desactivada a propósito: el acceso pasa siempre por la API propia. |
| Framework de back-end | Spring Boot 3.4: Web MVC, Security, Data JPA, Data MongoDB, Validation, WebSocket, Actuator. Documentado con **Swagger / OpenAPI** en `/api/docs`. |
| CRUD sobre una base de datos | Grupos, miembros, horarios, planes, ventanas, votos, fotos, comentarios, reportes… (`*/controller`), con validación (`jakarta.validation`) y errores en un formato único (`GlobalExceptionHandler`). |
| Aplicaciones en tiempo real / WebSockets | STOMP sobre WebSocket con SockJS (`config/WebSocketConfig.java`). Votos, planes, álbum, «Estoy libre», «Llegué» y recordatorios llegan en vivo a cada grupo; el canal se autentica con el JWT (`SeguridadStompInterceptor`). |
| Autenticación y autorización | Spring Security *stateless*. Contraseñas con BCrypt. Roles `USUARIO` / `ADMIN`: todo `/api/admin/**` exige ADMIN. Permisos por recurso: solo miembros ven su grupo, solo quien organiza cierra un plan. Freno a la fuerza bruta en login y registro (`ProteccionAcceso`). Cuentas suspendibles. |
| JSON Web Token (JWS) | Tokens HS256 firmados con `jjwt` (`auth/service/JwtService.java`). El rol viaja en el token; un token con la clave de desarrollo no arranca en producción. |
| **Seguridad en dos pasos** | **Verificación en dos pasos TOTP** (RFC 6238, la de Google Authenticator), implementada sin librerías en `auth/service/Totp.java` y validada con los vectores oficiales del RFC. El login devuelve un desafío de 5 minutos que no sirve como sesión, y `/api/auth/dos-pasos` lo cambia por el token con el código. Secreto cifrado con AES-256-GCM, 10 códigos de respaldo de un solo uso (guardados como SHA-256), sin repetir un código ya usado, y con límite de intentos. |

## Unidad 4 · Integración cliente-servidor

| Tema | Dónde se cumple |
| --- | --- |
| Integración y despliegue | Los tres servicios se integran por HTTPS: la web con la API (REST + WebSocket) y la API con la IA (REST con token compartido). CORS restringido al dominio exacto de la web. Despliegue con un *Blueprint* (`render.yaml`) que crea los tres servicios y sus variables, y genera los secretos. |
| Plataformas de despliegue | Render (Docker + sitio estático), Supabase, MongoDB Atlas y Google AI Studio. Manual paso a paso, con tabla de errores y vuelta atrás: [DESPLIEGUE.md](DESPLIEGUE.md). |
| **Monitoreo de aplicaciones** | Panel de administración: **Salud** (bases, tiempo real, IA, tareas programadas, memoria) y **Métricas** (peticiones por minuto, % de errores 4xx/5xx, tiempo medio y p95 por endpoint en vivo, con Spring Boot Actuator / Micrometer). *Health checks* de Render sobre `/api/actuator/health/liveness`. Monitor externo de disponibilidad con UptimeRobot. |
| **Registro (logging)** | **Id de petición** (`X-Request-Id`) en cada línea del log, en cada respuesta de error y en el mensaje que ve la persona. **Consola** del panel con las últimas 1000 líneas en vivo. Bandeja de **Fallos**: excepciones del servidor, de las tareas y errores de JavaScript del navegador, agrupados y reabiertos si reaparecen. Reportes de las personas desde la app. |

---

## Más allá del sílabo

Cosas que el curso no pide y que dan valor a la solución:

- **Inteligencia artificial** (servicio propio en Python): evaluación de la
  gravedad de un imprevisto, sugerencias para votaciones exprés, borrador e
  ideas de plan, resumen de votación y **lectura de horarios desde una foto**.
  Si la IA falla, todo sigue funcionando con reglas.
- **Privacidad por diseño (RNF-02):** nadie ve el horario de otra persona; el
  grupo solo ve recuentos de quién está libre. Las fotos se reprocesan en el
  servidor para quitarles la ubicación GPS.
- **Modo demo** sin servidor, para presentar aunque falle internet.
- **Accesibilidad:** navegación por teclado, `aria-*`, contraste y textos
  alternativos.

## Guion para enseñarlo (10 minutos)

1. **Arquitectura** (1 min): el diagrama de [DESPLIEGUE.md](DESPLIEGUE.md#arquitectura).
2. **Front-end** (2 min): registro con validación, navegación, crear grupo,
   unirse por QR, horario y escaneo con IA.
3. **Tiempo real** (2 min): dos ventanas; se vota en una y se ve en la otra al
   instante.
4. **Seguridad** (2 min): activar la verificación en dos pasos con el móvil,
   salir y volver a entrar con el código. Enseñar un 403 al pedir `/api/admin`
   con una cuenta normal (en Swagger).
5. **Back-end** (1 min): Swagger en `/api/docs`, Postgres + Mongo, migraciones.
6. **Monitoreo** (2 min): Métricas en vivo mientras se usa la app, Consola con
   el id de petición, Fallos y el estado de UptimeRobot.
