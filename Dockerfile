# ── Stage 1: build do frontend (React + Vite) ──
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend/ ./
RUN npm run build

# ── Stage 2: build do backend (Spring Boot) ──
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B dependency:go-offline || true
COPY . .
# Copia o build do frontend pro static/ (servido pelo Spring Boot)
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN ./mvnw -B clean package -DskipTests

# ── Stage 3: runtime ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 10000
# Render injeta PORT no env em runtime; o entrypoint repassa para SERVER_PORT.
# (ENV no Dockerfile NAO expande $PORT — por isso usamos shell exec.)
ENTRYPOINT ["sh", "-c", "SERVER_PORT=${PORT:-9090} java -jar app.jar"]
