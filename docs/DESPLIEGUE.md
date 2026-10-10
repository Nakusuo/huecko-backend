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

- **La API se duerme** tras unos 15 min sin peticiones y tarda **uno o dos
  minutos** en despertar (0,1 CPU). El frontend lo sabe: al abrirse despierta
  la API, y el login avisa y espera hasta 3 minutos.
- **Mientras la API duerme, las votaciones no se cierran.** Las que vencieron
  se cierran todas juntas en cuanto alguien la despierta.
- **La IA duerme y despierta con la API.** Al arrancar y cada 10 minutos, la
  API la llama para tenerla despierta (`VigiaIA`). Justo después de un
  arranque en frío, el primer imprevisto puede salir por reglas
  (`REGLAS_POR_FALLO`) mientras la IA termina de despertar.
- **750 horas al mes compartidas** entre los servicios gratuitos de tu cuenta
  de Render. Si se agotan, Render los suspende hasta fin de mes. No dejes el
  panel de admin abierto en una pestaña: consulta la API cada 15 s y la
  mantiene despierta (y gastando horas). Lo mismo con Neon: mientras la API
  está despierta, sus barridos impiden que la base se suspenda.
- La API tiene 512 MB de RAM. La JVM va ajustada en `render.yaml`
  (`JAVA_TOOL_OPTIONS`).

---

## 1. Postgres en Neon

1. https://neon.tech → **New project**. Región **AWS US East (N. Virginia)**,
   la misma que Render (`virginia` en `render.yaml`). La base debe estar
   **vacía**: Flyway crea las tablas en el primer arranque.
2. En **Connection details**, desactiva **Connection pooling** (Flyway
   necesita la conexión directa: el host **sin** `-pooler`).
3. Te da algo como:

   ```
   postgresql://neondb_owner:CLAVE@ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```

   De ahí salen tres valores para Render. **Quita `&channel_binding=require`**:
   el driver de Java no reconoce ese nombre y falla al conectar.

   | Variable | Valor |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx-123.us-east-1.aws.neon.tech/neondb?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
   | `SPRING_DATASOURCE_PASSWORD` | `CLAVE` |

## 2. Mongo en Atlas

1. https://cloud.mongodb.com → **Create** → **M0 (Free)**, proveedor AWS,
   región **N. Virginia (us-east-1)**.
2. **Database Access** → crea un usuario con contraseña (mejor solo letras y
   números; si lleva símbolos, hay que codificarlos en la URL).
3. **Network Access** → **Add IP Address** → `0.0.0.0/0`. Render gratuito no
   tiene IP fija, así que no hay otra forma; la protección es el usuario y la
   contraseña.
4. **Connect** → **Drivers** copia una cadena como esta:

   ```
   mongodb+srv://USUARIO:<db_password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0
   ```

   Cambia `<db_password>` por la contraseña y **`/?` por `/huecko?`** (el
   nombre de la base):

   | Variable | Valor |
   | --- | --- |
   | `SPRING_DATA_MONGODB_URI` | `mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/huecko?retryWrites=true&w=majority&appName=Cluster0` |

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

**Cambiar `JAVA_TOOL_OPTIONS` u otra variable con `value:` en `render.yaml`**:
edita el archivo y haz push a `main`. Si la cambias a mano en **Environment**,
el siguiente sync del Blueprint la vuelve a dejar como en el archivo. Las de
`sync: false` sí se cambian en **Environment** (con **Save and deploy**, o se
aplican en el siguiente despliegue).

## 5. Frontend en Vercel

1. https://vercel.com → **Add New** → **Project** → importa
   `huecko-frontend`. Si ya existía el proyecto, ve a sus **Settings**.
2. Framework **Vite**; la rama de producción, `main`.
3. **Environment Variables**, solo en el entorno **Production**:

   | Variable | Valor |
   | --- | --- |
   | `VITE_API_URL` | `https://huecko-api.onrender.com/api` |

   Se lee al **construir**: si la cambias, hay que redesplegar. Sin ella la web
   sale en modo demostración. Las previews de cada PR se quedan así, en modo
   demo, a propósito: para conectarlas habría que abrir CORS a un comodín de
   `vercel.app`, y cualquiera puede crear un proyecto que encaje con él.
