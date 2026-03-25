#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
LIBRARY_ID="${1:-/spring-projects/spring-security}"
TOPIC="${2:-csrf}"
TOKENS="${3:-5000}"
LIMIT="${4:-5}"
MODE="${5:-HYBRID}"
ESCAPED_LIBRARY_ID=$(printf '%s' "$LIBRARY_ID" | sed 's/\\/\\\\/g; s/"/\\"/g')
ESCAPED_TOPIC=$(printf '%s' "$TOPIC" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"library_id":"${ESCAPED_LIBRARY_ID}","topic":"${ESCAPED_TOPIC}","tokens":${TOKENS},"limit":${LIMIT},"mode":"${MODE}"}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/GetLibraryDocs
