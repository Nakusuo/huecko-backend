# Puesta en marcha de Huecko — manual paso a paso

Guía para dejar funcionando el backend y probarlo junto al frontend **desde una
máquina donde no hay nada instalado**. Escrita para Windows 11, que es donde se
está desarrollando; al final hay equivalentes para macOS y Linux.

Tiempo aproximado la primera vez: 30–40 minutos, casi todo descargas.

---

## 0. Qué falta instalar

`huecko-backend` es un proyecto Java con dos bases de datos. Hacen falta dos
cosas, y ninguna viene con Windows:

| Herramienta | Para qué | Versión |
| --- | --- | --- |
| **JDK 17+** | Compilar y ejecutar la aplicación | 17 o superior (LTS) |
| **Docker Desktop** | Levantar Postgres y Mongo sin instalarlos a mano | cualquiera reciente |

> **Maven no está en la lista a propósito.** El repositorio trae el Maven
> Wrapper (`mvnw` / `mvnw.cmd`): la primera vez que lo ejecutas descarga la
> versión correcta de Maven en `~/.m2/wrapper` y la reutiliza. Por eso todos
> los comandos de esta guía usan `.\mvnw.cmd` y no `mvn`.

> ¿Por qué Docker y no instalar Postgres y Mongo directamente? Porque así las
> dos bases se levantan con un solo comando, con la misma versión para todo el
> equipo, y se borran igual de fácil sin dejar servicios corriendo en el
> arranque de Windows.

---

## 1. Instalar las herramientas

### Opción A — con `winget` (recomendada)

`winget` viene incluido en Windows 11. Abre **PowerShell** (no hace falta como
administrador) y ejecuta:

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Docker.DockerDesktop -e
```

Cada uno tarda unos minutos. Docker Desktop pedirá reiniciar el equipo: hazlo
antes de seguir.

> No busques Maven en `winget`: **no está en el catálogo**. `winget install
> --id Apache.Maven` falla sin instalar nada. No hace falta, para eso está el
> wrapper.

### Opción B — instaladores manuales

| Herramienta | Descarga |
| --- | --- |
| JDK 17 (Temurin) | https://adoptium.net/temurin/releases/?version=17 |
| Docker Desktop | https://www.docker.com/products/docker-desktop/ |

### Comprobar que quedó bien

**Cierra y vuelve a abrir la terminal.** Esto no es opcional: Windows entrega la
lista del `PATH` a cada proceso *cuando arranca*, así que una ventana abierta
antes de instalar sigue sin ver los programas nuevos por mucho que se
reinstalen. Ya en una ventana nueva:

```powershell
java -version
docker --version
```

Las dos deben responder con un número de versión. Si alguna dice *"no se
reconoce como un comando"*, revisa el apartado 7.

Además, **Docker Desktop tiene que estar abierto y en marcha** (icono de la
ballena en la bandeja, en estado *Running*). Los comandos `docker` fallan si la
aplicación está cerrada.

---

## 2. Levantar las bases de datos

Desde la carpeta del backend:

```powershell
cd $HOME\Desktop\huecko-backend
Copy-Item .env.example .env
docker compose up -d
```

La primera vez descarga las imágenes de Postgres y Mongo (unos 400 MB). Al
terminar:

```powershell
docker compose ps
```

Deben aparecer `huecko-postgres` y `huecko-mongo` con estado `running (healthy)`.
Si dicen `starting`, espera unos segundos y vuelve a mirar.

**Los datos persisten** en volúmenes de Docker: puedes apagar el equipo y
seguirán ahí. Para empezar completamente de cero, `docker compose down -v` (la
`-v` es la que borra los datos).

---

## 3. Arrancar el backend

En la misma carpeta:

```powershell
.\mvnw.cmd spring-boot:run
```

La primera vez el wrapper descarga Maven y todas las dependencias; tarda varios
minutos y escupe mucho texto. Ya está listo cuando aparece algo como:

```
Started HueckoBackendApplication in 8.123 seconds
Datos de demo listos. Entra con alex.rodriguez@huecko.com / demo1234
```

**Deja esta terminal abierta**: mientras siga ahí, el backend está encendido.
Se apaga con `Ctrl+C`.

### Comprobar que responde

En **otra** terminal:

```powershell
curl.exe http://localhost:8080/api/actuator/health
```

Respuesta esperada: `{"status":"UP"}`

> Usa `curl.exe` con la extensión. `curl` a secas en PowerShell es un alias de
> `Invoke-WebRequest`, que tiene otra sintaxis y confunde.

### Comprobar el login

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"alex.rodriguez@huecko.com\",\"password\":\"demo1234\"}"
```

