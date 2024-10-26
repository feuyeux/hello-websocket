package org.feuyeux.websocket.client;

import static org.feuyeux.websocket.tools.HelloUtils.buildKissResponse;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
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
import org.feuyeux.websocket.info.KissRequest;

@Slf4j
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
  private static Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();
  private static String kissResponse = gson.toJson(buildKissResponse());
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
    log.info("WebSocket Client connected!");
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
    if (msg instanceof FullHttpResponse response) {
      throw new Exception(
          "Unexpected FullHttpResponse (getStatus="
              + response.status()
              + ", content="
              + response.content().toString(CharsetUtil.UTF_8)
              + ')');
    }

    final WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame textFrame) {
      String json = textFrame.text();
      try {
        KissRequest kissRequest = gson.fromJson(json, KissRequest.class);
        log.info("received[T]:{}", kissRequest);
        ch.writeAndFlush(new TextWebSocketFrame(kissResponse));
      } catch (JsonSyntaxException e) {
        log.warn("received[T]-{}", json);
      }
    } else if (frame instanceof BinaryWebSocketFrame binaryFrame) {
      ByteBuf byteBuf = binaryFrame.content();
      try {
        EchoResponse echoResponse = EchoResponseCodec.decode(byteBuf);
        log.info("received[B]:{}", echoResponse);
      } catch (Exception e) {
        log.warn("received[B]-{}", byteBuf.toString(CharsetUtil.UTF_8));
      }
    } else {
      log.error("Unsupported frame type: {}", frame.getClass().getName());
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    log.error("", cause);
    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    ctx.close();
  }
}
