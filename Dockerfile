# Сборка: JDK + Gradle wrapper, на выходе один jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# На Windows git может заменить переводы строк в gradlew на CRLF, тогда Linux его не запустит
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Запуск: только JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system shop
COPY --from=build /src/build/libs/*.jar app.jar
USER shop
EXPOSE 8080
# На маленьких тарифах (512 МБ) JVM должна брать память от лимита контейнера
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "app.jar"]