Debe devolver un `token` y el objeto `user`. Si ves `Credenciales incorrectas`,
los datos de demo no se sembraron: mira el apartado 7.

---

## 4. Arrancar el frontend conectado

En **otra terminal**, sin cerrar la del backend:

```powershell
cd $HOME\Desktop\huecko-frontend
npm install
```

Ahora hay que decirle al frontend que hable con el backend. Edita
`.env.local` y **descomenta** la línea de `VITE_API_URL`, de forma que quede:

```
VITE_API_URL=/api
VITE_BACKEND_PROXY=http://localhost:8080
```

Ese es el único interruptor entre los dos modos:

| `VITE_API_URL` | Modo |
| --- | --- |
| vacía o comentada | **Demo**: datos simulados, no necesita backend |
| `/api` | **Conectado**: todo sale contra `huecko-backend` |

Vite **no relee ese archivo en caliente**: si cambias `.env.local` con el
servidor arrancado, hay que pararlo y volver a arrancarlo.

```powershell
npm run dev
```

Abre la URL que imprime (normalmente `http://localhost:5173`).

---

## 5. Prueba de punta a punta

Con backend y frontend arrancados, en el navegador:

1. **Entra** con `alex.rodriguez@huecko.com` / `demo1234`.
   → Si entra, el login está saliendo del backend real y el JWT quedó guardado.

2. **Mira el horario.** Deben aparecer los bloques sembrados: Cálculo II los
   lunes y miércoles, prácticas los martes, gimnasio los viernes, y un
   **Laboratorio de Redes** marcado como borrador de OCR.
   → Si los ves, la lectura desde Mongo funciona.

3. **Crea un bloque nuevo**, ponle un color y una categoría.

4. **Recarga la página con F5.**
   → El bloque sigue ahí, **con su color y su categoría**. Esta es la prueba
   importante: antes esos dos campos vivían solo en el navegador y se perdían.

5. **Edita el bloque** (cambia la hora) y recarga otra vez. El cambio persiste.

6. **Bórralo** y recarga. No vuelve.

7. **Entra con el otro usuario** (`diana.torres@huecko.com` / `demo1234`).
   → Debe verse un horario **distinto**. Cada usuario ve solo el suyo.

### Verlo por dentro

Mientras haces lo anterior, la terminal del backend va registrando cada
petición. Y puedes mirar los datos directamente:

```powershell
# Usuarios en Postgres
docker exec -it huecko-postgres psql -U huecko -d huecko -c "select id, nombre, email from usuarios;"

# Bloques en Mongo
docker exec -it huecko-mongo mongosh -u huecko -p huecko --authenticationDatabase admin huecko --eval "db.bloques_horario.find({}, {etiqueta:1, categoria:1, color:1, estado:1}).pretty()"
```

Ahí se ve el modelo híbrido funcionando: el usuario en Postgres, sus bloques en
Mongo, enlazados por el UUID.

---

## 6. Probar sin instalar nada (atajo)

Si solo quieres tocar el frontend y no te apetece instalar Java ni Docker, el
repositorio del frontend trae un servidor de mentira que imita las mismas rutas:

```powershell
cd $HOME\Desktop\huecko-frontend
npm run dev:stub      # en una terminal
npm run dev           # en otra
```

