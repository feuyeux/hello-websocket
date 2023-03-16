package org.feuyeux.websocket.codec;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.feuyeux.websocket.info.EchoRequest;

public class EchoRequestCodec {

  public static ByteBuf encode(EchoRequest echoRequest) {
    ByteBuf byteBuf = Unpooled.buffer();
    byteBuf.writeLong(echoRequest.getId());
    byteBuf.writeBytes(echoRequest.getData().getBytes());
    return byteBuf;
  }

  public static EchoRequest decode(ByteBuf byteBuf) {
    long id = byteBuf.readLong();
    int currentIndex = byteBuf.readerIndex();
    int endIndex = byteBuf.writerIndex();

    byte[] dst = new byte[endIndex - currentIndex];
    byteBuf.readBytes(dst);
    String data = new String(dst);
    return new EchoRequest(id, data);
  }
}
