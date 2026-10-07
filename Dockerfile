# Imagen de huecko-backend.
#
#   docker build -t huecko-backend .
#   docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod huecko-backend
#
# El esquema de Postgres lo crea Flyway al arrancar, así que basta con una base
# vacía. Las variables que necesita están en .env.example.

# --- Compilación ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# Primero solo lo que define las dependencias: mientras no cambie el pom, esta
# capa sale de la caché y no se vuelve a descargar medio Maven Central.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
# Los tests ya pasan en la CI; aquí solo se empaqueta.
RUN ./mvnw -B -ntp -DskipTests package \
    && cp target/huecko-backend-*.jar /src/app.jar

# --- Ejecución ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Sin privilegios de root dentro del contenedor.
RUN useradd --system --create-home huecko
COPY --from=build /src/app.jar app.jar
USER huecko

# Por defecto en modo despliegue; en local se puede pasar dev.
ENV SPRING_PROFILES_ACTIVE=prod
# Ajusta la memoria de la JVM al límite del contenedor, no al de la máquina.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD wget -qO- "http://localhost:${PORT:-8080}/api/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
