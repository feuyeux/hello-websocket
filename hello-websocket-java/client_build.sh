#!/usr/bin/env bash
set -e
export DEPLOY_NAME=hello.websocket.java.client
export MAIN_CLASS=org.feuyeux.websocket.HelloClient
mvn clean package

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.client-jar-with-dependencies.jar bin/client.jar
