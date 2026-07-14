# syntax=docker/dockerfile:1

# Frontend: o lockfile e obrigatorio; o build falha se package.json e lock divergirem.
FROM node:20-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Backend: Java 21 e Maven Wrapper definido pelo proprio repositorio.
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
COPY --from=frontend-build /workspace/frontend/dist/ src/main/resources/static/
RUN ./mvnw -B -ntp clean package -DskipTests

# Runtime minimo: somente JRE, artefato empacotado e usuario sem privilegios.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 eventhub \
    && useradd --system --uid 10001 --gid eventhub --home-dir /app --shell /usr/sbin/nologin eventhub
COPY --from=backend-build --chown=eventhub:eventhub /workspace/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=10000

USER eventhub
EXPOSE 10000

# Render fornece PORT; outros ambientes podem usar SERVER_PORT.
ENTRYPOINT ["sh", "-c", "port=${PORT:-${SERVER_PORT:-10000}}; exec java \"-Dserver.port=$port\" -jar /app/app.jar"]