4. **Deploy**. Apunta el dominio que te da (p. ej. `https://huecko.vercel.app`).
5. En Render → `huecko-api` → **Environment**, pon ese dominio exacto en
   `HUECKO_CORS_ORIGINS` (con `https://` y sin barra final) y **Save and
   deploy**.

## 6. Tu cuenta de administrador

**El orden importa.** Quien tenga una cuenta con un correo de
`HUECKO_ADMIN_EMAILS` pasa a admin cada vez que la API arranca, y hoy no se
verifica que el correo sea suyo.

1. Regístrate en la web con tu correo.
2. Render → `huecko-api` → **Environment** → `HUECKO_ADMIN_EMAILS` = ese
   correo (si la variable no aparece, créala con **Add Environment
   Variable**). **Save and deploy**.
3. Cierra sesión y vuelve a entrar: el rol viaja en el token.

Nunca pongas un correo que aún no tenga cuenta. Lo que ya no puede pasar:
nadie puede cambiarse el correo a uno de esa lista, cambiar el correo pide la
contraseña actual, y una cuenta admin no puede cambiar su correo desde la app.

## 7. Prueba de punta a punta

1. Abre la web: si la API dormía, el login avisa de que está despertando.
2. Regístrate con dos cuentas (una en una ventana privada).
3. Crea un grupo y añade a la segunda por correo; marcad horarios.
4. Propón un plan, votad y ciérralo.
5. Con la segunda cuenta, reporta un imprevisto con un motivo («tengo las
   entradas de todos»). La votación exprés llega con la sugerencia de la IA.
6. Como admin, en **Salud** la fila «Servicio de IA» debe decir que responde
   y tiene Gemini.

## Copias de seguridad

Atlas M0 no hace copias y Neon gratuito solo permite volver unas horas
atrás. Si los datos importan, saca copias de vez en cuando a un sitio
**privado** (los repos son públicos: los artefactos de sus Actions los puede
descargar cualquiera):

```bash
pg_dump "postgresql://USUARIO:CLAVE@HOST/neondb?sslmode=require" > huecko-postgres.sql
mongodump --uri "mongodb+srv://USUARIO:CLAVE@cluster0.xxxxx.mongodb.net/huecko" --out huecko-mongo
```

## Si algo falla

| Síntoma | Causa y arreglo |
| --- | --- |
| Render: **Ran out of memory (used over 512MB)** | Baja `MaxRAMPercentage` en `JAVA_TOOL_OPTIONS` (de 55 a 45) **en `render.yaml`** y haz push. |
| La API muere con `Schema-validation: missing column…` | Una entidad cambió sin su migración. Añade `V<n>__*.sql` en `src/main/resources/db/migration`. |
| La API arranca pero no hay tablas | La base de Neon no estaba vacía: Flyway la dio por versión 1 sin crear nada. Usa una base nueva. |
| `The connection attempt failed` con `channel_binding` en el log | Sobra `&channel_binding=require` en `SPRING_DATASOURCE_URL`. |
| `FATAL: password authentication failed` / `MongoSecurityException` | Usuario o clave mal copiados. En Mongo, símbolos sin codificar en la URL, o `<db_password>` sin sustituir. |
| `Timed out after 30000 ms while waiting to connect` (Mongo) | Falta `0.0.0.0/0` en **Network Access** de Atlas. |
| El navegador da **CORS error** | `HUECKO_CORS_ORIGINS` no incluye el dominio exacto de Vercel (con `https://`, sin barra final). |
| La web entra en **modo demo** | Falta `VITE_API_URL` en Production de Vercel o no se redesplegó después de ponerla. |
| «Demasiados intentos» al entrar | Límite contra fuerza bruta: 5 fallos por correo o 20 intentos por IP cada 15 min. Espera un poco. |
| En **Salud**, la IA «no responde» | `HUECKO_IA_URL` no apunta al servicio real, o el token no llegó a `huecko-ia`. Si dice «sin GEMINI_API_KEY», falta la clave. |
| Tiempo real no llega | El WebSocket va al mismo dominio de la API; revisa CORS (también lo usa SockJS). |
