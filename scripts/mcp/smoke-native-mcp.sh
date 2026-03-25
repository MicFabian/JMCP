#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${JMCP_MCP_URL:-http://127.0.0.1:8080/mcp}"
LOOPS="${JMCP_SMOKE_LOOPS:-3}"
INIT_RETRIES="${JMCP_SMOKE_INIT_RETRIES:-20}"
ACCEPT_HEADER='application/json, text/event-stream'

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

request() {
  local session_id="$1"
  local payload="$2"
  local headers_file="$3"
  local body_file="$4"

  local -a curl_args=(
    -sS
    -D "${headers_file}"
    -o "${body_file}"
    -H "Content-Type: application/json"
    -H "Accept: ${ACCEPT_HEADER}"
    -X POST
    "${BASE_URL}"
    --data "${payload}"
  )

  if [[ -n "${session_id}" ]]; then
    curl_args+=(-H "Mcp-Session-Id: ${session_id}")
  fi

  curl "${curl_args[@]}"
}

status_code_from_headers() {
  awk 'NR==1 {print $2}' "$1"
}

session_id_from_headers() {
  tr -d '\r' < "$1" | sed -n 's/^Mcp-Session-Id: //p' | head -n1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq "${expected}" "${file}"; then
    echo "Assertion failed. Expected '${expected}' in response."
    echo "--- Response body ---"
    cat "${file}"
    exit 1
  fi
}

for i in $(seq 1 "${LOOPS}"); do
  echo "[loop ${i}/${LOOPS}] initialize"
  init_headers="${tmp_dir}/init-${i}.headers"
  init_body="${tmp_dir}/init-${i}.body"
  session_id=""
  for attempt in $(seq 1 "${INIT_RETRIES}"); do
    if request "" '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-script","version":"1.0"}}}' "${init_headers}" "${init_body}" 2>/dev/null; then
      if [[ "$(status_code_from_headers "${init_headers}")" == "200" ]]; then
        session_id="$(session_id_from_headers "${init_headers}")"
        if [[ -n "${session_id}" ]]; then
          break
        fi
      fi
    fi
    if [[ "${attempt}" -lt "${INIT_RETRIES}" ]]; then
      sleep 1
    fi
  done

  [[ -n "${session_id}" ]] || {
    echo "Initialize failed after ${INIT_RETRIES} attempts"
    cat "${init_headers}" 2>/dev/null || true
    cat "${init_body}" 2>/dev/null || true
    exit 1
  }
  assert_contains "${init_body}" '"protocolVersion":"2025-06-18"'

  echo "[loop ${i}/${LOOPS}] notifications/initialized"
  notification_headers="${tmp_dir}/notification-${i}.headers"
  notification_body="${tmp_dir}/notification-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}' "${notification_headers}" "${notification_body}"
  notification_code="$(status_code_from_headers "${notification_headers}")"
  [[ "${notification_code}" == "200" || "${notification_code}" == "202" || "${notification_code}" == "204" ]] || {
    echo "Initialized notification failed with status ${notification_code}"
    cat "${notification_body}"
    exit 1
  }

  echo "[loop ${i}/${LOOPS}] tools/list"
  tools_headers="${tmp_dir}/tools-${i}.headers"
  tools_body="${tmp_dir}/tools-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}' "${tools_headers}" "${tools_body}"
  [[ "$(status_code_from_headers "${tools_headers}")" == "200" ]] || {
    echo "tools/list failed"
    cat "${tools_body}"
    exit 1
  }
  assert_contains "${tools_body}" 'resolve-library-id'
  assert_contains "${tools_body}" 'query-docs'
  assert_contains "${tools_body}" 'search'

  echo "[loop ${i}/${LOOPS}] resources/list + prompts/list"
  resources_headers="${tmp_dir}/resources-${i}.headers"
  resources_body="${tmp_dir}/resources-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","id":"3","method":"resources/list","params":{}}' "${resources_headers}" "${resources_body}"
  [[ "$(status_code_from_headers "${resources_headers}")" == "200" ]] || {
    echo "resources/list failed"
    cat "${resources_body}"
    exit 1
  }
  assert_contains "${resources_body}" 'mcp://docs/spring-boot-csrf'

  prompts_headers="${tmp_dir}/prompts-${i}.headers"
  prompts_body="${tmp_dir}/prompts-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","id":"4","method":"prompts/list","params":{}}' "${prompts_headers}" "${prompts_body}"
  [[ "$(status_code_from_headers "${prompts_headers}")" == "200" ]] || {
    echo "prompts/list failed"
    cat "${prompts_body}"
    exit 1
  }
  assert_contains "${prompts_body}" 'resolve-then-query'

  echo "[loop ${i}/${LOOPS}] tools/call search"
  search_headers="${tmp_dir}/search-${i}.headers"
  search_body="${tmp_dir}/search-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","id":"5","method":"tools/call","params":{"name":"search","arguments":{"q":"csrf","limit":3}}}' "${search_headers}" "${search_body}"
  [[ "$(status_code_from_headers "${search_headers}")" == "200" ]] || {
    echo "tools/call(search) failed"
    cat "${search_body}"
    exit 1
  }
  assert_contains "${search_body}" '"isError":false'
  assert_contains "${search_body}" 'spring-boot-csrf'

  echo "[loop ${i}/${LOOPS}] tools/call analyze (negative)"
  analyze_headers="${tmp_dir}/analyze-${i}.headers"
  analyze_body="${tmp_dir}/analyze-${i}.body"
  request "${session_id}" '{"jsonrpc":"2.0","id":"6","method":"tools/call","params":{"name":"analyze","arguments":{}}}' "${analyze_headers}" "${analyze_body}"
  [[ "$(status_code_from_headers "${analyze_headers}")" == "200" ]] || {
    echo "tools/call(analyze invalid) failed"
    cat "${analyze_body}"
    exit 1
  }
  assert_contains "${analyze_body}" '"isError":true'
  assert_contains "${analyze_body}" "'code' is required"
done

echo "Native MCP smoke test passed (${LOOPS} loops): ${BASE_URL}"
