#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
BUILD_FILE="${1:-plugins { id 'org.springframework.boot' version '3.3.2' } java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }}"
CODE="${2:-import javax.servlet.*; class Demo { void run(){ System.out.println(\"x\"); } }}"
TARGET_JAVA="${3:-25}"
TARGET_BOOT="${4:-4.0.0}"
INCLUDE_DOCS="${5:-true}"

escape_json() {
  printf '%s' "$1" | sed ':a;N;$!ba;s/\\/\\\\/g;s/"/\\"/g;s/\n/\\n/g'
}

ESCAPED_BUILD_FILE="$(escape_json "$BUILD_FILE")"
ESCAPED_CODE="$(escape_json "$CODE")"

PAYLOAD=$(cat <<JSON
{"build_file":"${ESCAPED_BUILD_FILE}","build_file_path":"build.gradle","code":"${ESCAPED_CODE}","target_java_version":${TARGET_JAVA},"target_spring_boot_version":"${TARGET_BOOT}","include_docs":${INCLUDE_DOCS}}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/MigrationAssistant
