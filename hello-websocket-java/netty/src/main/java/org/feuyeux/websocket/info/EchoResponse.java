package org.feuyeux.websocket.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EchoResponse {

  private long id;
  private long elapse;
  private String data;

  @Override
  public String toString() {
    return String.format("[%d] %s (%d)", id, data, elapse);
  }
}
