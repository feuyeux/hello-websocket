package org.feuyeux.websocket;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ClientWsApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(ClientWsApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
