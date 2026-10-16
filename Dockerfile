# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# Dependencies first, so this layer is cached until the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/target/languagelearner-backend.jar app.jar
USER app
# Cloud Run sets PORT; 8080 is the default for local runs.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
