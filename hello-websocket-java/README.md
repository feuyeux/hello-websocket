# Hello WebSocket - Java Implementation

Java implementation of the Hello WebSocket protocol using [Java-WebSocket](https://github.com/TakahikoKawasaki/nv-websocket-client) and [Gson](https://github.com/google/gson).

## Project Structure

```
hello-websocket-java/
├── pom.xml                 # Maven build configuration
├── common/
│   ├── src/main/java/io/github/hellowebsocket/Codec.java
│   └── src/test/java/io/github/hellowebsocket/CodecTest.java
├── server/
│   └── src/main/java/io/github/hellowebsocket/WsServer.java
├── client/
│   └── src/main/java/io/github/hellowebsocket/WsClient.java
├── scripts/
│   ├── build.sh            # Build with Maven
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **JDK** 21+
- **Maven** 3.9+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
mvn clean package -DskipTests
```

## Run Server

```bash
./scripts/run-server.sh
```

Environment variables:
- `WS_SERVER_PORT` — Port to listen on (default: 9898)

## Run Client

```bash
./scripts/run-client.sh
```

Environment variables:
- `WS_SERVER_HOST` — Server host (default: 127.0.0.1)
- `WS_SERVER_PORT` — Server port (default: 9898)

## Protocol

See [../PROTOCOL.md](../PROTOCOL.md) for the canonical protocol specification.
