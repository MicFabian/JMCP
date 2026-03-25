FROM gradle:9.1.0-jdk25 AS builder
WORKDIR /app
COPY build.gradle settings.gradle gradle.properties ./
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system --gid 10001 mcp && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin mcp \
    && mkdir -p /app/data \
    && chown -R 10001:10001 /app
COPY --chown=10001:10001 --from=builder /app/build/libs/java-mcp-0.1.0-SNAPSHOT.jar /app/mcp.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -XX:MaxRAMPercentage=80.0"
EXPOSE 8080
EXPOSE 9090
ENTRYPOINT ["java","-jar","/app/mcp.jar"]
