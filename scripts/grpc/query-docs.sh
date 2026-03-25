#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
LIBRARY_ID="${1:-/spring-projects/spring-security}"
QUERY="${2:-csrf}"
TOKENS="${3:-5000}"
LIMIT="${4:-5}"
MODE="${5:-HYBRID}"
ALPHA="${6:-0.65}"
ESCAPED_LIBRARY_ID=$(printf '%s' "$LIBRARY_ID" | sed 's/\\/\\\\/g; s/"/\\"/g')
ESCAPED_QUERY=$(printf '%s' "$QUERY" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"library_id":"${ESCAPED_LIBRARY_ID}","query":"${ESCAPED_QUERY}","tokens":${TOKENS},"limit":${LIMIT},"mode":"${MODE}","alpha":${ALPHA}}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/QueryDocs
