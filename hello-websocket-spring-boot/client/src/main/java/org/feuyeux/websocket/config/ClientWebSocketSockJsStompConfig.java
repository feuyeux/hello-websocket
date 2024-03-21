package org.feuyeux.websocket.config;

import java.util.ArrayList;
import java.util.List;
import org.feuyeux.websocket.handler.ClientStompSessionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Configuration
public class ClientWebSocketSockJsStompConfig {
  @Bean
  public WebSocketStompClient webSocketStompClient(
      @Qualifier("stompSessionClient") WebSocketClient stompSessionClient,
      StompSessionHandler stompSessionHandler) {
    WebSocketStompClient webSocketStompClient = new WebSocketStompClient(stompSessionClient);
    webSocketStompClient.setMessageConverter(new StringMessageConverter());
    webSocketStompClient.connectAsync(
        "http://localhost:8080/websocket-sockjs-stomp", stompSessionHandler);
    return webSocketStompClient;
  }

  @Bean
  public WebSocketClient stompSessionClient() {
    List<Transport> transports = new ArrayList<>();
    transports.add(new WebSocketTransport(new StandardWebSocketClient()));
    transports.add(new RestTemplateXhrTransport());
    return new SockJsClient(transports);
  }

  @Bean
  public StompSessionHandler stompSessionHandler() {
    return new ClientStompSessionHandler();
  }
}
