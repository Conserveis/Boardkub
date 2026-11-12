# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so code changes don't re-download them
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Apply OS security updates published after the base image was built
RUN apt-get update \
 && apt-get -y upgrade --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*

# Fixed numeric UID/GID so Kubernetes can verify runAsNonRoot (k8s/base/backend.yaml)
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid 10001 --no-create-home app
COPY --from=build --chown=10001:10001 /build/target/*.jar app.jar
# Image folder for STORAGE_TYPE=local (docker compose). On GKE images go to Cloud Storage.
RUN mkdir -p /data/media && chown 10001:10001 /data/media
ENV MEDIA_LOCAL_DIR=/data/media
USER 10001:10001

EXPOSE 8080

# Keep the JVM heap inside the container memory limit
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Djava.io.tmpdir=/tmp"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
