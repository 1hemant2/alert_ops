# syntax=docker/dockerfile:1
FROM maven:3.9.12-eclipse-temurin-17@sha256:a0603aab698040d9c94259f379ec0487da1678560748d6c7508483034033c53d AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7
WORKDIR /app

# The pinned base can lag behind Ubuntu security updates; install the current
# patched packages available from the Jammy repository.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        libssl3 \
        openssl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8096

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
