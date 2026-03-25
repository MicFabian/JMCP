#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"
QUERY="${1:-constructor injection}"
LIMIT="${2:-5}"
MODE="${3:-HYBRID}"
ESCAPED_QUERY=$(printf '%s' "$QUERY" | sed 's/\\/\\\\/g; s/"/\\"/g')

PAYLOAD=$(cat <<JSON
{"query":"${ESCAPED_QUERY}","limit":${LIMIT},"mode":"${MODE}","diagnostics":true}
JSON
)

docker run --rm fullstorydev/grpcurl -plaintext -d "$PAYLOAD" "$TARGET" mcp.v1.McpService/Search
