#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec java -jar target/hello-websocket-java-1.0.0.jar
