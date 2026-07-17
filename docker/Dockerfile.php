FROM composer:2 AS build-base
COPY hello-websocket-php /app/hello-websocket-php
WORKDIR /app/hello-websocket-php
RUN composer install --no-interaction --no-progress --no-scripts --no-dev --prefer-dist --classmap-authoritative

FROM php:8.5-cli AS server
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-php /app
USER app
ENTRYPOINT ["php", "/app/server/ws_server.php"]

FROM php:8.5-cli AS client
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-php /app
USER app
ENTRYPOINT ["php", "/app/client/ws_client.php"]