Con `VITE_API_URL=/api` la app funciona igual, pero los datos viven en memoria y
se pierden al reiniciar. **No sustituye a la prueba real**: no valida ni el JWT,
ni Postgres, ni Mongo.

---

## 7. Si algo falla

| Síntoma | Causa y solución |
| --- | --- |
| `java`, `mvn` o `docker` "no se reconoce como un comando" | La terminal es anterior a la instalación. Ciérrala y abre una nueva. Si sigue, reinicia el equipo. |
| `docker: error during connect` | Docker Desktop está cerrado. Ábrelo y espera a que el icono diga *Running*. |
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Ya tienes un Postgres ocupando ese puerto. Cambia `POSTGRES_PORT` en `.env` (por ejemplo a `5433`) y repite `docker compose up -d`. Lo mismo con `MONGO_PORT`. |
| El backend arranca y muere con `Connection refused` a Postgres o Mongo | Las bases aún no terminaron de arrancar. Comprueba `docker compose ps` y espera a `healthy`. |
| `Credenciales incorrectas` con el usuario de demo | El seeder no llegó a ejecutarse. Busca en el log la línea `Datos de demo listos`. Si no está, revisa que `huecko.seed.enabled` sea `true` y que no estés en el perfil `prod`. |
| La app carga pero sale *"No fue posible conectar con el backend"* | O el backend está apagado, o `.env.local` no tiene `VITE_API_URL=/api`, o no reiniciaste `npm run dev` tras editarlo. |
| Entras pero el horario sale vacío | Sesión antigua con un token de otro usuario. Cierra sesión y vuelve a entrar. |
| Error de CORS en la consola del navegador | Estás apuntando `VITE_API_URL` directo a `http://localhost:8080` en vez de a `/api`. Usa `/api` y deja que el proxy de Vite haga el trabajo, o añade el origen a `HUECKO_CORS_ORIGINS` en `.env`. |
| Maven falla descargando dependencias | Corta de red o proxy corporativo. Reintenta; Maven continúa donde lo dejó. |
| `mvn` "no se reconoce como un comando" | Correcto: Maven no se instala en este proyecto. Usa `.\mvnw.cmd` (PowerShell) o `./mvnw` (bash). |
| `mvnw.cmd` "no se reconoce" ejecutándolo desde `cmd.exe` | El sistema tiene `NoDefaultCurrentDirectoryInExePath=1`, que impide ejecutar programas del directorio actual sin ruta. Usa PowerShell con `.\mvnw.cmd`, o en `cmd` escribe la ruta completa. |
| `The JAVA_HOME environment variable is not defined correctly` | La ventana es anterior a instalar el JDK. Ábrela de nuevo; el instalador ya deja `JAVA_HOME` definido a nivel de máquina. |

### Empezar de cero

```powershell
docker compose down -v      # borra los contenedores Y los datos
docker compose up -d        # bases vacías otra vez
.\mvnw.cmd spring-boot:run  # vuelve a sembrar los datos de demo
```

---

## 8. macOS y Linux

Los pasos 2 a 7 son idénticos; solo cambia la instalación.

**macOS** (con [Homebrew](https://brew.sh)):

```bash
brew install openjdk@17
brew install --cask docker      # después hay que abrir Docker.app una vez
```

**Ubuntu / Debian:**

```bash
sudo apt update
sudo apt install openjdk-17-jdk
# Docker Engine + plugin de Compose, según https://docs.docker.com/engine/install/
```

En ambos, `curl` funciona sin el `.exe`, y las variables se copian con
`cp .env.example .env`.

---

## Resumen: el día a día

Una vez instalado todo, arrancar el entorno completo son tres comandos en tres
terminales:

```powershell
# 1) bases de datos (se quedan corriendo en segundo plano)
cd $HOME\Desktop\huecko-backend; docker compose up -d

# 2) backend
cd $HOME\Desktop\huecko-backend; .\mvnw.cmd spring-boot:run

# 3) frontend
cd $HOME\Desktop\huecko-frontend; npm run dev
```

Y al terminar el día, `docker compose stop` para liberar memoria sin perder los
datos.
