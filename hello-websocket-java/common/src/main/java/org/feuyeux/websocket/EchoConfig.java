package org.feuyeux.websocket;

public record EchoConfig() {
  public static final String host = "127.0.0.1";
  public static final String WEBSOCKET_PATH = "/websocket";
  private static final String WS_HELLO_SECURE = "WS_HELLO_SECURE";
  public static final boolean SSL = System.getProperty(WS_HELLO_SECURE) != null;
  public static final int TCP_PORT = 9800;
  public static final int TLS_PORT = 9886;
}
