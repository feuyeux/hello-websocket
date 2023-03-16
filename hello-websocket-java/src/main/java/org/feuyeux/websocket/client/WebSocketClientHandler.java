package org.feuyeux.websocket.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.codec.EchoResponseCodec;
import org.feuyeux.websocket.info.EchoResponse;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@Slf4j
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

  private final WebSocketClientHandshaker handShaker;
  private ChannelPromise handshakeFuture;

  public WebSocketClientHandler(final WebSocketClientHandshaker handShaker) {
    this.handShaker = handShaker;
  }

  public ChannelFuture handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(final ChannelHandlerContext ctx) {
    handshakeFuture = ctx.newPromise();
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) {
    handShaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) {
    log.info("WebSocket Client disconnected!");
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    final Channel ch = ctx.channel();
    if (!handShaker.isHandshakeComplete()) {
      // web socket client connected
      handShaker.finishHandshake(ch, (FullHttpResponse) msg);
      handshakeFuture.setSuccess();
      return;
    }

    if (msg instanceof FullHttpResponse) {
      final FullHttpResponse response = (FullHttpResponse) msg;
      throw new Exception(
          "Unexpected FullHttpResponse (getStatus=" + response.getStatus() + ", content="
              + response.content().toString(CharsetUtil.UTF_8) + ')');
    }

    final WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame) {
      final TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
      log.info("received[T]:{}", textFrame.text());
    } else if (frame instanceof BinaryWebSocketFrame) {
      final BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
      ByteBuf byteBuf = binaryFrame.content();
      EchoResponse echoResponse = EchoResponseCodec.decode(byteBuf);
      log.info("received[B]:{}", echoResponse);
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
      throws Exception {
    cause.printStackTrace();
    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    ctx.close();
  }
}