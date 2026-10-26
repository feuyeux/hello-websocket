// Hello WebSocket Protocol - Dart Server
import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import '../common/codec.dart';

void main() async {
  final portEnv = Platform.environment['WS_PORT'];
  final p = portEnv != null ? int.tryParse(portEnv) ?? port : port;

  log('ws-server', 'Starting Dart WebSocket server on port $p');

  final server = await HttpServer.bind(InternetAddress.anyIPv4, p);
  await for (final request in server) {
    if (WebSocketTransformer.isUpgradeRequest(request)) {
      final userId = request.headers.value('userId') ??
          'dart-${DateTime.now().millisecondsSinceEpoch}';
      final ws = await WebSocketTransformer.upgrade(request);
      _handleConnection(ws, userId);
    } else {
      request.response.statusCode = HttpStatus.badRequest;
      await request.response.close();
    }
  }
}

void _handleConnection(WebSocket ws, String userId) {
  final sessionId = '${DateTime.now().millisecondsSinceEpoch}';
  var clientLanguage = 'unknown';
  var lastPongTs = nowMs();

  log('ws-server', '[$userId] session+');

  // Background: PING every 1s
  Timer.periodic(Duration(milliseconds: pingIntervalMs), (t) {
    if (ws.readyState != WebSocket.open) {
      t.cancel();
      return;
    }
    ws.add(ping(nowMs()).encode());
  });

  // Background: TIME_NOTIFICATION every 5s
  Timer.periodic(Duration(milliseconds: timeIntervalMs), (t) {
    if (ws.readyState != WebSocket.open) {
      t.cancel();
      return;
    }
    ws.add(timeNotif(nowMs(), nowISO()).encode());
  });

  // Background: KISS_REQUEST every 5s
  Timer.periodic(Duration(milliseconds: kissIntervalMs), (t) {
    if (ws.readyState != WebSocket.open) {
      t.cancel();
      return;
    }
    ws.add(kissRequest(
            Platform.operatingSystem, 'unknown', 'unknown', Platform.version)
        .encode());
  });

  // Background: timeout check every 5s
  Timer.periodic(Duration(seconds: 5), (t) {
    if (ws.readyState != WebSocket.open) {
      t.cancel();
      return;
    }
    if (nowMs() - lastPongTs > sessionTimeoutMs) {
      log('ws-server', '[$userId] session timeout');
      ws.close();
      t.cancel();
    }
  });

  // Receive loop
  ws.listen(
    (data) {
      if (data is! List<int>) return;
      if (data.length > 1024 * 1024) {
        ws.close(WebSocketStatus.messageTooBig, 'message exceeds 1 MiB');
        return;
      }
      final bytes = Uint8List.fromList(data);
      Message msg;
      try {
        msg = decodeMessage(bytes);
      } catch (e) {
        log('ws-server', 'Decode error: $e');
        final unknown = e.toString().contains('unknown message type');
        ws.add(errorMsg(unknown ? errUnknownMsgType : errDecode, e.toString())
            .encode());
        if (!unknown)
          ws.close(WebSocketStatus.protocolError, 'invalid protocol frame');
        return;
      }

      switch (msg.type) {
        case msgHello:
          clientLanguage = msg.clientLanguage!;
          log('ws-server',
              'HELLO from $clientLanguage, session=$sessionId, time=${nowMs()}');
          ws.add(bonjour(serverLang).encode());
          break;

        case msgEchoRequest:
          log('ws-server',
              'ECHO_REQUEST id=${msg.echoId} meta=${msg.echoMeta} data=${msg.echoData}');
          final resp = Message(msgEchoResponse)
            ..echoStatus = 200
            ..echoResults = [
              EchoResult(nowMs(), 0, {
                'id': msg.echoId.toString(),
                'idx': msg.echoData!,
                'data': msg.echoData!,
                'meta': clientLanguage,
              })
            ];
          ws.add(resp.encode());
          break;

        case msgKissResponse:
          log('ws-server',
              'KISS_RESPONSE lang=${msg.kissLanguage} enc=${msg.kissEncoding} tz=${msg.kissTimeZone}');
          break;

        case msgPong:
          lastPongTs = nowMs();
          log('ws-server', 'PONG ts=${msg.timestampMs}');
          break;

        case msgRandomNumber:
          log('ws-server',
              'RANDOM_NUMBER id=${msg.randomId} number=${msg.randomNumber}');
          final hash = hashNumber(msg.randomNumber!);
          ws.add(hashResponseMsg(msg.randomId!, hash).encode());
          log('ws-server', 'HASH_RESPONSE id=${msg.randomId} hash=$hash');
          break;

        case msgDisconnect:
          log('ws-server', 'DISCONNECT reason=${msg.disconnectReason}');
          ws.close();
          break;

        case msgError:
          log('ws-server',
              'ERROR code=${msg.errorCode} msg=${msg.errorMessage}');
          break;

        default:
          log('ws-server',
              'Unknown message type: 0x${msg.type.toRadixString(16)}');
          ws.add(errorMsg(errUnknownMsgType,
                  'unknown type 0x${msg.type.toRadixString(16)}')
              .encode());
      }
    },
    onDone: () {
      log('ws-server', '[$userId] session-');
    },
    onError: (e) {
      log('ws-server', 'Error: $e');
    },
  );
}
