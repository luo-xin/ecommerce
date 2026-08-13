FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/ecommerce-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082 19090
ENTRYPOINT ["java", "-jar", "app.jar"]
