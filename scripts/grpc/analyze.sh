#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
INPUT="${1:-class Demo { void run(){ System.out.println(\"x\"); } }}"

if [[ -f "$INPUT" ]]; then
  CODE_CONTENT=$(tr '\n' ' ' < "$INPUT")
  FILE_NAME=$(basename "$INPUT")
else
  CODE_CONTENT="$INPUT"
  FILE_NAME="InlineSnippet.java"
fi

ESCAPED_CODE=$(printf '%s' "$CODE_CONTENT" | sed 's/\\/\\\\/g; s/"/\\"/g')
ESCAPED_FILE=$(printf '%s' "$FILE_NAME" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"fileName":"${ESCAPED_FILE}","code":"${ESCAPED_CODE}"}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/Analyze
