# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests clean package

FROM eclipse-temurin:17.0.19_10-jre-jammy@sha256:475d8e96b4b2bfe08999e5e854755c773af1581acdf959a4545d88f0696a2339
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8096

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
