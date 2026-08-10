# =============================================================================
# Blaze Event Hub - Multi-stage Docker build
# =============================================================================
# Stage 1: Build frontend (React + Vite)
# Stage 2: Build backend (Spring Boot + Maven)
# Stage 3: Runtime (JRE only)
# =============================================================================

# Stage 1: Build frontend
FROM node:20-alpine AS frontend-build

# The API key is baked into the SPA bundle at BUILD time (import.meta.env).
# Render passes build-time env vars as --build-arg; without this ARG the
# bundle silently falls back to 'dev-local-key' and every /api/** call 401s.
ARG VITE_NOLLEN_API_KEY
ENV VITE_NOLLEN_API_KEY=${VITE_NOLLEN_API_KEY}

WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./

# Fail fast instead of deploying a bundle that 401s on every API call.
RUN if [ -z "$VITE_NOLLEN_API_KEY" ]; then \
      echo "ERROR: VITE_NOLLEN_API_KEY build arg is required (must equal the backend NOLLEN_API_KEY). See README 'Build único'." >&2; \
      exit 1; \
    fi
RUN npm run build

# Stage 2: Build backend
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app

# Copy Maven wrapper and pom first for better layer caching
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Copy frontend dist into static resources (pom.xml copies it to jar)
COPY --from=frontend-build /app/frontend/dist ./frontend/dist

# Build the jar (skip tests for faster build, run them in CI)
RUN ./mvnw package -DskipTests -B

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the jar from build stage
COPY --from=backend-build /app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# EXPOSE is documentation only; Render routes traffic to the $PORT env var,
# which application.yml now honors (server.port = SERVER_PORT > PORT > 8080).
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider "http://localhost:${PORT:-8080}/api/health" || exit 1

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]