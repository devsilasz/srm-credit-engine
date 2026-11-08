FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Camada separada: mudar só o código não baixa as dependências de novo.
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Sem testes: Testcontainers dentro do docker build exigiria Docker-in-Docker. Eles rodam no CI.
RUN ./mvnw -B -q -DskipTests package

# A imagem alpine traz o wget usado no healthcheck do compose.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/target/credit-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
