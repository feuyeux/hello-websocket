FROM gradle:8.14-jdk21 AS build-base
COPY docker/gradle-init.d/aliyun.gradle /root/.gradle/init.d/aliyun.gradle
COPY hello-websocket-kotlin /app/hello-websocket-kotlin
WORKDIR /app/hello-websocket-kotlin
RUN gradle :server:jar :client:jar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-kotlin/server/build/libs/server.jar /app/hello-ws.jar
ENTRYPOINT ["java", "-jar", "hello-ws.jar"]

FROM eclipse-temurin:21-jre-alpine AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-kotlin/client/build/libs/client.jar /app/hello-ws.jar
ENTRYPOINT ["java", "-cp", "hello-ws.jar", "org.feuyeux.ws.client.WsClientKt"]
