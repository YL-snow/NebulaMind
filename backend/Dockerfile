FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src
COPY settings.xml .

RUN mvn -s settings.xml clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/target/nebulamind-backend-1.0.0-SNAPSHOT.jar app.jar

RUN addgroup -S nebulamind && adduser -S nebulamind -G nebulamind
USER nebulamind

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
