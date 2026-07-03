'use strict';

const path = require('path');
const crypto = require('crypto');
const WebSocket = require('ws');
const C = require(path.join(__dirname, '..', 'common', 'codec.js'));

function main() {
    const host = process.env.WS_SERVER || '127.0.0.1';
    const port = parseInt(process.env.WS_PORT) || C.PORT;

    C.log('ws-client', 'Starting Node.js WebSocket client [version: 1.0.0]');
    C.log('ws-client', `Connecting to ws://${host}:${port}`);

    const url = `ws://${host}:${port}`;
    const userId = `nodejs-client-${crypto.randomUUID().substring(0, 8)}`;

    const ws = new WebSocket(url, { headers: { userId } });

    let randomId = 1;

    ws.on('open', () => {
        C.log('ws-client', 'Connected');

        // Send HELLO
        ws.send(C.encodeHello(C.CLIENT_LANG));

        // Random number background task
        const randomTimer = setInterval(() => {
            const num = Math.floor(Math.random() * 9007199254740991);
            ws.send(C.encodeRandomNumber(randomId, num));
            C.log('ws-client', `RANDOM_NUMBER id=${randomId} number=${num}`);
            randomId++;
        }, C.RANDOM_INTERVAL);

        ws.on('message', (data) => {
            try {
                const msg = C.decodeMessage(data);
                switch (msg.type) {
                    case C.MSG_BONJOUR:
                        C.log('ws-client', `BONJOUR server_language=${msg.bonjour.serverLanguage}`);
                        break;

                    case C.MSG_PING:
                        C.log('ws-client', `PING ts=${msg.ping.timestampMs}`);
                        ws.send(C.encodePong(msg.ping.timestampMs));
                        C.log('ws-client', `PONG ts=${msg.ping.timestampMs}`);
                        break;

                    case C.MSG_TIME_NOTIFICATION:
                        C.log('ws-client', `TIME_NOTIFICATION ts=${msg.timeNotif.timestampMs} iso=${msg.timeNotif.iso8601}`);
                        break;

                    case C.MSG_KISS_REQUEST:
                        const kr = msg.kissReq;
                        C.log('ws-client', `KISS_REQUEST os=${kr.osName} ver=${kr.osVersion} rel=${kr.osRelease} arch=${kr.osArchitecture}`);
                        ws.send(C.encodeKissResponse('en_US', 'UTF-8', Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'));
                        C.log('ws-client', 'KISS_RESPONSE sent');
                        break;

                    case C.MSG_ECHO_RESPONSE:
                        const er = msg.echoResp;
                        C.log('ws-client', `ECHO_RESPONSE status=${er.status} results=${er.results.length}`);
                        er.results.forEach((r, i) => {
                            C.log('ws-client', `  Result #${i + 1}: idx=${r.idx} type=${r.type} kv=${JSON.stringify(r.kv)}`);
                        });
                        break;

                    case C.MSG_HASH_RESPONSE:
                        C.log('ws-client', `HASH_RESPONSE id=${msg.hash.id} hash=${msg.hash.hashHex}`);
                        break;

                    case C.MSG_ERROR:
                        C.log('ws-client', `ERROR code=${msg.error.code} msg=${msg.error.message}`);
                        break;

                    default:
                        C.log('ws-client', `Unknown message type: 0x${msg.type.toString(16)}`);
                }
            } catch (e) {
                C.log('ws-client', `Decode error: ${e.message}`);
            }
        });

        ws.on('close', () => {
            clearInterval(randomTimer);
            C.log('ws-client', 'Disconnected');
        });

        ws.on('error', () => {});
    });
}

main();
