FROM composer:2 AS build-base
COPY hello-websocket-php /app/hello-websocket-php
WORKDIR /app/hello-websocket-php
RUN composer install --no-interaction --no-progress --no-scripts --no-dev --prefer-dist --classmap-authoritative

FROM php:8.3-cli AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-php /app
ENTRYPOINT ["php", "/app/server/ws_server.php"]

FROM php:8.3-cli AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-php /app
ENTRYPOINT ["php", "/app/client/ws_client.php"]
