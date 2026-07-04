#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec python server/ws_server.py
