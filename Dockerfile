FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/wrapper ./gradle/wrapper
COPY src ./src
RUN bash gradlew --no-daemon bootJar -Pkotlin.compiler.execution.strategy=in-process

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --uid 10001 --user-group --no-create-home vippela
COPY --from=build /src/build/libs/backend-0.0.1-SNAPSHOT.jar /app/backend.jar
USER 10001:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
