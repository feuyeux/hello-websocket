#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -e

# shellcheck disable=SC2155
OS=$(uname -s)
if [ "$OS" = "Linux" ] || [ "$OS" = "Darwin" ]; then
    echo "TODO"
elif [[ "$OS" == MINGW*_NT* ]]; then
    export JAVA_HOME="/d/zoo/jdk-21.0.3"
else
    echo "Unsupported operating system: $OS"
    exit 1
fi

export DEPLOY_NAME=hello.websocket.java.client
export MAIN_CLASS=org.feuyeux.websocket.HelloClient
mvn clean package

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.client-jar-with-dependencies.jar bin/hello-client.jar
