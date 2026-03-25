#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
LIBRARY_NAME="${1:-spring security}"
TOPIC="${2:-csrf}"
LIMIT="${3:-5}"
ESCAPED_LIBRARY_NAME=$(printf '%s' "$LIBRARY_NAME" | sed 's/\\/\\\\/g; s/"/\\"/g')
ESCAPED_TOPIC=$(printf '%s' "$TOPIC" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"library_name":"${ESCAPED_LIBRARY_NAME}","topic":"${ESCAPED_TOPIC}","limit":${LIMIT}}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/ResolveLibraryId
