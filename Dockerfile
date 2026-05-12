# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
RUN mkdir -p /root/.m2 && cat <<'EOF' > /root/.m2/settings.xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>Aliyun Maven Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q -s /root/.m2/settings.xml -DskipTests dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -s /root/.m2/settings.xml -DskipTests clean package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/target/AI-QA-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 17000

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
