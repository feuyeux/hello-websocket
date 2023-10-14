package org.feuyeux.websocket.config;

public class EchoConfig {

  public static final String host = "127.0.0.1";

  public static final String WEBSOCKET_PATH = "/websocket";
  private static final String WS_HELLO_SECURE = "WS_HELLO_SECURE";
  public static final boolean SSL = System.getProperty(WS_HELLO_SECURE) != null;
}
