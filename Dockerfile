# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system vippela \
    && useradd --system --gid vippela --home-dir /app --shell /usr/sbin/nologin vippela

WORKDIR /app
COPY --from=build --chown=vippela:vippela /workspace/build/libs/backend-0.0.1-SNAPSHOT.jar ./backend.jar

ENV PORT=8080
EXPOSE 8080

USER vippela
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
