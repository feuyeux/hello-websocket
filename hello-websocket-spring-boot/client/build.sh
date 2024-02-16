#!/usr/bin/env bash

#export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home
mvn -v
echo "======================"
mvn clean package -DskipTests
