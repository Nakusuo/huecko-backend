# Manual de despliegue de Huecko

Paso a paso para publicar los tres repos en planes gratuitos: el frontend en
Vercel, la API y el servicio de IA en Render, Postgres en Neon y Mongo en
MongoDB Atlas. El código ya está listo en `main`; lo que queda son pasos en
paneles web con tus cuentas y tus claves.

- **Tiempo:** 30–40 minutos.
- **Coste:** 0 €.
- **Versión:** v0.6.1 (frontend y backend), v0.6.0 (IA), con la CI en verde.

## Índice

- [Antes de empezar](#antes-de-empezar)
- [Lista de pasos](#lista-de-pasos)
- [1. Postgres en Neon](#1-postgres-en-neon)
- [2. Mongo en Atlas](#2-mongo-en-atlas)
- [3. Clave de Gemini](#3-clave-de-gemini)
- [4. API e IA en Render](#4-api-e-ia-en-render)
- [5. Comprobar Render](#5-comprobar-render)
- [6. Frontend en Vercel](#6-frontend-en-vercel)
- [7. Abrir CORS al frontend](#7-abrir-cors-al-frontend)
- [8. Tu cuenta de administrador](#8-tu-cuenta-de-administrador)
- [9. Prueba final](#9-prueba-final)
- [Qué implica el plan gratuito](#qué-implica-el-plan-gratuito)
- [Si algo falla](#si-algo-falla)
- [Volver atrás](#volver-atrás)
- [Mantenimiento](#mantenimiento)

---

## Antes de empezar

| Pieza | Servicio | Repo | URL prevista |
| --- | --- | --- | --- |
| Frontend | Vercel | `huecko-frontend` | `https://huecko.vercel.app` |
| API | Render (Docker) | `huecko-backend` | `https://huecko-api.onrender.com` |
| Servicio de IA | Render (Docker) | `huecko-ai-service` | `https://huecko-ia.onrender.com` |
| Postgres | Neon | — | cuentas, grupos, planes |
| Mongo | MongoDB Atlas M0 | — | horarios, imprevistos, reportes |
| Modelo | Google AI Studio | — | Gemini, para la criticidad y las sugerencias |

Necesitas:

- Acceso a GitHub con los tres repos. Son públicos, así que Render y Vercel
  los leen sin permisos extra.
- Cuentas en Neon, MongoDB Atlas, Render y Vercel. Con «entrar con GitHub»
  basta en casi todas.
- Un sitio donde apuntar las claves que vayas generando: un gestor de
  contraseñas, nunca un archivo del repo.

Todo se despliega desde la rama **`main`** de cada repo. Lo que llega a
`develop` no sale hasta la siguiente release.

## Lista de pasos

Marca cada paso al terminarlo (en GitHub, edita el archivo o cópialo a una
nota):

- [ ] 1. Postgres en Neon
- [ ] 2. Mongo en Atlas
- [ ] 3. Clave de Gemini
- [ ] 4. API e IA en Render
- [ ] 5. Comprobar Render
- [ ] 6. Frontend en Vercel
- [ ] 7. Abrir CORS al frontend
- [ ] 8. Tu cuenta de administrador
- [ ] 9. Prueba final

---

## 1. Postgres en Neon

*Tú · 5 min*

1. Entra en https://neon.tech y crea un proyecto nuevo.
2. Región: **AWS US East (N. Virginia)**, la misma que Render (`virginia` en
   `render.yaml`).
3. Deja la base **vacía**: las tablas las crea la API (Flyway) la primera vez
   que arranca.
4. En **Connection details**, desactiva **Connection pooling**. Necesitas el
   host directo, el que **no** lleva `-pooler`.
5. Copia la cadena. Tendrá esta forma:

   ```
   postgresql://neondb_owner:CLAVE@ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```

De ahí salen tres valores para Render:

| Variable | Valor |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | la `CLAVE` de la cadena |

> **Quita `&channel_binding=require`** y añade `jdbc:` delante. El driver de
> Java no reconoce `channel_binding` y falla al conectar.

## 2. Mongo en Atlas

*Tú · 8 min*

1. En https://cloud.mongodb.com: **Create** → **M0 (Free)**, proveedor AWS,
   región **N. Virginia (us-east-1)**.
2. **Database Access** → crea un usuario con contraseña. Mejor solo letras y
   números: los símbolos hay que codificarlos en la URL.
3. **Network Access** → **Add IP Address** → `0.0.0.0/0`. Render gratuito no
   tiene IP fija; la protección es el usuario y la contraseña.
4. **Connect** → **Drivers** y copia la cadena:

   ```
   mongodb+srv://USUARIO:<db_password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0
   ```

Haz dos cambios: sustituye `<db_password>` por la contraseña, y **`/?` por
`/huecko?`** (el nombre de la base). Queda así:

| Variable | Valor |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` | `mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/huecko?retryWrites=true&w=majority&appName=Cluster0` |

Los índices los crea la API al arrancar; no hay que hacer nada más en Atlas.

## 3. Clave de Gemini

*Tú · 2 min*

En https://aistudio.google.com → **Get API key** → **Create API key**. Esa
clave es `GEMINI_API_KEY`.

Sin ella todo funciona igual, pero la criticidad de las ausencias sale por
reglas y no hay sugerencias en las votaciones exprés.

## 4. API e IA en Render

*Tú · 10 min, más el primer build*

1. Entra en https://render.com con GitHub y da acceso a **los dos** repos:
   `huecko-backend` y `huecko-ai-service`.
2. **New** → **Blueprint** → elige `huecko-backend`. Render lee `render.yaml`
   y propone dos servicios: `huecko-api` y `huecko-ia`.
3. Te pide los valores que no están en el archivo (`sync: false`):

   | Variable | Servicio | Qué poner |
   | --- | --- | --- |
   | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | huecko-api | Lo del paso 1. |
   | `SPRING_DATA_MONGODB_URI` | huecko-api | Lo del paso 2. |
   | `HUECKO_IA_URL` | huecko-api | `https://huecko-ia.onrender.com`. Si Render le pone otro nombre al servicio, se corrige en el paso 5. |
   | `HUECKO_CORS_ORIGINS` | huecko-api | De momento `https://huecko.vercel.app`; se ajusta en el paso 7. |
   | `HUECKO_ADMIN_EMAILS` | huecko-api | **Vacío.** Se rellena en el paso 8. |
   | `GEMINI_API_KEY` | huecko-ia | Lo del paso 3. |

   No tienes que inventar ningún secreto: Render genera `HUECKO_JWT_SECRET` y
   `HUECKO_IA_TOKEN`, y la IA recibe el mismo token que la API.
4. Pulsa **Apply**. El primer build de la API tarda varios minutos: Maven
   descarga todas las dependencias.

> **Las variables con valor fijo en `render.yaml`** (memoria de la JVM,
> modelo de Gemini…) se cambian editando el archivo y haciendo push a `main`.
> Si las tocas a mano en **Environment**, el siguiente sync del Blueprint las
> vuelve a dejar como estaban. Las de `sync: false` sí se cambian en
> **Environment**, con **Save and deploy**.

## 5. Comprobar Render

*Tú · 2 min*

Abre estas dos direcciones (cambia el nombre si Render asignó otro):

```
https://huecko-api.onrender.com/api/actuator/health
https://huecko-ia.onrender.com/salud
```

- La API debe responder `{"status":"UP"}`.
- La IA debe responder con `"gemini": true`.

Si la IA quedó con otra URL, pon la buena en `HUECKO_IA_URL` de `huecko-api`
y elige **Save and deploy**.

## 6. Frontend en Vercel

*Tú · 5 min*

1. En https://vercel.com abre el proyecto `huecko-frontend`. Si no existe:
   **Add New** → **Project** → impórtalo, framework **Vite**, rama de
   producción `main`.
2. **Settings** → **Environment Variables**, solo en el entorno
   **Production**:

   | Variable | Valor |
   | --- | --- |
   | `VITE_API_URL` | `https://huecko-api.onrender.com/api` |

3. **Redespliega**: la variable se lee al construir, no al abrir la página.

Sin `VITE_API_URL` la web funciona en modo demostración, con datos de
ejemplo. Las previews de cada PR se quedan así a propósito, para no tener que
abrir CORS a cualquier dominio de Vercel.

El frontend sale con una **Content-Security-Policy** (`vercel.json`) que solo
deja hablar con la API en `https://*.onrender.com` y `wss://*.onrender.com`.
Si algún día la API va en otro dominio, añádelo a `connect-src` ahí.

## 7. Abrir CORS al frontend

*Tú · 2 min*

En Render → `huecko-api` → **Environment**, pon en `HUECKO_CORS_ORIGINS` el
dominio exacto que te dio Vercel, con `https://` y sin barra final. Después,
**Save and deploy**.

```
https://huecko.vercel.app
```

> **Sin comodines** como `https://huecko-*.vercel.app`: cualquiera puede crear
> en Vercel un proyecto cuyo nombre encaje con ese patrón.

## 8. Tu cuenta de administrador

*Tú · 3 min*

> **El orden importa.** Quien tenga una cuenta con un correo de
> `HUECKO_ADMIN_EMAILS` pasa a admin cada vez que arranca la API, y el correo
> no se verifica. Si pones un correo sin cuenta, otra persona podría
> registrarlo antes que tú.

1. Regístrate en la web con tu correo.
2. Render → `huecko-api` → **Environment** → `HUECKO_ADMIN_EMAILS` = ese
   correo. Si la variable no aparece, créala con **Add Environment
   Variable**. Después, **Save and deploy**.
3. Cierra sesión y vuelve a entrar: el rol viaja dentro del token.

Una vez hecho, nadie puede cambiarse el correo a uno de esa lista, cambiar el
correo pide la contraseña actual, y una cuenta admin no puede cambiar el suyo
desde la app.

## 9. Prueba final

*Tú · 10 min*

1. Abre la web. Si la API dormía, el login avisa de que el servidor se está
   despertando (uno o dos minutos) y espera.
2. Regístrate con dos cuentas (la segunda en una ventana privada).
3. Crea un grupo, añade a la segunda cuenta por correo y marcad horarios.
4. Propón un plan, votad y ciérralo.
5. Con la segunda cuenta, reporta un imprevisto con un motivo como «tengo las
   entradas de todos». La votación exprés debe llegar con la sugerencia de la
   IA.
6. **OCR:** en «Mi horario» → «Importar OCR», sube una foto de un horario. Es
   lo único que no se pudo probar de principio a fin con la CSP activa. Si
   falla, mira la consola del navegador (F12).
7. Como admin, en **Salud**, la fila «Servicio de IA» debe decir que responde
   y tiene Gemini.
8. Cierra la pestaña del panel de admin: consulta la API cada 15 segundos y
   la mantiene despierta.

---

## Qué implica el plan gratuito

- **La API se duerme** tras unos 15 minutos sin visitas y tarda uno o dos
  minutos en volver (0,1 CPU). El frontend lo sabe: al abrirse despierta la
  API, y el login avisa y espera hasta 3 minutos.
- **Mientras la API duerme, las votaciones no se cierran.** Las vencidas se
  cierran juntas en cuanto alguien la despierta.
- **La IA duerme con la API.** La API la despierta al arrancar y cada 10
  minutos (`VigiaIA`). Justo tras un arranque en frío, el primer imprevisto
  puede salir por reglas (`REGLAS_POR_FALLO`).
- **750 horas al mes** compartidas entre los servicios gratuitos de tu cuenta
  de Render. Si se agotan, se suspenden hasta fin de mes. Lo mismo con Neon:
  mientras la API está despierta, sus barridos impiden que la base se
  suspenda.
- **512 MB de memoria.** Medido: la API usa unos 240 MB en el pico. La JVM va
  ajustada en `render.yaml` (`JAVA_TOOL_OPTIONS`).
- **Sin copias automáticas.** Atlas M0 no hace copias y Neon gratuito solo
  permite volver unas horas atrás. Ver [Mantenimiento](#mantenimiento).

## Si algo falla

| Lo que ves | Causa y arreglo |
| --- | --- |
| Render: **Ran out of memory (used over 512MB)** | Baja `MaxRAMPercentage` de 55 a 45 en `JAVA_TOOL_OPTIONS`, **en `render.yaml`**, y haz push. |
| La API no conecta a Postgres y el log menciona `channel_binding` | Quita `&channel_binding=require` de `SPRING_DATASOURCE_URL`. |
| `password authentication failed` o `MongoSecurityException` | Usuario o clave mal copiados. En Mongo: símbolos sin codificar o `<db_password>` sin sustituir. |
| Mongo: `Timed out after 30000 ms while waiting to connect` | Falta `0.0.0.0/0` en **Network Access** de Atlas. |
| La API arranca pero no hay tablas | La base de Neon no estaba vacía: Flyway la dio por versión 1 sin crear nada. Usa una base nueva. |
| La API muere con `Schema-validation: missing column…` | Una entidad cambió sin su migración: falta un `V<n>__*.sql` en `src/main/resources/db/migration`. |
| El navegador da **CORS error** | `HUECKO_CORS_ORIGINS` no tiene el dominio exacto de Vercel (con `https://`, sin barra final). |
| La web sale en **modo demo** | Falta `VITE_API_URL` en Production de Vercel, o no se redesplegó después de ponerla. |
| Consola: **Refused to … Content Security Policy** | Algo carga desde un dominio que `vercel.json` no permite (otra URL de API, otro CDN). Añádelo a la directiva que indique el mensaje. |
| «Demasiados intentos» al entrar | Freno contra fuerza bruta: 5 fallos por correo o 20 intentos por IP cada 15 minutos. Espera un poco. |
| En **Salud**, la IA «no responde» | `HUECKO_IA_URL` no apunta al servicio real, o el token no llegó a `huecko-ia`. Si dice «sin GEMINI_API_KEY», falta la clave. |
| El tiempo real no llega | El WebSocket usa el dominio de la API: revisa CORS, que también lo usa (SockJS). |

## Volver atrás

- **API o IA:** si un despliegue no pasa el healthcheck, Render mantiene la
  versión anterior. Para volver a mano: el servicio → **Events** →
  **Rollback** en el despliegue bueno.
- **Frontend:** en Vercel, **Deployments** → el despliegue bueno → **Instant
  Rollback**.
- **Base de datos:** las migraciones solo avanzan. En el primer despliegue la
  base está vacía, así que no hay datos que proteger.

Cuándo volver atrás: si el login falla para todos, si la API reinicia en
bucle o si la prueba del paso 9 se rompe en un punto que antes funcionaba.

## Mantenimiento

### Publicar una versión nueva

Trabaja en `develop`, deja que pase la CI y fusiona en `main` con una release
(`release/vX.Y.Z`). Render y Vercel despliegan solos al recibir el push en
`main`.

### Cambiar una entidad de la base

Añade una migración nueva, por ejemplo `V3__añadir_campo.sql`, en
`src/main/resources/db/migration`. Nunca edites una migración ya aplicada:
Flyway compara su checksum y se niega a arrancar.

### Copias de seguridad

De vez en cuando, a un sitio **privado** (los repos son públicos: los
artefactos de sus Actions los puede descargar cualquiera):

```bash
pg_dump "postgresql://USUARIO:CLAVE@HOST/neondb?sslmode=require" > huecko-postgres.sql
mongodump --uri "mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/huecko" --out huecko-mongo
```

### Probar cabeceras y CSP antes de publicar

En `huecko-frontend`: `npm run build` y después `npm run preview`. Sirve la
build con las mismas cabeceras que Vercel, CSP incluida.
