#!/bin/bash
cd "$(
    cd "$(dirname "$0")" >/dev/null 2>&1
    pwd -P
)/" || exit
set -e

# shellcheck disable=SC2155
export VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{sub("^$", "0", $2); print $1$2}')
if [ "$VERSION" -lt 21 ]; then
    echo "JDK version is less than 21, please check JAVA_HOME"
    exit 1
fi

export DEPLOY_NAME=hello.websocket.java.server
export MAIN_CLASS=org.feuyeux.websocket.HelloServer
mvn clean package -U -Dmaven.test.skip=true

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.server-jar-with-dependencies.jar bin/hello-server.jar
