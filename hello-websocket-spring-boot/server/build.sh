#!/usr/bin/env bash

mvn -v
echo "======================"
mvn clean package -DskipTests
