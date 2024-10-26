package org.feuyeux.websocket.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextInboundHandler extends HelloInboundHandler<TextWebSocketFrame> {

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) {
    try {
      long start = System.currentTimeMillis();
      final String x = textWebSocketFrame.text();
      log.info("received[T]: {}", x);
      long end = System.currentTimeMillis();
      log.debug("elapsed[B]: {} ms", end - start);
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
