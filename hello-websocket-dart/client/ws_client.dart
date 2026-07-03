// Hello WebSocket Protocol - Dart Client
import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import '../common/codec.dart';

void main() async {
  final host = Platform.environment['WS_SERVER'] ?? '127.0.0.1';
  final portEnv = Platform.environment['WS_PORT'];
  final p = portEnv != null ? int.tryParse(portEnv) ?? port : port;

  log('ws-client', 'Starting Dart WebSocket client [version: 1.0.0]');
  final url = 'ws://$host:$p';
  log('ws-client', 'Connecting to $url');

  for (int attempt = 1; attempt <= 3; attempt++) {
    log('ws-client', 'Connection attempt $attempt/3 to $url');
    try {
      await _tryConnect(url);
      return;
    } catch (e) {
      log('ws-client', 'Error: $e');
      if (attempt < 3) await Future.delayed(Duration(seconds: 2));
    }
  }
  log('ws-client', 'Failed to connect after 3 attempts');
  exit(1);
}

Future<void> _tryConnect(String url) async {
  final ws = await WebSocket.connect(url, headers: {
    'userId': 'dart-client-${DateTime.now().millisecondsSinceEpoch}',
  });
  log('ws-client', 'Connected');

  // Send HELLO
  ws.add(hello(clientLang).encode());

  // Random number background task every 5s
  final rng = Random();
  var randomId = 1;
  Timer.periodic(Duration(milliseconds: randomIntervalMs), (t) {
    if (ws.readyState != WebSocket.open) { t.cancel(); return; }
    final num = rng.nextInt(9007199254740991);
    ws.add(randomNumberMsg(randomId, num).encode());
    log('ws-client', 'RANDOM_NUMBER id=$randomId number=$num');
    randomId++;
  });

  // Receive loop
  final completer = Completer<void>();
  ws.listen(
    (data) {
      if (data is! List<int>) return;
      final bytes = Uint8List.fromList(data);
      Message msg;
      try {
        msg = decodeMessage(bytes);
      } catch (e) {
        log('ws-client', 'Decode error: $e');
        return;
      }

      switch (msg.type) {
        case msgBonjour:
          log('ws-client', 'BONJOUR server_language=${msg.serverLanguage}');
          break;

        case msgPing:
          log('ws-client', 'PING ts=${msg.timestampMs}');
          ws.add(pong(msg.timestampMs!).encode());
          log('ws-client', 'PONG ts=${msg.timestampMs}');
          break;

        case msgTimeNotification:
          log('ws-client', 'TIME_NOTIFICATION ts=${msg.timestampMs} iso=${msg.iso8601}');
          break;

        case msgKissRequest:
          log('ws-client', 'KISS_REQUEST os=${msg.osName} ver=${msg.osVersion} rel=${msg.osRelease} arch=${msg.osArch}');
          ws.add(kissResponse('en_US', 'UTF-8', DateTime.now().timeZoneName).encode());
          log('ws-client', 'KISS_RESPONSE sent');
          break;

        case msgEchoResponse:
          log('ws-client', 'ECHO_RESPONSE status=${msg.echoStatus} results=${msg.echoResults!.length}');
          for (int i = 0; i < msg.echoResults!.length; i++) {
            final r = msg.echoResults![i];
            log('ws-client', '  Result #${i + 1}: idx=${r.idx} type=${r.type} kv=${r.kv}');
          }
          break;

        case msgHashResponse:
          log('ws-client', 'HASH_RESPONSE id=${msg.randomId} hash=${msg.hashHex}');
          break;

        case msgError:
          log('ws-client', 'ERROR code=${msg.errorCode} msg=${msg.errorMessage}');
          break;

        default:
          log('ws-client', 'Unknown message type: 0x${msg.type.toRadixString(16)}');
      }
    },
    onDone: () {
      log('ws-client', 'Disconnected');
      completer.complete();
    },
    onError: (e) {
      log('ws-client', 'Error: $e');
      completer.complete();
    },
  );

  await completer.future;

  // Send DISCONNECT
  if (ws.readyState == WebSocket.open) {
    ws.add(disconnectMsg('client shutdown').encode());
    await ws.close();
  }
}
