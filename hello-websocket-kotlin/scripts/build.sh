#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building hello-websocket-kotlin..."
./gradlew clean build -x test
echo "Build complete."
