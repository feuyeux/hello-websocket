package org.feuyeux.websocket.config;

import org.feuyeux.websocket.handler.ServerBinaryWebSocketHandler;
import org.feuyeux.websocket.handler.ServerTextWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // set the timeout to 2min
        container.setMaxSessionIdleTimeout(120000L);
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(8192);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ServerTextWebSocketHandler(), "/websocket/text");
        registry.addHandler(new ServerBinaryWebSocketHandler(), "/websocket/binary");
    }

}