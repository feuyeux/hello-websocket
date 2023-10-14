#!/usr/bin/env bash
set -e
export DEPLOY_NAME=hello.websocket.java.server
export MAIN_CLASS=org.feuyeux.websocket.HelloServer
mvn clean package

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.server-jar-with-dependencies.jar bin/hello-server.jar
