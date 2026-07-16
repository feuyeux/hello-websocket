#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dotnet restore test/hello-websocket-csharp-test.csproj --locked-mode
dotnet build -c Release --no-restore
dotnet test test/hello-websocket-csharp-test.csproj -c Release --no-restore
echo "Build complete"
