#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"
LIBRARY_ID="${1:-/spring-projects/spring-security}"
TOPIC="${2:-csrf}"
TOKENS="${3:-5000}"
LIMIT="${4:-5}"
MODE="${5:-HYBRID}"

curl -s --get "${BASE_URL}/api/tools/get-library-docs" \
  --data-urlencode "libraryId=${LIBRARY_ID}" \
  --data-urlencode "topic=${TOPIC}" \
  --data-urlencode "tokens=${TOKENS}" \
  --data-urlencode "limit=${LIMIT}" \
  --data-urlencode "mode=${MODE}"
