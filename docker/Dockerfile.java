FROM maven:3.9-eclipse-temurin-21 AS build-base
COPY docker/settings.xml /root/.m2/settings.xml
WORKDIR /app
COPY hello-websocket-java/pom.xml /app/pom.xml
COPY hello-websocket-java/common /app/common
COPY hello-websocket-java/server /app/server
COPY hello-websocket-java/client /app/client
RUN mvn --batch-mode clean package
RUN cp target/hello-websocket-java-1.0.0.jar /app/hello-ws.jar

FROM eclipse-temurin:21-jre-alpine AS server
WORKDIR /app
COPY --from=build-base /app/hello-ws.jar /app/
ENTRYPOINT ["java", "-jar", "hello-ws.jar"]

FROM eclipse-temurin:21-jre-alpine AS client
WORKDIR /app
COPY --from=build-base /app/hello-ws.jar /app/
ENTRYPOINT ["java", "-cp", "hello-ws.jar", "io.github.hellowebsocket.WsClient"]
