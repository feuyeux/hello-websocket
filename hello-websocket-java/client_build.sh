#!/usr/bin/env bash
export DEPLOY_NAME=hello.websocket.java.client
export MAIN_CLASS=org.feuyeux.websocket.EchoClient
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home
mvn clean package

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.client-jar-with-dependencies.jar bin/client.jar
