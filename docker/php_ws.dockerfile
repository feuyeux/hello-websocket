FROM composer:2 AS build-base
COPY hello-websocket-php /app/hello-websocket-php
WORKDIR /app/hello-websocket-php
# Force fresh dependency resolution. The committed vendor/ may be stale and
# reference a different commit than the lockfile claims, breaking the build.
RUN rm -rf vendor composer.lock && \
    composer update --no-interaction --no-progress --no-scripts --ignore-platform-reqs --prefer-stable cboden/ratchet ratchet/pawl react/event-loop react/socket && \
    composer install --no-interaction --no-progress --no-scripts --ignore-platform-reqs
RUN sed -i 's|new ClientNegotiator(new gPsr\HttpFactory())|new ClientNegotiator(null)|' vendor/ratchet/pawl/src/Connector.php

FROM php:8.3-cli AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-php /app
ENTRYPOINT ["php", "/app/server/ws_server.php"]

FROM php:8.3-cli AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-php /app
ENTRYPOINT ["php", "/app/client/ws_client.php"]
