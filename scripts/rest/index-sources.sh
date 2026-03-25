#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${JMCP_BASE_URL:-http://127.0.0.1:8080}"
curl -sS "${BASE_URL}/api/index/sources"
