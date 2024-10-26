package org.feuyeux.websocket.tools;

import static java.util.stream.Collectors.toList;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.Charset;
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
      Arrays.asList("Hello", "Bonjour", "Hola", "こんにちは", "Ciao", "안녕하세요", "你好");
  private static final Map<String, String> ANS_MAP =
      Map.of(
          "Hello",
          "Thank you very much",
          "Bonjour",
          "Merci beaucoup",
          "Hola",
          "Muchas Gracias",
          "こんにちは",
          "どうも ありがとう ございます",
          "Ciao",
          "Mille Grazie",
          "안녕하세요",
          "대단히 감사합니다",
          "你好",
          "非常感谢");

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
    return String.valueOf(random.nextInt(HELLO_LIST.size()));
  }

  public static List<EchoResult> buildResults(String... ids) {
    return Stream.of(ids).map(HelloUtils::buildResult).toList();
  }

  public static EchoResult buildResult(String id) {
    int index;
    try {
      index = Integer.parseInt(id);
      if (index >= getHelloList().size()) {
        index = getHelloList().size() - 1;
      }
    } catch (NumberFormatException ignored) {
      index = 0;
    }
    String hello = getHelloList().get(index);
    Map<String, String> kv = new HashMap<>();
    kv.put("id", UUID.randomUUID().toString());
    kv.put("idx", id);
    kv.put("data", hello + "," + getAnswerMap().get(hello));
    kv.put("meta", "JAVA");
    return EchoResult.builder().idx(System.nanoTime()).type(EchoType.OK).kv(kv).build();
  }

  public static KissRequest buildKissRequest() {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    String osName = osBean.getName();
    String osVersion = osBean.getVersion();
    String osArchitecture = osBean.getArch();
    String osRelease = System.getProperty("os.version");

    return KissRequest.builder()
        .body(
            KissRequest.Body.builder()
                .type("kiss")
                .content(
                    KissRequest.Content.builder()
                        .osName(osName)
                        .osVersion(osVersion)
                        .osRelease(osRelease)
                        .osArchitecture(osArchitecture)
                        .build())
                .build())
        .build();
  }

  public static KissResponse buildKissResponse() {
    String language = System.getProperty("user.language");
    String encoding = Charset.defaultCharset().name();
    String timeZone = TimeZone.getDefault().getID();

    return KissResponse.builder()
        .body(
            KissResponse.Body.builder()
                .type("kiss")
                .content(
                    KissResponse.Content.builder()
                        .language(language)
                        .encoding(encoding)
                        .timeZone(timeZone)
                        .build())
                .build())
        .build();
  }
}
