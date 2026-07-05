#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec npx ts-node --esm client/ws_client.ts
