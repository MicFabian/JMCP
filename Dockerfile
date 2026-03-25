FROM gradle:9.1.0-jdk25 AS builder
WORKDIR /app
COPY build.gradle settings.gradle gradle.properties ./
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system mcp && useradd --system --gid mcp --home-dir /app --shell /usr/sbin/nologin mcp \
    && mkdir -p /app/data \
    && chown -R mcp:mcp /app
COPY --chown=mcp:mcp --from=builder /app/build/libs/java-mcp-0.1.0-SNAPSHOT.jar /app/mcp.jar
USER mcp:mcp
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED -XX:MaxRAMPercentage=80.0"
EXPOSE 8080
EXPOSE 9090
ENTRYPOINT ["java","-jar","/app/mcp.jar"]
