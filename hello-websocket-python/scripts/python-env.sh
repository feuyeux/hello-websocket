#!/usr/bin/env bash
# Resolves the Python interpreter used by this implementation's scripts.
#
# This file is sourced, not executed. It sets PYTHON so that dependency
# installation and the application entry points always use the same
# interpreter, which prevents requirements from being installed into one Python
# while the code runs under another.
#
# Most Linux distributions (including the images used by docker/Dockerfile.python)
# ship `python3` without providing a `python` alias, so invoking `python`
# directly makes these scripts fail with "command not found".
#
# Override the interpreter explicitly when needed, for example a virtualenv:
#   PYTHON=.venv/bin/python ./scripts/run-server.sh

if [ -z "${PYTHON:-}" ]; then
    if command -v python3 > /dev/null 2>&1; then
        PYTHON=python3
    elif command -v python > /dev/null 2>&1; then
        PYTHON=python
    else
        echo "python3 (or python) is required but was not found" >&2
        exit 1
    fi
fi

export PYTHON
