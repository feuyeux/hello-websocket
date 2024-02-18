#!/usr/bin/env bash

java -Xmx200m -Xms200m \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt \
    -XX:+EnableDynamicAgentLoading \
    -jar target/hello-websocket-server-0.0.1-SNAPSHOT.jar
