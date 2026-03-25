#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"
LIBRARY_NAME="${1:-spring security}"
TOPIC="${2:-csrf}"
LIMIT="${3:-5}"

curl -s --get "${BASE_URL}/api/tools/resolve-library-id" \
  --data-urlencode "libraryName=${LIBRARY_NAME}" \
  --data-urlencode "topic=${TOPIC}" \
  --data-urlencode "limit=${LIMIT}"
