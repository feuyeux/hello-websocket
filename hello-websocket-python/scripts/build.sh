#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/python-env.sh
source scripts/python-env.sh
"${PYTHON}" -m pip install -r requirements-dev.txt
"${PYTHON}" -m pytest common/codec_test.py -v
echo "Build complete"
