#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"

curl -s "${BASE_URL}/actuator/prometheus"
