#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-jmcp-local}"
MCP_URL="${JMCP_MCP_URL:-http://127.0.0.1:8080/mcp}"
BEARER_TOKEN_ENV="${JMCP_BEARER_TOKEN_ENV:-}"

codex mcp remove "${NAME}" >/dev/null 2>&1 || true
if [[ -n "${BEARER_TOKEN_ENV}" ]]; then
  codex mcp add "${NAME}" --url "${MCP_URL}" --bearer-token-env-var "${BEARER_TOKEN_ENV}"
else
  codex mcp add "${NAME}" --url "${MCP_URL}"
fi

codex mcp get "${NAME}"
