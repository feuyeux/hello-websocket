#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec node --loader ts-node/esm server/ws_server.ts
