package org.feuyeux.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerWsApplication {
  public static void main(String[] args) {
    SpringApplication.run(ServerWsApplication.class, args);
  }
}
