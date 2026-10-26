#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec java -cp target/hello-websocket-java-1.0.0.jar io.github.hellowebsocket.WsClient
