#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dart pub get
mkdir -p build
dart compile exe server/ws_server.dart -o build/ws_server
dart compile exe client/ws_client.dart -o build/ws_client
echo "Build complete"
