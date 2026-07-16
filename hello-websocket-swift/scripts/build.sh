#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building hello-websocket-swift..."
swift build
swift test
echo "Build complete."
