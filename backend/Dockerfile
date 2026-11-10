# Build context is the backend/ folder: docker build -t placementhub-backend backend

# Stage 1: build the jar. Tests are skipped here because CI runs them (they need a database).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN sh ./mvnw -B -q -DskipTests package

# Stage 2: only a JRE and the jar, so the final image has no compiler, Maven or source code.
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
# The app listens on $PORT (Render sets it), or 8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
