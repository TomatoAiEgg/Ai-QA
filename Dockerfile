FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/target/AI-QA-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 7000

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
