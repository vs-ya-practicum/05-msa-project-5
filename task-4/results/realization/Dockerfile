FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
