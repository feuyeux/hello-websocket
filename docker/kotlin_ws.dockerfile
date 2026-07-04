FROM gradle:8.13-jdk21 AS build-base
COPY hello-websocket-kotlin /app/hello-websocket-kotlin
WORKDIR /app/hello-websocket-kotlin
RUN gradle :server:installDist :client:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-kotlin/server/build/install/server /app/
ENTRYPOINT ["/app/bin/server"]

FROM eclipse-temurin:21-jre-alpine AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-kotlin/client/build/install/client /app/
ENTRYPOINT ["/app/bin/client"]
