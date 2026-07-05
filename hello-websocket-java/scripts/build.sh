#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn clean package -DskipTests -q
echo "Build complete: target/hello-websocket-java-1.0.0.jar"
