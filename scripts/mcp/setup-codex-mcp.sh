#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_DIR="${ROOT_DIR}/.codex"
CONFIG_FILE="${CONFIG_DIR}/config.toml"

REMOTE_URL="${JMCP_REMOTE_MCP_URL:-http://94.16.111.94:30739/mcp}"
LOCAL_URL="${JMCP_LOCAL_MCP_URL:-http://127.0.0.1:8080/mcp}"
PRIMARY="${JMCP_PRIMARY:-prod}"           # prod|local
AUTH_MODE="${JMCP_AUTH_MODE:-header}"     # header|bearer|none
TOKEN_ENV="${JMCP_TOKEN_ENV:-JMCP_API_KEY}"
HEADER_NAME="${JMCP_HEADER_NAME:-X-API-Key}"
TOOL_TIMEOUT_SEC="${JMCP_TOOL_TIMEOUT_SEC:-60}"

mkdir -p "${CONFIG_DIR}"

remote_enabled=false
local_enabled=false
case "${PRIMARY}" in
  prod) remote_enabled=true ;;
  local) local_enabled=true ;;
  *)
    echo "Unsupported JMCP_PRIMARY='${PRIMARY}'. Use 'prod' or 'local'." >&2
    exit 1
    ;;
esac

remote_auth_block=""
case "${AUTH_MODE}" in
  header)
    remote_auth_block="env_http_headers = { \"${HEADER_NAME}\" = \"${TOKEN_ENV}\" }"
    ;;
  bearer)
    remote_auth_block="bearer_token_env_var = \"${TOKEN_ENV}\""
    ;;
  none)
    remote_auth_block=""
    ;;
  *)
    echo "Unsupported JMCP_AUTH_MODE='${AUTH_MODE}'. Use 'header', 'bearer', or 'none'." >&2
    exit 1
    ;;
esac

cat > "${CONFIG_FILE}" <<EOF
# Project-scoped Codex MCP config for JMCP.
# Export ${TOKEN_ENV} before launching Codex.

[mcp_servers.jmcp-prod]
url = "${REMOTE_URL}"
enabled = ${remote_enabled}
${remote_auth_block}
tool_timeout_sec = ${TOOL_TIMEOUT_SEC}

[mcp_servers.jmcp-local]
url = "${LOCAL_URL}"
enabled = ${local_enabled}
tool_timeout_sec = ${TOOL_TIMEOUT_SEC}
EOF

echo "Wrote ${CONFIG_FILE}"
echo
echo "Next steps:"
echo "  1. export ${TOKEN_ENV}=<your-jmcp-api-key>    # skip if JMCP_AUTH_MODE=none"
echo "  2. fully restart Codex from this repository"
echo "  3. verify in a Codex session opened from ${ROOT_DIR}"
