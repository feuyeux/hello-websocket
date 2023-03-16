# Hello websocket

```sh
go mod init hello-websocket
go env -w GO111MODULE=on
go mod tidy
```

## run server

```sh
go run server/echo_server.go
```

## run client

```sh
go run client/echo_client.go
```

The server includes a simple web client. To use the client, open
http://127.0.0.1:8080 in the browser and follow the instructions on the page.
