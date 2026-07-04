#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec node server/ws_server.js
