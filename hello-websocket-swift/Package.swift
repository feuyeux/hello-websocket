// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "hello-websocket-swift",
    products: [
        .executable(name: "ws-server", targets: ["HelloWebSocketServer"]),
        .executable(name: "ws-client", targets: ["HelloWebSocketClient"]),
    ],
    targets: [
        .target(name: "HelloWebSocket", path: "common"),
        .executableTarget(name: "HelloWebSocketServer", dependencies: ["HelloWebSocket"], path: "server"),
        .executableTarget(name: "HelloWebSocketClient", dependencies: ["HelloWebSocket"], path: "client"),
        .testTarget(name: "HelloWebSocketTests", dependencies: ["HelloWebSocket"]),
    ]
)
