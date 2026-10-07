# Despliegue de Huecko (plan gratuito)

Cómo publicar los tres repos sin pagar nada:

| Pieza | Dónde | Repo |
| --- | --- | --- |
| Frontend | Vercel | `huecko-frontend` |
| API | Render (Docker) | `huecko-backend` |
| Servicio de IA | Render (Docker) | `huecko-ai-service` |
| Postgres | Neon | — |
| Mongo | MongoDB Atlas (M0) | — |
| Modelo | Google AI Studio (Gemini) | — |

Todo se despliega desde la rama **`main`** de cada repo. Lo que llega a
`develop` no sale hasta la siguiente release.

## Lo que implica el plan gratuito

- **La API y la IA se duermen** tras unos 15 min sin peticiones y tardan
  alrededor de un minuto en despertar. El frontend lo sabe: al abrirse
  despierta la API y el login avisa y espera.
- **Mientras la API duerme, las votaciones no se cierran.** Las que vencieron
  se cierran todas juntas en cuanto alguien la despierta.
- **La IA también se duerme por su cuenta.** Si la API la llama dormida, no
  espera: usa las reglas (`REGLAS_POR_FALLO`) y la IA despierta para la
  siguiente.
- La API tiene 512 MB de RAM. La JVM va ajustada para eso en `render.yaml`
  (`JAVA_TOOL_OPTIONS`).

---

## 1. Postgres en Neon

1. https://neon.tech → **New project**. Región **AWS US East (N. Virginia)**,
   la misma que Render (`virginia` en `render.yaml`).
2. En **Connection details**, desactiva **Connection pooling** (Flyway
   necesita la conexión directa: el host **sin** `-pooler`).
3. Te da algo como:

   ```
   postgresql://neondb_owner:CLAVE@ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require
   ```

   De ahí salen tres valores para Render:

   | Variable | Valor |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
   | `SPRING_DATASOURCE_PASSWORD` | `CLAVE` |

Las tablas no se crean a mano: Flyway las crea al primer arranque.

## 2. Mongo en Atlas

1. https://cloud.mongodb.com → **Create** → **M0 (Free)**, proveedor AWS,
   región **N. Virginia (us-east-1)**.
2. **Database Access** → crea un usuario con contraseña (mejor solo letras y
   números; si lleva símbolos, hay que codificarlos en la URL).
3. **Network Access** → **Add IP Address** → `0.0.0.0/0`. Render gratuito no
   tiene IP fija, así que no hay otra forma; la protección es el usuario y la
   contraseña.
4. **Connect** → **Drivers** → copia la cadena y añade el nombre de la base
   (`/huecko`) antes del `?`:

   | Variable | Valor |
   | --- | --- |
   | `SPRING_DATA_MONGODB_URI` | `mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/huecko?retryWrites=true&w=majority` |

Los índices los crea la API al arrancar.

## 3. Clave de Gemini

https://aistudio.google.com → **Get API key** → **Create API key**. Será
`GEMINI_API_KEY`.

## 4. API e IA en Render

1. https://render.com → entra con GitHub y dale acceso a **los dos** repos,
   `huecko-backend` y `huecko-ai-service` (el Blueprint crea un servicio de
   cada uno).
2. **New** → **Blueprint** → elige `huecko-backend`. Render lee `render.yaml`
   y propone dos servicios, `huecko-api` y `huecko-ia`.
3. Te pide los valores marcados como `sync: false`:

   | Variable | Qué poner |
   | --- | --- |
   | `SPRING_DATASOURCE_*`, `SPRING_DATA_MONGODB_URI` | Lo de los pasos 1 y 2. |
   | `GEMINI_API_KEY` | Lo del paso 3. |
   | `HUECKO_IA_URL` | `https://huecko-ia.onrender.com`. Si Render le da otro nombre al servicio (sale en su página), corrígelo después. |
   | `HUECKO_CORS_ORIGINS` | De momento `https://huecko.vercel.app`; se ajusta en el paso 5. |
   | `HUECKO_ADMIN_EMAILS` | **Vacío.** Ver el paso 6. |

   `HUECKO_JWT_SECRET` y `HUECKO_IA_TOKEN` los genera Render; la IA recibe el
   mismo token que la API sin que tengas que copiarlo.
