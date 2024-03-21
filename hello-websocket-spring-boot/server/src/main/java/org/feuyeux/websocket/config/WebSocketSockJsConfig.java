package org.feuyeux.websocket.config;

import org.feuyeux.websocket.handler.ServerTextWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketSockJsConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketSockJsHandler(), "/websocket-sockjs")
                // Set the allowed origins property to "*"
                .setAllowedOrigins("*")
                // registers a STOMP over WebSocket endpoint with SockJS fallback
                .withSockJS()
                // Set the streamBytesLimit property to 512KB (the default is 128KB — 128 * 1024)
                .setStreamBytesLimit(512 * 1024)
                // Set the httpMessageCacheSize property to 1,000 (the default is 100)
                .setHttpMessageCacheSize(1000)
                // Set the disconnectDelay property to 30,000 milliseconds (the default is 5,000)
                .setWebSocketEnabled(true)
                // Set the heartbeatTime property to 25 seconds (the default is 25,000 milliseconds)
                .setHeartbeatTime(25000)
                // Set the disconnectDelay property to 30 property seconds (the default is five seconds 5 * 1000)
                .setDisconnectDelay(30 * 1000)
                // https://github.com/webjars/sockjs-client
                .setClientLibraryUrl("/webjars/sockjs-client/1.5.1/sockjs.js")
                // Set the sessionCookieNeeded property to false (the default is true)
                .setSessionCookieNeeded(false);
    }

    @Bean
    public WebSocketHandler webSocketSockJsHandler() {
        return new ServerTextWebSocketHandler();
    }
}
