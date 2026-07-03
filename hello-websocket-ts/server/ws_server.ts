// Hello WebSocket Protocol - TypeScript Server
import { WebSocketServer, WebSocket } from 'ws';
import { randomUUID } from 'crypto';
import {
  PORT, SERVER_LANG,
  MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
  MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
  MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
  MSG_DISCONNECT, MSG_ERROR,
  ERR_DECODE, ERR_UNKNOWN_MSG_TYPE,
  PING_INTERVAL_MS, SESSION_TIMEOUT_MS, TIME_INTERVAL_MS, KISS_INTERVAL_MS,
  encodeMessage, decodeMessage, log, nowMs, nowISO, hashNumber,
  type Message,
} from '../common/codec.js';

interface Session {
  userId: string;
  sessionId: string;
  clientLanguage: string;
  lastPongTs: bigint;
  timers: NodeJS.Timeout[];
}

const wss = new WebSocketServer({ port: PORT });

wss.on('connection', (ws: WebSocket, req) => {
  const userId = req.headers['userid'] as string || `ts-${randomUUID().substring(0, 8)}`;
  const session: Session = {
    userId,
    sessionId: randomUUID(),
    clientLanguage: 'unknown',
    lastPongTs: nowMs(),
    timers: [],
  };

  log('ws-server', `[${userId}] session+`);

  // Background: PING every 1s
  session.timers.push(setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(encodeMessage({ type: MSG_PING, timestampMs: nowMs() }));
    }
  }, PING_INTERVAL_MS));

  // Background: TIME_NOTIFICATION every 5s
  session.timers.push(setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(encodeMessage({ type: MSG_TIME_NOTIFICATION, timestampMs: nowMs(), iso8601: nowISO() }));
    }
  }, TIME_INTERVAL_MS));

  // Background: KISS_REQUEST every 5s
  session.timers.push(setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(encodeMessage({
        type: MSG_KISS_REQUEST,
        osName: process.platform,
        osVersion: 'unknown',
        osRelease: 'unknown',
        osArch: process.arch,
      }));
    }
  }, KISS_INTERVAL_MS));

  // Background: timeout check every 5s
  session.timers.push(setInterval(() => {
    const last = session.lastPongTs;
    if (nowMs() - last > BigInt(SESSION_TIMEOUT_MS)) {
      log('ws-server', `[${userId}] session timeout`);
      ws.close();
    }
  }, 5000));

  ws.on('message', (data: Buffer) => {
    let msg: Message;
    try {
      msg = decodeMessage(data);
    } catch (e) {
      log('ws-server', `Decode error: ${(e as Error).message}`);
      ws.send(encodeMessage({ type: MSG_ERROR, errorCode: ERR_DECODE, errorMessage: (e as Error).message }));
      return;
    }

    switch (msg.type) {
      case MSG_HELLO:
        session.clientLanguage = msg.clientLanguage;
        log('ws-server', `HELLO from ${msg.clientLanguage}, session=${session.sessionId}, time=${nowMs()}`);
        ws.send(encodeMessage({ type: MSG_BONJOUR, serverLanguage: SERVER_LANG }));
        break;

      case MSG_ECHO_REQUEST:
        log('ws-server', `ECHO_REQUEST id=${msg.echoId} meta=${msg.echoMeta} data=${msg.echoData}`);
        ws.send(encodeMessage({
          type: MSG_ECHO_RESPONSE,
          echoStatus: 200,
          echoResults: [{
            idx: nowMs(),
            type: 0,
            kv: {
              id: msg.echoId.toString(),
              idx: msg.echoData,
              data: msg.echoData,
              meta: session.clientLanguage,
            },
          }],
        }));
        break;

      case MSG_KISS_RESPONSE:
        log('ws-server', `KISS_RESPONSE lang=${msg.kissLanguage} enc=${msg.kissEncoding} tz=${msg.kissTimeZone}`);
        break;

      case MSG_PONG:
        session.lastPongTs = msg.timestampMs;
        log('ws-server', `PONG ts=${msg.timestampMs}`);
        break;

      case MSG_RANDOM_NUMBER:
        log('ws-server', `RANDOM_NUMBER id=${msg.randomId} number=${msg.randomNumber}`);
        const hash = hashNumber(msg.randomNumber);
        ws.send(encodeMessage({ type: MSG_HASH_RESPONSE, randomId: msg.randomId, hashHex: hash }));
        log('ws-server', `HASH_RESPONSE id=${msg.randomId} hash=${hash}`);
        break;

      case MSG_DISCONNECT:
        log('ws-server', `DISCONNECT reason=${msg.disconnectReason}`);
        ws.close();
        break;

      case MSG_ERROR:
        log('ws-server', `ERROR code=${msg.errorCode} msg=${msg.errorMessage}`);
        break;

      default:
        log('ws-server', `Unknown message type: 0x${msg.type.toString(16)}`);
        ws.send(encodeMessage({ type: MSG_ERROR, errorCode: ERR_UNKNOWN_MSG_TYPE, errorMessage: `unknown type 0x${msg.type.toString(16)}` }));
    }
  });

  ws.on('close', () => {
    session.timers.forEach(t => clearInterval(t));
    log('ws-server', `[${userId}] session-`);
  });

  ws.on('error', (err: Error) => {
    log('ws-server', `Error: ${err.message}`);
  });
});

wss.on('listening', () => {
  log('ws-server', `Starting TypeScript WebSocket server on port ${PORT}`);
});

process.on('SIGINT', () => {
  log('ws-server', 'Shutting down...');
  wss.close();
  process.exit(0);
});
