#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"
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
{"buildFile":"${ESCAPED_BUILD_FILE}","buildFilePath":"build.gradle","code":"${ESCAPED_CODE}","targetJavaVersion":${TARGET_JAVA},"targetSpringBootVersion":"${TARGET_BOOT}","includeDocs":${INCLUDE_DOCS}}
JSON
)

curl -s -X POST "${BASE_URL}/api/tools/migration-assistant" \
  -H "Content-Type: application/json" \
  --data "$PAYLOAD"
