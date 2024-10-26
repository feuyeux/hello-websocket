package org.feuyeux.websocket.info;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EchoResult {
  private long idx;
  private EchoType type;
  private Map<String, String> kv;
}
