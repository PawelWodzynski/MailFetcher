# Etap 1: Budowanie aplikacji
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
# Kopiowanie zawartości katalogu backend
COPY . .
# Zapewnienie uprawnień do wykonania gradlew
RUN chmod +x ./gradlew
# Budowanie projektu
RUN ./gradlew build --no-daemon

# Etap 2: Uruchamianie aplikacji
FROM openjdk:21-jdk
WORKDIR /app
# Kopiowanie pliku JAR z etapu budowania
COPY --from=build /app/build/libs/demo-mail-downloader.jar /app/app.jar

EXPOSE 8080
# Proste uruchomienie JAR-a
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
