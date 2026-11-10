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
# The image always runs as production (no demo seed, required secrets) unless told otherwise.
ENV SPRING_PROFILES_ACTIVE=prod
# Memory for a small instance (Render free: 512 MB): about 60% for the heap, the rest for metaspace,
# thread stacks and the JVM itself. Override JAVA_TOOL_OPTIONS on Render for a bigger instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=150m"
RUN useradd --system --no-create-home appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
# The app listens on $PORT (Render sets it), or 8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
