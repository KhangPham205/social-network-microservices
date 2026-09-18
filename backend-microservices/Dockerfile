# Stage 1: Build file JAR
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build bỏ qua test để deploy cho nhanh
RUN mvn clean package -DskipTests

# Stage 2: Chạy file JAR
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Giới hạn RAM cho Java để không bị Render dập vì quá tải 512MB
ENTRYPOINT ["java", "-Xmx300m", "-jar", "app.jar"]