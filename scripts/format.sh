#!/usr/bin/env bash
# Run code formatters for all language implementations that have one.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Hello WebSocket — Code Formatting ==="

# Python
if command -v black &>/dev/null && [ -d hello-websocket-python ]; then
    echo "[Python] black hello-websocket-python/"
    black hello-websocket-python/ 2>/dev/null || true
fi

# Go
if command -v gofmt &>/dev/null && [ -d hello-websocket-go ]; then
    echo "[Go] gofmt hello-websocket-go/"
    gofmt -w hello-websocket-go/ 2>/dev/null || true
fi

# Rust
if command -v rustfmt &>/dev/null && [ -d hello-websocket-rust ]; then
    echo "[Rust] rustfmt hello-websocket-rust/"
    rustfmt hello-websocket-rust/**/*.rs 2>/dev/null || true
fi

# Node.js / TypeScript
if [ -d hello-websocket-nodejs ] && [ -f hello-websocket-nodejs/node_modules/.bin/prettier ]; then
    echo "[Node.js] prettier hello-websocket-nodejs/"
    (cd hello-websocket-nodejs && npx prettier --write . 2>/dev/null) || true
fi
if [ -d hello-websocket-ts ] && [ -f hello-websocket-ts/node_modules/.bin/prettier ]; then
    echo "[TypeScript] prettier hello-websocket-ts/"
    (cd hello-websocket-ts && npx prettier --write . 2>/dev/null) || true
fi

# Java
if command -v mvn &>/dev/null && [ -d hello-websocket-java ]; then
    echo "[Java] mvn spotless:apply (if configured)"
    (cd hello-websocket-java && mvn spotless:apply -q 2>/dev/null) || true
fi

# Kotlin
if [ -d hello-websocket-kotlin ] && [ -f hello-websocket-kotlin/gradlew ]; then
    echo "[Kotlin] ktlintFormat (if configured)"
    (cd hello-websocket-kotlin && ./gradlew ktlintFormat -q 2>/dev/null) || true
fi

# Dart
if command -v dart &>/dev/null && [ -d hello-websocket-dart ]; then
    echo "[Dart] dart format hello-websocket-dart/"
    dart format hello-websocket-dart/ 2>/dev/null || true
fi

# PHP
if command -v php-cs-fixer &>/dev/null && [ -d hello-websocket-php ]; then
    echo "[PHP] php-cs-fixer hello-websocket-php/"
    php-cs-fixer fix hello-websocket-php/ 2>/dev/null || true
fi

echo "=== Formatting complete ==="
