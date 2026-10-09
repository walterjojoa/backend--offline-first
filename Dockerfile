# Etapa 1: compilar con el JDK
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Etapa 2: imagen liviana solo con el JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
# No correr como root dentro del contenedor
RUN useradd --system --uid 1001 lacocha
COPY --from=build --chown=lacocha /app/target/lacocha-backend.jar app.jar
USER lacocha
# El plan gratis de Render tiene 512 MB de RAM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
