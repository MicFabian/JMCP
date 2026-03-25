#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"
QUERY="${1:-constructor}"

curl -s "${BASE_URL}/api/search?q=${QUERY}&diagnostics=true"
