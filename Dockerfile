# Build inside the image so the deploy needs neither Maven nor Docker locally.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/academy-portal-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Duser.timezone=Asia/Kolkata"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
