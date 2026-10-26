#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building hello-websocket-kotlin..."
gradle clean build --no-daemon
echo "Build complete."
