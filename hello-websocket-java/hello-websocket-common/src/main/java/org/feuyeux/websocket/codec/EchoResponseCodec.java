package org.feuyeux.websocket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.feuyeux.websocket.info.EchoResponse;
import org.feuyeux.websocket.info.EchoResult;
import org.feuyeux.websocket.info.EchoType;

public class EchoResponseCodec {

  // Encode EchoResponse to ByteBuf
  public static ByteBuf encode(EchoResponse echoResponse) {
    ByteBuf byteBuf = Unpooled.buffer();
    byteBuf.writeInt(echoResponse.getStatus());
    List<EchoResult> results = echoResponse.getResults();
    byteBuf.writeInt(results.size());
    for (EchoResult result : results) {
      byteBuf.writeLong(result.getIdx());
      byteBuf.writeInt(result.getType().ordinal());
      Map<String, String> kv = result.getKv();
      byteBuf.writeInt(kv.size());
      for (Map.Entry<String, String> entry : kv.entrySet()) {
        byte[] keyBytes = entry.getKey().getBytes();
        byteBuf.writeInt(keyBytes.length);
        byteBuf.writeBytes(keyBytes);
        byte[] valueBytes = entry.getValue().getBytes();
        byteBuf.writeInt(valueBytes.length);
        byteBuf.writeBytes(valueBytes);
      }
    }
    return byteBuf;
  }

  public static EchoResponse decode(ByteBuf byteBuf) {
    EchoResponse echoResponse = new EchoResponse();
    echoResponse.setStatus(byteBuf.readInt());
    int resultsSize = byteBuf.readInt();
    List<EchoResult> results = new ArrayList<>(resultsSize);
    for (int i = 0; i < resultsSize; i++) {
      long idx = byteBuf.readLong();
      EchoType type = EchoType.values()[byteBuf.readInt()];
      int kvSize = byteBuf.readInt();
      Map<String, String> kv = new HashMap<>(kvSize);
      for (int j = 0; j < kvSize; j++) {
        int keyLength = byteBuf.readInt();
        byte[] keyBytes = new byte[keyLength];
        byteBuf.readBytes(keyBytes);
        String key = new String(keyBytes);
        int valueLength = byteBuf.readInt();
        byte[] valueBytes = new byte[valueLength];
        byteBuf.readBytes(valueBytes);
        String value = new String(valueBytes);
        kv.put(key, value);
      }
      results.add(new EchoResult(idx, type, kv));
    }
    echoResponse.setResults(results);
    return echoResponse;
  }
}
