#!/usr/bin/env bash
export DEPLOY_NAME=hello.websocket.java.server
export MAIN_CLASS=org.feuyeux.websocket.EchoServer
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home
mvn clean package

if [ ! -d "bin" ]; then
    mkdir bin
fi
mv target/hello.websocket.java.server-jar-with-dependencies.jar bin/server.jar
