# Manual de despliegue de Huecko

Cómo está publicado Huecko y cómo volver a publicarlo desde cero, en planes
gratuitos:
- La web, la API y el servicio de IA en **Render**.
- Postgres en **Supabase**.
- Mongo en **MongoDB Atlas**.

El código ya está listo en `main`. Lo que queda son pasos en paneles web, con
tus cuentas y tus claves.

- **Tiempo:** 40–50 minutos la primera vez.
- **Coste:** 0 €.
- **Versión:** v0.10.0 (backend), v0.9.0 (frontend), v0.7.0 (IA).

## Índice

- [Arquitectura](#arquitectura)
- [Lista de pasos](#lista-de-pasos)
- [1. Postgres en Supabase](#1-postgres-en-supabase)
- [2. Mongo en Atlas](#2-mongo-en-atlas)
- [3. Clave de Gemini](#3-clave-de-gemini)
- [4. Blueprint en Render](#4-blueprint-en-render)
- [5. Comprobar](#5-comprobar)
- [6. Tu cuenta de administrador](#6-tu-cuenta-de-administrador)
- [7. Prueba final](#7-prueba-final)
- [Monitoreo y registro](#monitoreo-y-registro)
- [Apagar y encender (plan gratuito)](#apagar-y-encender-plan-gratuito)
- [Si algo falla](#si-algo-falla)
- [Volver atrás](#volver-atrás)
- [Mantenimiento](#mantenimiento)

---

## Arquitectura

```
Navegador ──HTTPS──► huecko-web (Render, sitio estático: React)
    │
    └──HTTPS + WebSocket──► huecko-api (Render, Docker: Spring Boot)
                               ├──► Postgres (Supabase, pooler de sesión)
                               ├──► MongoDB (Atlas M0)
                               └──HTTPS + token──► huecko-ia (Render, Docker: FastAPI) ──► Gemini
```

| Pieza | Servicio | Repo | URL |
| --- | --- | --- | --- |
| Web | Render, sitio estático | `huecko-frontend` | `https://huecko-web.onrender.com` |
| API | Render, Docker | `huecko-backend` | `https://huecko-api.onrender.com` |
| Servicio de IA | Render, Docker | `huecko-ai-service` | `https://huecko-ia.onrender.com` |
| Postgres | Supabase (BaaS) | — | cuentas, grupos, planes, fotos |
| MongoDB | Atlas M0 | — | horarios, imprevistos, reportes, fallos |
| Modelo | Google AI Studio | — | Gemini, para criticidad, sugerencias y escaneo |

Los tres servicios salen de un solo archivo, `render.yaml` (un *Blueprint* de
Render), en la rama **`main`** de `huecko-backend`. Lo que llega a `develop` no
se publica hasta la siguiente release.

> **Alternativa.** El frontend también funciona en Vercel (`vercel.json` tiene
> las mismas cabeceras), y Postgres en Neon. Si usas Neon, la URL es la directa
> con `?sslmode=require` y sin `channel_binding`.

## Lista de pasos

- [ ] 1. Postgres en Supabase
- [ ] 2. Mongo en Atlas
- [ ] 3. Clave de Gemini
- [ ] 4. Blueprint en Render
- [ ] 5. Comprobar
- [ ] 6. Tu cuenta de administrador
- [ ] 7. Prueba final

---

## 1. Postgres en Supabase

*Tú · 5 min*

1. En https://supabase.com crea una organización (plan Free) y un proyecto.
   - **Región:** East US (North Virginia), la misma que Render.
   - **Database password:** pulsa **Generate a password**, y **cópiala y
     guárdala**. Supabase no la vuelve a enseñar.
   - **Security:** desactiva **Enable Data API**. La API de Huecko se conecta
     directo a Postgres; con la Data API, las tablas quedarían expuestas en
     la API REST pública de Supabase.
2. Cuando el proyecto diga *Healthy*, abre **Connect** → **Direct** →
   **Session pooler**. El host directo (`db.xxx.supabase.co`) solo tiene IPv6
   y Render no lo alcanza; el pooler en modo sesión sí. Y no el de
   transacciones (puerto 6543), que rompe las sentencias preparadas de JDBC.

De ahí salen tres valores para Render:

| Variable | Valor |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` (el host que diga tu panel) |
| `SPRING_DATASOURCE_USERNAME` | `postgres.<ref-del-proyecto>` |
| `SPRING_DATASOURCE_PASSWORD` | la contraseña del paso 1 |

La base se deja **vacía**: las tablas las crea Flyway la primera vez que
arranca la API.

## 2. Mongo en Atlas

*Tú · 8 min*

1. En https://cloud.mongodb.com crea un proyecto y, dentro, un cluster:
   **Free (M0)**, proveedor **AWS**, región **N. Virginia (us-east-1)**.
   Cuidado: la pantalla viene con el plan de pago M10 marcado.
2. **Database Access:** crea un usuario (p. ej. `huecko`) con **Autogenerate
   Secure Password** y cópiala. Mejor solo letras y números: los símbolos hay
   que codificarlos en la URL.
3. **Network Access:** añade `0.0.0.0/0`. Render gratuito no tiene IP fija; la
   protección es el usuario y la contraseña.
4. **Connect** → **Drivers** y copia la cadena.

Haz dos cambios en la cadena:
- Sustituye `<db_password>` por la contraseña, **quitando también los signos
  `<` y `>`**.
- Cambia **`/?` por `/huecko?`**, que es el nombre de la base.

Queda así:

| Variable | Valor |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | `mongodb+srv://huecko:CLAVE@cluster0.xxxxx.mongodb.net/huecko?retryWrites=true&w=majority` |

## 3. Clave de Gemini

*Tú · 2 min*

En https://aistudio.google.com → **Get API key** → **Create API key**. Esa
clave es `GEMINI_API_KEY`.

Sin ella todo funciona igual, pero la criticidad de las ausencias sale por
reglas y no hay sugerencias ni escaneo de horarios con IA.

## 4. Blueprint en Render

*Tú · 10 min, más el primer build*

1. En https://dashboard.render.com → **New** → **Blueprint** → pega la URL
   pública `https://github.com/Nakusuo/huecko-backend` (o conecta GitHub).
   Rama `main`.
2. Render lee `render.yaml` y propone **huecko-api**, **huecko-ia** y
   **huecko-web**. Pide los valores marcados `sync: false`:

   | Variable | Servicio | Qué poner |
   | --- | --- | --- |
   | `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | huecko-api | Lo del paso 1. |
   | `SPRING_DATA_MONGODB_URI` | huecko-api | Lo del paso 2. |
   | `HUECKO_CORS_ORIGINS` | huecko-api | `https://huecko-web.onrender.com` |
   | `HUECKO_IA_URL` | huecko-api | `https://huecko-ia.onrender.com` |
   | `HUECKO_ADMIN_EMAILS` | huecko-api | **Vacío.** Se rellena en el paso 6. |
   | `GEMINI_API_KEY` | huecko-ia | Lo del paso 3. |
   | `VITE_API_URL` | huecko-web | `https://huecko-api.onrender.com/api` |

   No hay que inventar secretos: Render genera `HUECKO_JWT_SECRET` y
   `HUECKO_IA_TOKEN`, y la IA recibe el mismo token que la API.
3. **Deploy Blueprint.** El primer build de la API tarda unos 5 minutos
   (Maven descarga todas las dependencias).

> **Cuidado al pegar.** Cada valor va en su casilla: si la cadena de Mongo
> acaba en `SPRING_DATASOURCE_URL`, la API no arranca (`URL must start with
> 'jdbc'`).

Las variables con valor fijo en `render.yaml` (memoria de la JVM, modelo de
Gemini, cabeceras de la web…) se cambian editando el archivo y haciendo push a
`main`; un cambio a mano en **Environment** lo pisa el siguiente sync. Las de
`sync: false` sí se cambian en **Environment** → **Save, rebuild and deploy**.

> **Si los repos se conectaron por URL pública** y no con la app de GitHub,
> Render no despliega solo al hacer push. Tras cada release: Blueprint →
> **Manual Sync**, y en cada servicio **Manual Deploy → Deploy latest commit**.

## 5. Comprobar

*Tú · 2 min*

| Dirección | Debe responder |
| --- | --- |
| `https://huecko-api.onrender.com/api/actuator/health` | `{"status":"UP"}` |
| `https://huecko-ia.onrender.com/salud` | `"estado":"ok"` y `"gemini":true` |
| `https://huecko-web.onrender.com` | La pantalla de acceso, sin el aviso de «modo demo» |
| `https://huecko-api.onrender.com/api/docs` | Swagger con todos los endpoints |

Si Render le puso otro nombre a algún servicio, corrige `HUECKO_IA_URL`,
`HUECKO_CORS_ORIGINS` o `VITE_API_URL` y vuelve a desplegar. Recuerda que
`VITE_API_URL` se lee al construir la web.

## 6. Tu cuenta de administrador

*Tú · 3 min*

> **El orden importa.** Quien tenga una cuenta con un correo de
> `HUECKO_ADMIN_EMAILS` pasa a admin cada vez que arranca la API, y el correo
> no se verifica. Si pones un correo sin cuenta, otra persona podría
> registrarlo antes que tú.

1. Regístrate en la web con tu correo.
2. Render → `huecko-api` → **Environment** → `HUECKO_ADMIN_EMAILS` = ese
   correo (**Add Environment Variable** si no aparece) → **Save, rebuild and
   deploy**.
3. Cierra sesión y vuelve a entrar: el rol viaja dentro del token.
4. Recomendado: panel → **Cuenta** → activa la **verificación en dos pasos**
   y guarda los códigos de respaldo.

## 7. Prueba final

*Tú · 10 min*

1. Abre la web. Si la API dormía, el login avisa de que el servidor se está
   despertando (uno o dos minutos) y espera.
2. Entra con dos cuentas (la segunda en una ventana privada).
3. Crea un grupo, une a la segunda cuenta con el código o el QR, y marcad
   horarios (a mano o con «Escanear horario»).
4. Propón un plan, votad y ciérralo. Todo debe actualizarse en vivo en las dos
   ventanas.
5. Con la segunda cuenta, reporta un imprevisto. La votación exprés debe
   llegar con la sugerencia de la IA.
6. Como admin: **Salud** (todo en verde), **Métricas** (las peticiones de la
   prueba) y **Consola** (las líneas con su id de petición).

---

## Monitoreo y registro

Qué mirar y dónde, de lo más inmediato a lo más detallado:

| Qué | Dónde | Para qué |
| --- | --- | --- |
| **Salud** | Panel admin → Salud (`GET /api/admin/salud`) | Postgres, Mongo, tiempo real e IA responden; tareas automáticas al día; memoria y versión. |
| **Métricas** | Panel admin → Métricas (`GET /api/admin/metricas`) | Peticiones por minuto, % de errores 5xx y 4xx, tiempo medio y p95 por endpoint, en vivo (Micrometer / Actuator). |
| **Fallos** | Panel admin → Fallos | Excepciones del servidor, de las tareas y errores de JavaScript del navegador, agrupados y con su número de veces. |
| **Consola** | Panel admin → Consola | Las últimas 1000 líneas del log, en vivo y con filtro. |
| **Logs completos** | Render → servicio → **Logs** | Todo lo que escribe el proceso, incluido el arranque. Busca con el cuadro de búsqueda (`Caused by`, un id de petición…). |
| **Disponibilidad** | Monitor externo (abajo) | Te avisa por correo si la web o la API dejan de responder. |

### Id de petición

Cada petición lleva un identificador corto (`X-Request-Id`). Aparece:

- en todas sus líneas del log: `WARN [k3x9m2p7q4hz] … POST /api/planes → 500 (2310 ms)`;
- en el JSON de cualquier error (`"idPeticion"`);
- en el mensaje que ve la persona cuando el error es del servidor: «… (código: k3x9m2p7q4hz)»;
- en la bandeja de **Fallos**.

Si alguien reporta un error con su código, búscalo en **Consola** o en los
logs de Render y salen solo las líneas de esa petición. Las peticiones normales
se registran en DEBUG; las que fallan (5xx) o tardan más de 2 s, en WARN, así
que llegan a la consola del panel.

### Monitor externo de caída (UptimeRobot)

*Tú · 5 min · gratis*

1. Crea una cuenta en https://uptimerobot.com.
2. **Add New Monitor** → tipo **HTTP(s)**:
   - **URL:** `https://huecko-api.onrender.com/api/actuator/health/liveness`
   - **Interval:** 5 minutos.
   - **Alert contacts:** tu correo.
3. Repite con `https://huecko-web.onrender.com` (la web) y, si quieres,
   `https://huecko-ia.onrender.com/salud`.
4. Opcional: **Status Pages** → crea una página pública con los monitores, para
   enseñarla en la presentación.

> **Ojo con las horas gratis.** Un monitor que consulta la API cada 5 minutos
> **impide que se duerma**, así que gasta horas de Render todo el día (unas
> 48 h diarias entre API e IA). Tenlo **pausado** (botón *Pause*) y actívalo
> solo los días de entrega o exposición. El monitor de la web estática sí
> puede quedarse siempre: no gasta horas.

## Apagar y encender (plan gratuito)

Render da **750 horas al mes** para todos los servicios juntos. La API y la IA
despiertas gastan unas 48 al día: se agotan en unos 15 días.

- **Apagar** cuando no se use: Render → `huecko-api` y `huecko-ia` → **Settings**
  → al final → **Suspend Web Service**. La web estática no gasta horas.
- **Encender** (30 minutos antes de usarla):
  1. Supabase: si el proyecto dice *Paused* (pasa tras 7 días sin uso), **Restore**.
  2. Atlas: si Cluster0 dice *Paused*, **Resume**.
  3. Render: **Resume Web Service** en `huecko-ia` y luego en `huecko-api`.
  4. Comprueba las URL del paso 5.
- Horas restantes: Render → workspace → **Billing**.

## Si algo falla

| Lo que ves | Causa y arreglo |
| --- | --- |
| Render: **Ran out of memory (used over 512MB)** | Baja `MaxRAMPercentage` de 55 a 45 en `JAVA_TOOL_OPTIONS`, **en `render.yaml`**, y haz push. |
| `URL must start with 'jdbc'` | En `SPRING_DATASOURCE_URL` hay otra cosa (p. ej. la cadena de Mongo). Pon la URL `jdbc:postgresql://…` del paso 1. |
| `MongoSecurityException … bad auth` | Usuario o clave mal en `SPRING_DATA_MONGODB_URI`. Lo más común: la clave quedó entre `<` `>`. |
| `password authentication failed` (Postgres) | Clave de Supabase mal copiada, o el usuario no es `postgres.<ref>`. |
| `Tenant or user not found` | Host del pooler equivocado (`aws-0` / `aws-1`) o el proyecto de Supabase está pausado. |
| Mongo: `Timed out after 30000 ms` | Falta `0.0.0.0/0` en **Network Access** de Atlas, o el cluster está pausado. |
| La API muere con `Schema-validation: missing column…` | Una entidad cambió sin su migración en `src/main/resources/db/migration`. |
| El navegador da **CORS error** | `HUECKO_CORS_ORIGINS` no tiene el dominio exacto de la web (con `https://`, sin barra final). |
| La web sale en **modo demo** | Falta `VITE_API_URL` en `huecko-web`, o no se volvió a construir después de ponerla. |
| Consola: **Refused to … Content Security Policy** | Algo carga desde un dominio que las cabeceras de `render.yaml` no permiten. |
| «Demasiados intentos» al entrar | Freno contra fuerza bruta: 5 fallos por correo o 20 intentos por IP cada 15 minutos. |
| El código de dos pasos «no es correcto» | La hora del móvil no está en automático, o se usó el mismo código dos veces. Espera al siguiente o usa uno de respaldo. |
| En **Salud**, la IA «no responde» | `HUECKO_IA_URL` no apunta al servicio real, o `huecko-ia` está suspendido. |

## Volver atrás

- **API, IA o web:** si un despliegue no pasa el healthcheck, Render mantiene la
  versión anterior. A mano: el servicio → **Events** → **Rollback**.
- **Base de datos:** las migraciones solo avanzan. Para deshacer una, se escribe
  otra migración que revierta el cambio.

## Mantenimiento

### Publicar una versión nueva

Trabaja en `develop` (Git Flow), deja que pase la CI y fusiona en `main` con una
release (`release/vX.Y.Z` y etiqueta). Después, si Render no despliega solo,
**Manual Sync** del Blueprint y **Deploy latest commit**.

### Cambiar una entidad de la base

Añade una migración nueva (`V11__…sql`) en `src/main/resources/db/migration`.
Nunca edites una ya aplicada: Flyway compara su checksum y se niega a arrancar.

### Copias de seguridad

De vez en cuando, a un sitio **privado**:

```bash
pg_dump "postgresql://postgres.<ref>:CLAVE@aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require" > huecko-postgres.sql
mongodump --uri "mongodb+srv://huecko:CLAVE@cluster0.xxxxx.mongodb.net/huecko" --out huecko-mongo
```
