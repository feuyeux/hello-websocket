# hello-websocket-go/go.mod declares `go 1.23`, the minimum toolchain
# version this module requires (per Go's module compatibility contract,
# https://go.dev/doc/toolchain). Building with a newer toolchain such as
# 1.26 here is expected and safe; it is not a version mismatch to "fix" by
# keeping this pinned to 1.23.
FROM golang:1.26-alpine AS build-base
RUN apk add --update git && rm -rf /var/cache/apk/*
ENV GOPROXY=https://goproxy.cn,direct
ENV GO111MODULE=on

COPY hello-websocket-go /app/hello-websocket-go
WORKDIR /app/hello-websocket-go
RUN go mod download
RUN go build -o ws_server server/main.go
RUN go build -o ws_client client/main.go

FROM alpine:3.24 AS server
RUN apk add --update ca-certificates && rm -rf /var/cache/apk/*
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-go/ws_server /app/
USER app
ENTRYPOINT ["./ws_server"]

FROM alpine:3.24 AS client
RUN apk add --update ca-certificates && rm -rf /var/cache/apk/*
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-go/ws_client /app/
USER app
ENTRYPOINT ["./ws_client"]
