// Hello WebSocket Protocol Codec Tests - Dart
import 'dart:typed_data';
import 'package:test/test.dart';
import '../common/codec.dart';

void main() {
  test('HELLO worked example byte-level match', () {
    final msg = hello('Go');
    final data = msg.encode();
    final expected = Uint8List.fromList([
      0x48,
      0x01,
      0x01,
      0x00,
      0x00,
      0x00,
      0x00,
      0x06,
      0x00,
      0x00,
      0x00,
      0x02,
      0x47,
      0x6F,
    ]);
    expect(data, equals(expected));
  });

  test('Round-trip all simple message types', () {
    final messages = [
      hello('Dart'),
      bonjour('Java'),
      Message(msgEchoRequest)
        ..echoId = 42
        ..echoMeta = 'Python'
        ..echoData = 'hello',
      ping(1700000000000),
      pong(1700000000001),
      timeNotif(1700000000000, '2023-11-14T22:13:20Z'),
      randomNumberMsg(99, 42),
      hashResponseMsg(99, '7688b6ef5a'),
      disconnectMsg('bye'),
      errorMsg(errUnknownMsgType, 'bad type'),
    ];
    for (final orig in messages) {
      final decoded = decodeMessage(orig.encode());
      expect(decoded.type, equals(orig.type));
    }
  });

  test('Round-trip EchoResponse', () {
    final orig = Message(msgEchoResponse)
      ..echoStatus = 200
      ..echoResults = [
        EchoResult(123, 0, {'id': '1', 'data': 'Hello'})
      ];
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgEchoResponse));
    expect(decoded.echoStatus, equals(200));
    expect(decoded.echoResults!.length, equals(1));
    expect(decoded.echoResults![0].kv['id'], equals('1'));
  });

  test('Round-trip Kiss', () {
    final orig = kissRequest('Linux', '6.6', 'arch', 'AMD64');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgKissRequest));
    expect(decoded.osName, equals('Linux'));
    expect(decoded.osArch, equals('AMD64'));
  });

  test('Bad magic rejected', () {
    final data =
        Uint8List.fromList([0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeFrame(data), throwsException);
  });

  test('Bad version rejected', () {
    final data =
        Uint8List.fromList([0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeFrame(data), throwsException);
  });

  test('Truncated payload rejected', () {
    final data =
        Uint8List.fromList([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF]);
    expect(() => decodeFrame(data), throwsException);
  });

  test('Hash number produces 10-char hex', () {
    final h = hashNumber(42);
    expect(h.length, equals(10));
    expect(h, equals(hashNumber(42)));
  });

  test('Round-trip KissResponse', () {
    final orig = kissResponse('en_US', 'UTF-8', 'UTC');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgKissResponse));
    expect(decoded.kissLanguage, equals('en_US'));
    expect(decoded.kissEncoding, equals('UTF-8'));
    expect(decoded.kissTimeZone, equals('UTC'));
  });

  test('Round-trip Disconnect', () {
    final orig = disconnectMsg('test reason');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgDisconnect));
    expect(decoded.disconnectReason, equals('test reason'));
  });

  test('Round-trip Error', () {
    final orig = errorMsg(errDecode, 'decode failed');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgError));
    expect(decoded.errorCode, equals(errDecode));
    expect(decoded.errorMessage, equals('decode failed'));
  });

  test('Round-trip RandomNumber', () {
    final orig = randomNumberMsg(5, 99999);
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgRandomNumber));
    expect(decoded.randomId, equals(5));
    expect(decoded.randomNumber, equals(99999));
  });

  test('Round-trip HashResponse', () {
    final orig = hashResponseMsg(7, 'abcdef1234');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.type, equals(msgHashResponse));
    expect(decoded.randomId, equals(7));
    expect(decoded.hashHex, equals('abcdef1234'));
  });

  test('Empty string round-trip', () {
    final orig = disconnectMsg('');
    final decoded = decodeMessage(orig.encode());
    expect(decoded.disconnectReason, equals(''));
  });

  test('Unknown message type rejected', () {
    final data =
        Uint8List.fromList([0x48, 0x01, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeMessage(data), throwsException);
  });

  test('Empty EchoResponse results array', () {
    final orig = Message(msgEchoResponse)
      ..echoStatus = 204
      ..echoResults = [];
    final decoded = decodeMessage(orig.encode());
    expect(decoded.echoStatus, equals(204));
    expect(decoded.echoResults!.length, equals(0));
  });
}
