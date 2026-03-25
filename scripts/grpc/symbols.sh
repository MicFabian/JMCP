#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
CODE="${1:-class A { void run(){ helper(); } void helper(){} }}"
ESCAPED_CODE=$(printf '%s' "$CODE" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"code":"${ESCAPED_CODE}"}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/Symbols
