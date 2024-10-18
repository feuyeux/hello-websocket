package org.feuyeux.websocket.controller;

import jakarta.annotation.PreDestroy;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@EnableScheduling
public class MessageMappingController {
  private static final Logger logger = LoggerFactory.getLogger(MessageMappingController.class);
  @Autowired private SimpMessagingTemplate messagingTemplate;

  @GetMapping("/")
  public String index() {
    return "index";
  }

  @MessageMapping("/send")
  @SendTo("/queue/responses")
  public String send(GenericMessage<String> message) {
    try {
      message.getHeaders().forEach((k, v) -> logger.debug("header: {}={}", k, v));
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      String response = df.format(new Date()) + ">>" + message.getPayload();
      logger.info("send: {}", response);
      return response;
    } catch (Exception e) {
      logger.error("send failed", e);
      return null;
    }
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
  @SendTo("/topic/callback")
  public void callback() {
    try {
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      messagingTemplate.convertAndSend("/topic/callback", df.format(new Date()));
    } catch (MessagingException e) {
      logger.error("callback failed", e);
    }
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

  @PreDestroy
  public void destroy() {
    logger.info("Mock releasing resources...");
    try {
      TimeUnit.SECONDS.sleep(3);
    } catch (InterruptedException e) {
      logger.error("", e);
    }
    logger.info("Mock released resources.");
  }
}
