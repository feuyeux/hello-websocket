FROM dart:3.12.2 AS build-base
ENV PUB_HOSTED_URL=https://pub.flutter-io.cn
COPY hello-websocket-dart /app/hello-websocket-dart
WORKDIR /app/hello-websocket-dart
RUN dart pub get
RUN dart compile exe -o ws_server server/ws_server.dart
RUN dart compile exe -o ws_client client/ws_client.dart

FROM debian:bookworm-slim AS server
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-dart/ws_server /app/
USER app
ENTRYPOINT ["./ws_server"]

FROM debian:bookworm-slim AS client
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-dart/ws_client /app/
USER app
ENTRYPOINT ["./ws_client"]
