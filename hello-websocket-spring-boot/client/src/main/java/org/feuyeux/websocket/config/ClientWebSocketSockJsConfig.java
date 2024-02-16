package org.feuyeux.websocket.config;

import org.feuyeux.websocket.handler.ClientWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.client.Transport;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ClientWebSocketSockJsConfig {
    @Bean
    public WebSocketConnectionManager webSocketSockJsConnectionManager() {
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                webSocketSockJsClient(),
                webSocketSockJsHandler(),
                "http://localhost:8080/websocket-sockjs"
        );
        manager.setAutoStartup(true);
        return manager;
    }

    @Bean
    public WebSocketClient webSocketSockJsClient() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        transports.add(new RestTemplateXhrTransport());
        return new SockJsClient(transports);
    }

    @Bean
    public WebSocketHandler webSocketSockJsHandler() {
        return new ClientWebSocketHandler("websocket-sockjs");
    }
}
