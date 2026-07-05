#!/usr/bin/env bash
# Check library versions across all 12 language implementations.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Hello WebSocket — Version Check ==="
echo ""

# Go
if [ -f hello-websocket-go/go.mod ]; then
    echo "[Go] go.mod dependencies:"
    grep -E '^\s+require' hello-websocket-go/go.mod || true
    echo ""
fi

# Rust
if [ -f hello-websocket-rust/Cargo.toml ]; then
    echo "[Rust] Cargo.toml dependencies:"
    grep -E '^\[' hello-websocket-rust/Cargo.toml | head -5
    grep -E '^[a-z].*=' hello-websocket-rust/Cargo.toml || true
    echo ""
fi

# Python
if [ -f hello-websocket-python/requirements.txt ]; then
    echo "[Python] requirements.txt:"
    cat hello-websocket-python/requirements.txt
    echo ""
fi

# Node.js
if [ -f hello-websocket-nodejs/package.json ]; then
    echo "[Node.js] package.json dependencies:"
    node -e "const p=require('./hello-websocket-nodejs/package.json'); console.log(JSON.stringify(p.dependencies||{}, null, 2))"
    echo ""
fi

# TypeScript
if [ -f hello-websocket-ts/package.json ]; then
    echo "[TypeScript] package.json dependencies:"
    node -e "const p=require('./hello-websocket-ts/package.json'); console.log(JSON.stringify(p.dependencies||{}, null, 2))"
    echo ""
fi

# Java
if [ -f hello-websocket-java/pom.xml ]; then
    echo "[Java] pom.xml dependencies:"
    grep -oP '<artifactId>[^<]+</artifactId>' hello-websocket-java/pom.xml || true
    echo ""
fi

# Kotlin
if [ -f hello-websocket-kotlin/build.gradle.kts ]; then
    echo "[Kotlin] build.gradle.kts:"
    grep -E 'implementation|api' hello-websocket-kotlin/build.gradle.kts || true
    echo ""
fi

# C++
if [ -f hello-websocket-cpp/CMakeLists.txt ]; then
    echo "[C++] CMakeLists.txt:"
    grep -iE 'find_package|FetchContent' hello-websocket-cpp/CMakeLists.txt || true
    echo ""
fi

# C#
if [ -f hello-websocket-csharp/hello-websocket-csharp.csproj ]; then
    echo "[C#] csproj dependencies:"
    grep -E '<PackageReference' hello-websocket-csharp/hello-websocket-csharp.csproj || true
    echo ""
fi

# Dart
if [ -f hello-websocket-dart/pubspec.yaml ]; then
    echo "[Dart] pubspec.yaml dependencies:"
    grep -A20 'dependencies:' hello-websocket-dart/pubspec.yaml || true
    echo ""
fi

# PHP
if [ -f hello-websocket-php/composer.json ]; then
    echo "[PHP] composer.json dependencies:"
    grep -A20 '"require"' hello-websocket-php/composer.json || true
    echo ""
fi

# Swift
if [ -f hello-websocket-swift/Package.swift ]; then
    echo "[Swift] Package.swift dependencies:"
    grep -E '\.package\(|\.product\(' hello-websocket-swift/Package.swift || true
    echo ""
fi

echo "=== Version check complete ==="
