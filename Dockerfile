# Etapa 1: compila el proyecto con Maven y genera target/srb.jar
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package

# Etapa 2: imagen liviana que solo trae Java y el .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/target/srb.jar srb.jar
# Cada servicio de docker-compose indica qué clase ejecutar
CMD ["java", "-cp", "srb.jar", "srb.clientes.GeneradorCarga"]
