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
WORKDIR /app
COPY --from=build-base /app/hello-websocket-go/ws_server /app/
ENTRYPOINT ["./ws_server"]

FROM alpine:3.24 AS client
RUN apk add --update ca-certificates && rm -rf /var/cache/apk/*
WORKDIR /app
COPY --from=build-base /app/hello-websocket-go/ws_client /app/
ENTRYPOINT ["./ws_client"]
