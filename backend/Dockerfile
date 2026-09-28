FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# OCR runs in a separate container; keep the Java API image small.
RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app/data/documents \
    && chown -R appuser /app
COPY --from=build /workspace/target/dynamic-form-backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
