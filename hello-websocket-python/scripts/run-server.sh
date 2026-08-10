#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/python-env.sh
source scripts/python-env.sh
exec "${PYTHON}" server/ws_server.py
