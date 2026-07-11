# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8096
ENV SPRING_PROFILES_ACTIVE=prod

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
