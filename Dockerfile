FROM node:22-alpine AS frontend
WORKDIR /workspace/frontend
ARG VITE_BASE_PATH=/eventpulse/
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
COPY frontend/package.json ./
COPY frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY pom.xml ./
COPY src/ src/
COPY --from=frontend /workspace/frontend/dist/ src/main/resources/static/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}
WORKDIR /app
RUN mkdir -p /app/data && chown 10001:10001 /app/data
COPY --from=backend --chown=10001:10001 /workspace/target/cloud-native-hackathon-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
