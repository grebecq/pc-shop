# Сборка: JDK + Gradle wrapper, на выходе один jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Запуск: только JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system shop
COPY --from=build /src/build/libs/*.jar app.jar
USER shop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
