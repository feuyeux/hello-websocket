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

export DEPLOY_NAME=hello.websocket.java.server
export MAIN_CLASS=org.feuyeux.websocket.HelloServer
mvn clean package -U -Dmaven.test.skip=true

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.server-jar-with-dependencies.jar bin/hello-server.jar
