#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building hello-websocket-swift..."
swift build
echo "Build complete."
