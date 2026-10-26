# hello-websocket-java/pom.xml sets maven.compiler.source/target=17, the
# bytecode target this module compiles to. Building and running on a newer
# JDK (25 here) is standard Java forward compatibility (javac --release 17
# semantics) and is not a version mismatch to "fix" by pinning this image
# to JDK 17.
FROM maven:3-eclipse-temurin-25 AS build-base
COPY docker/settings.xml /root/.m2/settings.xml
WORKDIR /app
COPY hello-websocket-java/pom.xml /app/pom.xml
COPY hello-websocket-java/common /app/common
COPY hello-websocket-java/server /app/server
COPY hello-websocket-java/client /app/client
RUN mvn --batch-mode clean package
RUN cp target/hello-websocket-java-1.0.0.jar /app/hello-ws.jar

FROM eclipse-temurin:25-jre-alpine AS server
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-ws.jar /app/
USER app
ENTRYPOINT ["java", "-jar", "hello-ws.jar"]

FROM eclipse-temurin:25-jre-alpine AS client
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-ws.jar /app/
USER app
ENTRYPOINT ["java", "-cp", "hello-ws.jar", "io.github.hellowebsocket.WsClient"]
