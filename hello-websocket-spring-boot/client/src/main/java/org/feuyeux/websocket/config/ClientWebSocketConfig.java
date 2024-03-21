package org.feuyeux.websocket.config;

import org.feuyeux.websocket.handler.ClientBinaryWebSocketHandler;
import org.feuyeux.websocket.handler.ClientTextWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class ClientWebSocketConfig {

    @Bean
    public WebSocketConnectionManager textWebSocketConnectionManager() {
        ClientTextWebSocketHandler clientTextWebSocketHandler = new ClientTextWebSocketHandler();
        clientTextWebSocketHandler.setType("websocket-text");
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                clientTextWebSocketHandler,
                "ws://localhost:8080/websocket/text"
        );
        manager.setAutoStartup(true);
        return manager;
    }

    @Bean
    public WebSocketConnectionManager binaryWebSocketConnectionManager() {
        ClientBinaryWebSocketHandler clientBinaryWebSocketHandler = new ClientBinaryWebSocketHandler();
        clientBinaryWebSocketHandler.setType("websocket-binary");
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                clientBinaryWebSocketHandler,
                "ws://localhost:8080/websocket/binary"
        );
        manager.setAutoStartup(true);
        return manager;
    }
}