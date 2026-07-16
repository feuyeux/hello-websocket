FROM node:24-alpine AS build-base
COPY hello-websocket-ts /app/hello-websocket-ts
WORKDIR /app/hello-websocket-ts
RUN npm ci

FROM node:24-alpine AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-ts/package*.json /app/
COPY --from=build-base /app/hello-websocket-ts/node_modules /app/node_modules
COPY --from=build-base /app/hello-websocket-ts/server /app/server
COPY --from=build-base /app/hello-websocket-ts/client /app/client
COPY --from=build-base /app/hello-websocket-ts/common /app/common
COPY --from=build-base /app/hello-websocket-ts/tsconfig.json /app/
ENTRYPOINT ["node", "--loader", "ts-node/esm", "server/ws_server.ts"]

FROM node:24-alpine AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-ts/package*.json /app/
COPY --from=build-base /app/hello-websocket-ts/node_modules /app/node_modules
COPY --from=build-base /app/hello-websocket-ts/server /app/server
COPY --from=build-base /app/hello-websocket-ts/client /app/client
COPY --from=build-base /app/hello-websocket-ts/common /app/common
COPY --from=build-base /app/hello-websocket-ts/tsconfig.json /app/
ENTRYPOINT ["node", "--loader", "ts-node/esm", "client/ws_client.ts"]
