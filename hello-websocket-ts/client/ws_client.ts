// Hello WebSocket Protocol - TypeScript Client
import WebSocket from 'ws';
import { randomUUID } from 'crypto';
import {
  PORT, CLIENT_LANG, SERVER_LANG,
  MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
  MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
  MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
  MSG_DISCONNECT, MSG_ERROR,
  RANDOM_INTERVAL_MS,
  encodeMessage, decodeMessage, log, nowMs,
  type Message,
} from '../common/codec.js';

const host = process.env.WS_SERVER || '127.0.0.1';
const port = parseInt(process.env.WS_PORT || String(PORT), 10);

log('ws-client', 'Starting TypeScript WebSocket client [version: 1.0.0]');
const url = `ws://${host}:${port}`;
log('ws-client', `Connecting to ${url}`);

for (let attempt = 1; attempt <= 3; attempt++) {
  log('ws-client', `Connection attempt ${attempt}/3 to ${url}`);
  try {
    await connect(url);
    process.exit(0);
  } catch (e) {
    log('ws-client', `Error: ${(e as Error).message}`);
    if (attempt < 3) await new Promise(r => setTimeout(r, 2000));
  }
}
log('ws-client', 'Failed to connect after 3 attempts');
process.exit(1);

async function connect(url: string): Promise<void> {
  const ws = new WebSocket(url, {
    headers: { userId: `ts-client-${randomUUID().substring(0, 8)}` },
  });

  await new Promise<void>((resolve, reject) => {
    ws.on('open', () => {
      log('ws-client', 'Connected');
      ws.send(encodeMessage({ type: MSG_HELLO, clientLanguage: CLIENT_LANG }));
      resolve();
    });
    ws.on('error', reject);
  });

  // Random number background task every 5s
  let randomId = 1n;
  const randomTimer = setInterval(() => {
    if (ws.readyState !== WebSocket.OPEN) return;
    const num = BigInt(Math.floor(Math.random() * Number.MAX_SAFE_INTEGER));
    ws.send(encodeMessage({ type: MSG_RANDOM_NUMBER, randomId, randomNumber: num }));
    log('ws-client', `RANDOM_NUMBER id=${randomId} number=${num}`);
    randomId++;
  }, RANDOM_INTERVAL_MS);

  return new Promise<void>((resolve) => {
    ws.on('message', (data: Buffer) => {
      let msg: Message;
      try {
        msg = decodeMessage(data);
      } catch (e) {
        log('ws-client', `Decode error: ${(e as Error).message}`);
        return;
      }

      switch (msg.type) {
        case MSG_BONJOUR:
          log('ws-client', `BONJOUR server_language=${msg.serverLanguage}`);
          break;

        case MSG_PING:
          log('ws-client', `PING ts=${msg.timestampMs}`);
          ws.send(encodeMessage({ type: MSG_PONG, timestampMs: msg.timestampMs }));
          log('ws-client', `PONG ts=${msg.timestampMs}`);
          break;

        case MSG_TIME_NOTIFICATION:
          log('ws-client', `TIME_NOTIFICATION ts=${msg.timestampMs} iso=${msg.iso8601}`);
          break;

        case MSG_KISS_REQUEST:
          log('ws-client', `KISS_REQUEST os=${msg.osName} ver=${msg.osVersion} rel=${msg.osRelease} arch=${msg.osArch}`);
          ws.send(encodeMessage({
            type: MSG_KISS_RESPONSE,
            kissLanguage: 'en_US',
            kissEncoding: 'UTF-8',
            kissTimeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
          }));
          log('ws-client', 'KISS_RESPONSE sent');
          break;

        case MSG_ECHO_RESPONSE:
          log('ws-client', `ECHO_RESPONSE status=${msg.echoStatus} results=${msg.echoResults.length}`);
          msg.echoResults.forEach((r, i) => {
            log('ws-client', `  Result #${i + 1}: idx=${r.idx} type=${r.type} kv=${JSON.stringify(r.kv)}`);
          });
          break;

        case MSG_HASH_RESPONSE:
          log('ws-client', `HASH_RESPONSE id=${msg.randomId} hash=${msg.hashHex}`);
          break;

        case MSG_ERROR:
          log('ws-client', `ERROR code=${msg.errorCode} msg=${msg.errorMessage}`);
          break;

        default:
          log('ws-client', `Unknown message type: 0x${msg.type.toString(16)}`);
      }
    });

    ws.on('close', () => {
      clearInterval(randomTimer);
      log('ws-client', 'Disconnected');
      resolve();
    });

    ws.on('error', (err: Error) => {
      log('ws-client', `Error: ${err.message}`);
      clearInterval(randomTimer);
      resolve();
    });

    process.on('SIGINT', () => {
      log('ws-client', 'Shutting down...');
      ws.send(encodeMessage({ type: MSG_DISCONNECT, disconnectReason: 'client shutdown' }));
      ws.close();
      clearInterval(randomTimer);
      resolve();
    });
  });
}
