FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
COPY --from=build /app/target/*.jar app.jar
COPY data /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
