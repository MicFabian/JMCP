#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080}"

echo "MANIFEST:"
curl -s "${BASE_URL}/api/mcp/manifest"
echo
echo "TOOLS:"
curl -s "${BASE_URL}/api/mcp/tools"
echo
echo "TOOL RULES:"
curl -s "${BASE_URL}/api/mcp/tool-rules"
echo
echo "RESOURCES:"
curl -s "${BASE_URL}/api/mcp/resources"
echo
echo "PROMPTS:"
curl -s "${BASE_URL}/api/mcp/prompts"
echo
