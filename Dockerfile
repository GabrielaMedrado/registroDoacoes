# syntax=docker/dockerfile:1

# 1. Build do React
FROM node:26-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

# 2. JAR com o React embutido (os testes rodam no CI, que tem Docker para o Testcontainers)
FROM eclipse-temurin:25-jdk AS backend
ARG VERSAO=0.0.0-dev
WORKDIR /src/backend
COPY config/ /src/config/
COPY backend/ ./
COPY --from=frontend /src/frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Drevision="${VERSAO}" \
    && cp "target/doacoes-${VERSAO}.jar" /app.jar

# 3. Execução, sem root
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app
WORKDIR /app
COPY --from=backend /app.jar /app/app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
