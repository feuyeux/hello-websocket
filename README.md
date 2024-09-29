<!-- markdownlint-disable MD033 MD045 -->

# Hello Websocket

## :coffee: Develop

### protocol

#### Upstream

REQEUST

```json
{
  "data": "请求数据",
  "meta": "客户端语言"
}
```

RESPONSE

```json
{
  "status": 200,
  "results": [
    {
      "id": 1234567890,
      "type": "OK",
      "kv": {
        "id": "uuid",
        "idx": 1,
        "data": "响应数据",
        "meta": "服务器端语言"
      }
    },
    {
      "id": 1234567891,
      "type": "FAIL",
      "kv": {
        "id": "uuid",
        "idx": 2,
        "data": "响应数据",
        "meta": "服务器端语言"
      }
    }
  ]
}
```

#### Downstream

REQEUST

```json
{
  "data": "请求数据",
  "meta": "客户端语言"
}
```

RESPONSE

```json
{
  "status": 200,
  "results": [
    {
      "id": 1234567890,
      "type": "OK",
      "kv": {
        "id": "uuid",
        "idx": 1,
        "data": "响应数据",
        "meta": "服务器端语言"
      }
    },
    {
      "id": 1234567891,
      "type": "FAIL",
      "kv": {
        "id": "uuid",
        "idx": 2,
        "data": "响应数据",
        "meta": "服务器端语言"
      }
    }
  ]
}
```

### languanges

1. [hello-websocket-java](hello-websocket-java)
1. [hello-websocket-go](hello-websocket-go)
1. [hello-websocket-rust](hello-websocket-rust)
1. [hello-websocket-python](hello-websocket-python)
1. [hello-websocket-nodejs](hello-websocket-nodejs)

### features

- pingpong
- tls
- handshake
- payload

## :coffee: Build & Ship

- [docker](docker)
  
## :coffee: Recommend

<https://github.com/facundofarias/awesome-websockets>

## :coffee: Stars

[![Star History Chart](https://api.star-history.com/svg?repos=feuyeux/hello-grpc&type=Date)](https://star-history.com/#feuyeux/hello-grpc&Date)
