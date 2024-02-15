package org.feuyeux.websocket.config;

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
      container.setMaxSessionIdleTimeout(120000L); // set the timeout to 2min

      // ...
      return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

    }
  }