4. **Apply**. El primer build tarda varios minutos (Maven descarga todo).
5. Comprueba:
   - `https://huecko-api.onrender.com/api/actuator/health` → `{"status":"UP"}`
   - `https://huecko-ia.onrender.com/salud` → `"gemini": true`

## 5. Frontend en Vercel

1. https://vercel.com → **Add New** → **Project** → importa
   `huecko-frontend`. Si ya existía el proyecto, ve a sus **Settings**.
2. Framework **Vite**; la rama de producción, `main`.
3. **Environment Variables**:

   | Variable | Valor |
   | --- | --- |
   | `VITE_API_URL` | `https://huecko-api.onrender.com/api` |

   Se lee al **construir**: si la cambias, hay que redesplegar. Sin ella, la
   web publicada sale en modo demostración.
4. **Deploy**. Apunta el dominio que te da (p. ej. `https://huecko.vercel.app`).
5. En Render → `huecko-api` → **Environment**, pon en `HUECKO_CORS_ORIGINS`
   ese dominio y, si quieres que funcionen las previews de cada PR, también
   el patrón:

   ```
   https://huecko.vercel.app,https://huecko-*.vercel.app
   ```

   Render redespliega solo al guardar.

## 6. Tu cuenta de administrador

**El orden importa.** Quien tenga una cuenta con un correo de
`HUECKO_ADMIN_EMAILS` será admin al siguiente arranque, y hoy no se verifica
que el correo sea suyo.

1. Regístrate en la web con tu correo.
2. Render → `huecko-api` → **Environment** → `HUECKO_ADMIN_EMAILS` = ese
   correo. Guarda (reinicia).
3. Cierra sesión y vuelve a entrar: el rol viaja en el token.

Nunca pongas un correo que aún no tenga cuenta.

## 7. Prueba de punta a punta

1. Abre la web: si la API dormía, el login avisa de que está despertando.
2. Regístrate con dos cuentas (una en una ventana privada).
3. Crea un grupo y añade a la segunda por correo; marcad horarios.
4. Propón un plan, votad y ciérralo.
5. Con la segunda cuenta, reporta un imprevisto con un motivo («tengo las
   entradas de todos»). Si la IA estaba despierta, la votación exprés llega
   con su sugerencia; si no, con la criticidad por reglas.

## Si algo falla

| Síntoma | Causa y arreglo |
| --- | --- |
| Render: **Ran out of memory (used over 512MB)** | Baja `MaxRAMPercentage` en `JAVA_TOOL_OPTIONS` (de 60 a 50) desde **Environment**. |
| La API muere con `Schema-validation: missing column…` | Una entidad cambió sin su migración. Añade `V<n>__*.sql` en `src/main/resources/db/migration`. |
| `FATAL: password authentication failed` / `MongoSecurityException` | Usuario o clave mal copiados. En Mongo, símbolos sin codificar en la URL. |
| `Timed out after 30000 ms while waiting to connect` (Mongo) | Falta `0.0.0.0/0` en **Network Access** de Atlas. |
| El navegador da **CORS error** | `HUECKO_CORS_ORIGINS` no incluye el dominio exacto de Vercel (con `https://`, sin barra final). |
| La web entra en **modo demo** | Falta `VITE_API_URL` en Vercel o no se redesplegó después de ponerla. |
| La IA nunca opina (`REGLAS_POR_FALLO` siempre) | `HUECKO_IA_URL` no apunta al servicio real, o falta `GEMINI_API_KEY` (`/salud` dice `"gemini": false`). |
| Tiempo real no llega | El WebSocket va al mismo dominio de la API; revisa CORS (también lo usa SockJS). |
