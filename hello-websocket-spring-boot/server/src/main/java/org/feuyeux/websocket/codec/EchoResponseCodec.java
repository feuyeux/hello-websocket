package org.feuyeux.websocket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.feuyeux.websocket.pojo.EchoResponse;

public class EchoResponseCodec {

    public static ByteBuf encode(EchoResponse echoResponse) {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeLong(echoResponse.getId());
        byteBuf.writeLong(echoResponse.getElapse());
        byteBuf.writeBytes(echoResponse.getData().getBytes());
        return byteBuf;
    }

    public static EchoResponse decode(ByteBuf byteBuf) {
        long id = byteBuf.readLong();
        long elapse = byteBuf.readLong();
        int currentIndex = byteBuf.readerIndex();
        int endIndex = byteBuf.writerIndex();

        byte[] dst = new byte[endIndex - currentIndex];
        byteBuf.readBytes(dst);
        String data = new String(dst);
        return new EchoResponse(id, elapse, data);
    }
}
