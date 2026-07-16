'use strict';

const path = require('path');
const crypto = require('crypto');
const os = require('os');
const WebSocket = require('ws');
const C = require(path.join(__dirname, '..', 'common', 'codec.js'));

function main() {
    const port = parseInt(process.env.WS_PORT) || C.PORT;
    C.log('ws-server', `Starting Node.js WebSocket server on port ${port}`);

    const pathName = process.env.WS_PATH || '/ws';
    const wss = new WebSocket.Server({ port, path: pathName, maxPayload: 1024 * 1024 });

    wss.on('connection', (ws, req) => {
        const userId = req.headers['userid'] || crypto.randomUUID();
        const sessionId = crypto.randomUUID();
        const connectedAt = C.nowMs();
        let lastPongTs = connectedAt;
        let clientLanguage = 'unknown';

        C.log('ws-server', `[${userId}] session+`);

        // Background tasks
        const pingTimer = setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) ws.send(C.encodePing(C.nowMs()));
        }, C.PING_INTERVAL);

        const timeTimer = setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) ws.send(C.encodeTimeNotification(C.nowMs(), C.nowISO()));
        }, C.TIME_INTERVAL);

        const kissTimer = setInterval(() => {
            if (ws.readyState === WebSocket.OPEN) ws.send(C.encodeKissRequest(os.type(), os.release(), os.hostname(), os.arch()));
        }, C.KISS_INTERVAL);

        const timeoutTimer = setInterval(() => {
            if (C.nowMs() - lastPongTs > C.SESSION_TIMEOUT) {
                C.log('ws-server', `[${userId}] session timeout`);
                ws.close();
            }
        }, 5000);

        ws.on('message', (data) => {
            try {
                const msg = C.decodeMessage(data);
                switch (msg.type) {
                    case C.MSG_HELLO:
                        clientLanguage = msg.hello.clientLanguage;
                        C.log('ws-server', `HELLO from ${clientLanguage}, session=${sessionId}, time=${C.nowMs()}`);
                        ws.send(C.encodeBonjour(C.SERVER_LANG));
                        break;

                    case C.MSG_ECHO_REQUEST:
                        C.log('ws-server', `ECHO_REQUEST id=${msg.echoReq.id} meta=${msg.echoReq.meta} data=${msg.echoReq.data}`);
                        ws.send(C.encodeEchoResponse(200, [{
                            idx: C.nowMs(), type: 0,
                            kv: { id: String(msg.echoReq.id), idx: msg.echoReq.data, data: msg.echoReq.data, meta: clientLanguage }
                        }]));
                        break;

                    case C.MSG_KISS_RESPONSE:
                        C.log('ws-server', `KISS_RESPONSE lang=${msg.kissResp.language} enc=${msg.kissResp.encoding} tz=${msg.kissResp.timeZone}`);
                        break;

                    case C.MSG_PONG:
                        lastPongTs = C.nowMs();
                        C.log('ws-server', `PONG ts=${msg.pong.timestampMs}`);
                        break;

                    case C.MSG_RANDOM_NUMBER:
                        C.log('ws-server', `RANDOM_NUMBER id=${msg.random.id} number=${msg.random.number}`);
                        const hash = C.hashNumber(msg.random.number);
                        ws.send(C.encodeHashResponse(msg.random.id, hash));
                        C.log('ws-server', `HASH_RESPONSE id=${msg.random.id} hash=${hash}`);
                        break;

                    case C.MSG_DISCONNECT:
                        C.log('ws-server', `DISCONNECT reason=${msg.disconnect.reason}`);
                        ws.close();
                        break;

                    case C.MSG_ERROR:
                        C.log('ws-server', `ERROR code=${msg.error.code} msg=${msg.error.message}`);
                        break;

                    default:
                        C.log('ws-server', `Unknown message type: 0x${msg.type.toString(16)}`);
                        ws.send(C.encodeError(C.ERR_UNKNOWN_MSG_TYPE, `unknown type 0x${msg.type.toString(16)}`));
                }
            } catch (e) {
                C.log('ws-server', `Decode error: ${e.message}`);
                const unknown = e.message.startsWith('unknown message type');
                ws.send(C.encodeError(unknown ? C.ERR_UNKNOWN_MSG_TYPE : C.ERR_DECODE, e.message));
                if (!unknown) ws.close(1002, 'invalid protocol frame');
            }
        });

        ws.on('close', () => {
            clearInterval(pingTimer);
            clearInterval(timeTimer);
            clearInterval(kissTimer);
            clearInterval(timeoutTimer);
            C.log('ws-server', `[${userId}] session-`);
        });

        ws.on('error', () => {});
    });
}

main();
