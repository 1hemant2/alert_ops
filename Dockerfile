# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests clean package

FROM eclipse-temurin:17.0.20.1_1-jre-jammy@sha256:0776d74b60f5a0bf34d1dc8ee339bbb21d7243c9d50b134c8de69a8e822e9ede
WORKDIR /app

# The pinned base can lag behind Ubuntu security updates.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        libssl3=3.0.2-0ubuntu1.30 \
        openssl=3.0.2-0ubuntu1.30 \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8096

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
