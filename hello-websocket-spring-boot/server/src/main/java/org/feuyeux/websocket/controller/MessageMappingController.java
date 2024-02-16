package org.feuyeux.websocket.controller;

import org.feuyeux.websocket.handler.ServerWebSocketHandler;
import org.feuyeux.websocket.pojo.SocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Controller
@EnableScheduling
public class MessageMappingController {
    private static final Logger logger = LoggerFactory.getLogger(MessageMappingController.class);
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @MessageMapping("/send")
    @SendTo("/queue/responses")
    public SocketMessage send(SocketMessage message) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        message.date = df.format(new Date());
        message.message = ">>" + message.message;
        return message;
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    @SendTo("/topic/callback")
    public void callback() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        messagingTemplate.convertAndSend("/topic/callback", df.format(new Date()));
    }

    @MessageExceptionHandler
    @SendTo("/queue/errors")
    public String handleException(Throwable exception) {
        logger.error("Server exception", exception);
        return "server exception: " + exception.getMessage();
    }

    @SubscribeMapping("/subscribe")
    public String sendOneTimeMessage() {
        logger.info("Subscription via the application");
        return "server one-time message via the application";
    }
}