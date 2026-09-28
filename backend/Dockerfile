# =====================================================================
# Imagen del backend de AdArena (multi-etapa).
#   Etapa 1 "build": compila con Maven + JDK 21.
#   Etapa 2 "runtime": solo el JRE 21 y la aplicación → imagen pequeña y sin herramientas
#   de compilación. Se ejecuta con un usuario sin privilegios.
# =====================================================================

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Primero solo el pom.xml: Docker cachea esta capa y no vuelve a descargar las dependencias
# mientras el pom no cambie (compilaciones mucho más rápidas).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Separa el JAR en capas (dependencias / aplicación) para que las actualizaciones sean ligeras
RUN java -Djarmode=tools -jar target/adarena-backend.jar extract --layers --launcher --destination extracted

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre

RUN groupadd --system adarena && useradd --system --gid adarena --no-create-home adarena
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER adarena
EXPOSE 8080

# Ajustado para el plan gratuito de Render (512 MB de memoria y poca CPU):
#  - MaxRAMPercentage=55: el "heap" usa como mucho ~280 MB; el resto es para Java en sí (clases,
#    hilos, código compilado). Con más, el contenedor se pasaría de 512 MB y lo matarían.
#  - SerialGC: el recolector de basura más ligero (ideal con una sola CPU pequeña).
#  - TieredStopAtLevel=1: compila menos (arranca más rápido y gasta menos memoria).
#  - Xss512k, ReservedCodeCacheSize, MaxMetaspaceSize: menos memoria por hilo y límites fijos.
#  - ExitOnOutOfMemoryError: si aun así se queda sin memoria, se cierra y Render lo reinicia.
# Con más memoria (plan de pago) puedes cambiarlos en la variable JAVA_OPTS del panel.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
