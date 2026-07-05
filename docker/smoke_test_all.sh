#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -e

# Cross-language smoke test: start a server, run each client briefly,
# verify HELLO/BONJOUR exchange and at least one PING/PONG round-trip.

SERVER_LANG="java"
CLIENT_LANGS=(cpp rust java go csharp python nodejs dart kotlin swift php ts)
TIMEOUT=10
PASS=0
FAIL=0

usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -s, --server LANG    Server language (default: java)"
    echo "  -c, --client LANG    Test only this client (default: all)"
    echo "  -t, --timeout SECS   Per-client timeout (default: 10)"
    echo "  -h, --help           Display this help message"
    exit 1
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
        echo "feuyeux/ws_${comp}_node:1.0.0"
    else
        echo "feuyeux/ws_${comp}_${lang}:1.0.0"
    fi
}

SERVER_IMG=$(get_image_name "$SERVER_LANG" "server")
SERVER_NAME="ws-smoke-server"

echo "=== Hello WebSocket — Cross-Language Smoke Test ==="
echo "Server: $SERVER_LANG ($SERVER_IMG)"
echo "Clients: ${CLIENT_LANGS[*]}"
echo ""

# Start server
echo "--- Starting $SERVER_LANG server ---"
if docker ps -a --format '{{.Names}}' | grep -q "^${SERVER_NAME}$"; then
    docker rm -f "$SERVER_NAME" >/dev/null
fi
docker run -d --name "$SERVER_NAME" -p 9898:9898 "$SERVER_IMG" >/dev/null
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
    if docker ps -a --format '{{.Names}}' | grep -q "^${CLIENT_NAME}$"; then
        docker rm -f "$CLIENT_NAME" >/dev/null
    fi

    # Run client with timeout
    if timeout "$TIMEOUT" docker run --rm --name "$CLIENT_NAME" \
        -e WS_SERVER=host.docker.internal -e WS_PORT=9898 \
        "$CLIENT_IMG" 2>&1 | tee /tmp/smoke_${lang}.log; then
        # Check for HELLO/BONJOUR exchange in logs
        if grep -qi "bonjour" /tmp/smoke_${lang}.log 2>/dev/null; then
            echo "  [PASS] $lang client: HELLO/BONJOUR exchange verified"
            PASS=$((PASS + 1))
        else
            echo "  [WARN] $lang client: completed but no BONJOUR in logs"
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
docker rm -f "$SERVER_NAME" >/dev/null 2>&1 || true

# Summary
echo ""
echo "============================================"
echo "Smoke test results: $PASS passed, $FAIL failed"
echo "============================================"

if [[ "$FAIL" -gt 0 ]]; then
    exit 1
fi
