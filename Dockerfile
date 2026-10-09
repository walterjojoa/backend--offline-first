# Stage 1: build with the JDK
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Stage 2: lightweight image with only the JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
# Do not run as root inside the container
RUN useradd --system --uid 1001 lacocha
COPY --from=build --chown=lacocha /app/target/lacocha-backend.jar app.jar
USER lacocha
# Render's free plan has 512 MB of RAM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
