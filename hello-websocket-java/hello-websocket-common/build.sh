#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -e

# shellcheck disable=SC2155
export VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{sub("^$", "0", $2); print $1$2}')
if [ "$VERSION" -lt 21 ]; then
    OS=$(uname -s)
    if [ "$OS" = "Linux" ] || [ "$OS" = "Darwin" ]; then
        echo "TODO"
    elif [[ "$OS" == MINGW*_NT* ]]; then
        export JAVA_HOME="/d/zoo/jdk-21.0.3"
    else
        echo "Unsupported operating system: $OS"
        exit 1
    fi
fi
mvn clean install
