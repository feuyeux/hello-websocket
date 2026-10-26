#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec node client/ws_client.js
