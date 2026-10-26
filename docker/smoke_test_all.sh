#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -euo pipefail

SCRIPT_DIR="$(pwd -P)"
# shellcheck source=container_runtime.sh
source "$SCRIPT_DIR/container_runtime.sh"

# Cross-language smoke test: start a server, run each client briefly,
# verify HELLO/BONJOUR exchange and at least one PING/PONG round-trip.

SERVER_LANG="java"
CLIENT_LANGS=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
TIMEOUT=10
PASS=0
FAIL=0
LOG_DIR=$(mktemp -d)

usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -s, --server LANG    Server language (default: java)"
    echo "  -c, --client LANG    Test only this client (default: all)"
    echo "  -t, --timeout SECS   Per-client timeout (default: 10)"
    echo "  -h, --help           Display this help message"
    exit 1
}

run_with_timeout() {
    local duration="$1"
    shift

    if command -v timeout >/dev/null 2>&1; then
        timeout "$duration" "$@"
        return
    fi
    if command -v gtimeout >/dev/null 2>&1; then
        gtimeout "$duration" "$@"
        return
    fi

    "$@" &
    local pid=$!
    local elapsed=0
    while kill -0 "$pid" >/dev/null 2>&1; do
        if [[ "$elapsed" -ge "$duration" ]]; then
            kill "$pid" >/dev/null 2>&1 || true
            wait "$pid" >/dev/null 2>&1 || true
            return 124
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    wait "$pid"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
    -s | --server) SERVER_LANG="$2"; shift 2 ;;
    -c | --client) CLIENT_LANGS=("$2"); shift 2 ;;
    -t | --timeout) TIMEOUT="$2"; shift 2 ;;
    -h | --help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
    esac
done

# Function to get image name
get_image_name() {
    local lang="$1"
    local comp="$2"
    if [[ "$lang" == "nodejs" ]]; then
        echo "feuyeux/ws_${comp}_node:${IMAGE_TAG:-1.0.0}"
    else
        echo "feuyeux/ws_${comp}_${lang}:${IMAGE_TAG:-1.0.0}"
    fi
}

SERVER_IMG=$(get_image_name "$SERVER_LANG" "server")
SERVER_NAME="ws-smoke-server"
cleanup() {
    ws_container_remove "$SERVER_NAME" >/dev/null 2>&1 || true
    rm -rf "$LOG_DIR"
}
trap cleanup EXIT

ws_container_runtime_init
SERVER_HOST="host.docker.internal"
if [[ "$WS_CONTAINER_RUNTIME" == "container" ]]; then
    ws_container_require_host_domain
    SERVER_HOST="host.container.internal"
fi

echo "=== Hello WebSocket — Cross-Language Smoke Test ==="
echo "Server: $SERVER_LANG ($SERVER_IMG)"
echo "Clients: ${CLIENT_LANGS[*]}"
echo ""

# Start server
echo "--- Starting $SERVER_LANG server ---"
ws_container_remove "$SERVER_NAME" >/dev/null 2>&1 || true
ws_container_run -d --name "$SERVER_NAME" -p 9898:9898 "$SERVER_IMG" >/dev/null
echo "Server started. Waiting for it to be ready..."
sleep 3

# Test each client
for lang in "${CLIENT_LANGS[@]}"; do
    # Skip testing client against same-language server to save time? No, test all.
    CLIENT_IMG=$(get_image_name "$lang" "client")
    CLIENT_NAME="ws-smoke-client-$lang"

    echo ""
    echo "--- Testing $lang client → $SERVER_LANG server ---"

    # Remove any existing container
    ws_container_remove "$CLIENT_NAME" >/dev/null 2>&1 || true

    server_log_lines=$(ws_container_logs "$SERVER_NAME" 2>&1 | wc -l | tr -d ' ')
    set +e
    run_with_timeout "$TIMEOUT" "$WS_CONTAINER_RUNTIME" run --rm --name "$CLIENT_NAME" \
        -e WS_SERVER="$SERVER_HOST" -e WS_PORT=9898 \
        "$CLIENT_IMG" 2>&1 | tee "$LOG_DIR/${lang}.log"
    client_status=${PIPESTATUS[0]}
    set -e
    ws_container_logs "$SERVER_NAME" 2>&1 | tail -n "+$((server_log_lines + 1))" >"$LOG_DIR/${lang}-server.log"
    if [[ "$client_status" -eq 0 || "$client_status" -eq 124 ]]; then
        if grep -qi "bonjour" "$LOG_DIR/${lang}.log" && grep -qi "pong" "$LOG_DIR/${lang}-server.log"; then
            echo "  [PASS] $lang client: HELLO/BONJOUR and PING/PONG verified"
            PASS=$((PASS + 1))
        else
            echo "  [FAIL] $lang client: required protocol exchanges missing"
            FAIL=$((FAIL + 1))
        fi
    else
        echo "  [FAIL] $lang client: timed out or crashed"
        FAIL=$((FAIL + 1))
    fi
done

# Cleanup
echo ""
echo "--- Cleaning up ---"
ws_container_remove "$SERVER_NAME" >/dev/null 2>&1 || true

# Summary
echo ""
echo "============================================"
echo "Smoke test results: $PASS passed, $FAIL failed"
echo "============================================"

if [[ "$FAIL" -gt 0 ]]; then
    exit 1
fi
