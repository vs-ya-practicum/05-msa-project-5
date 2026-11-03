FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app.jar
ADD https://github.com/prometheus/jmx_exporter/releases/download/1.5.0/jmx_prometheus_javaagent-1.5.0.jar /app/jmx-prometheus-javaagent.jar
COPY jmx-exporter.yml /app/jmx-exporter.yml
EXPOSE 9404
ENTRYPOINT ["java","-javaagent:/app/jmx-prometheus-javaagent.jar=9404:/app/jmx-exporter.yml","-jar","/app.jar"]
