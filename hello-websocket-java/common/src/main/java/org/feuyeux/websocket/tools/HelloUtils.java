package org.feuyeux.websocket.tools;

import static java.util.stream.Collectors.toList;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.feuyeux.websocket.info.*;

public class HelloUtils {

  private static final RandomGenerator random = RandomGenerator.getDefault();

  // "81", "わかった"
  // "82", "알았어"
  // "44", "Got it"
  // "33", "Je l'ai"
  // "7", "Понял"
  // "30", "Το έπιασα"
  // "ru:Большое спасибо"
  // "fr:Merci beaucoup"
  // "es:Muchas Gracias"
  // "ar:" + "شكرا جزيلا"
  // "he:" + "תודה רבה"

  private static final List<String> HELLO_LIST =
      Arrays.asList("Hello", "Bonjour", "Hola", "こんにちは", "Ciao", "안녕하세요");
  private static final Map<String, String> ANS_MAP =
      Map.of(
          "你好", "非常感谢",
          "Hello", "Thank you very much",
          "Bonjour", "Merci beaucoup",
          "Hola", "Muchas Gracias",
          "こんにちは", "どうも ありがとう ございます",
          "Ciao", "Mille Grazie",
          "안녕하세요", "대단히 감사합니다");

  public static List<String> getHelloList() {
    return HELLO_LIST;
  }

  public static Map<String, String> getAnswerMap() {
    return ANS_MAP;
  }

  public static LinkedList<EchoRequest> buildLinkRequests() {
    LinkedList<EchoRequest> requests = new LinkedList<>();
    for (int i = 0; i < 3; i++) {
      EchoRequest echoRequest =
          EchoRequest.builder()
              .id(System.nanoTime())
              .meta("JAVA")
              .data(HelloUtils.getRandomId())
              .build();
      requests.addFirst(echoRequest);
    }
    return requests;
  }

  public static List<String> getRandomIds(int max) {
    return IntStream.range(0, max).mapToObj(i -> getRandomId()).collect(toList());
  }

  public static String getRandomId() {
    return String.valueOf(random.nextInt(5));
  }

  public static List<EchoResult> buildResults(String... ids) {
    return Stream.of(ids).map(HelloUtils::buildResult).toList();
  }

  public static EchoResult buildResult(String id) {
    int index;
    try {
      index = Integer.parseInt(id);
    } catch (NumberFormatException ignored) {
      index = 0;
    }
    String hello;
    if (index > 5) {
      hello = "你好";
    } else {
      hello = getHelloList().get(index);
    }
    Map<String, String> kv = new HashMap<>();
    kv.put("id", UUID.randomUUID().toString());
    kv.put("idx", id);
    kv.put("data", hello + "," + getAnswerMap().get(hello));
    kv.put("meta", "JAVA");
    return EchoResult.builder().idx(System.nanoTime()).type(EchoType.OK).kv(kv).build();
  }
}
