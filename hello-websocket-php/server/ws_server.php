<?php
/**
 * Hello WebSocket Protocol - PHP Server.
 *
 * Implements the full PROTOCOL.md server lifecycle: handshake, background tasks,
 * echo, kiss, ping/pong, time broadcast, random/hash, disconnect.
 */

require __DIR__ . '/../vendor/autoload.php';

use Ratchet\MessageComponentInterface;
use Ratchet\ConnectionInterface;
use Ratchet\Server\IoServer;
use Ratchet\Http\HttpServer;
use Ratchet\WebSocket\WsServer;
use Ratchet\RFC6455\Messaging\Frame;
use React\EventLoop\Loop;
use HelloWs\Common\ByteWriter;

function sendBinary(ConnectionInterface $conn, string $data): void {
    $conn->send(new Frame($data, true, Frame::OP_BINARY));
}
use function HelloWs\Common\{now_ms, now_iso, hash_number, log_msg};
use function HelloWs\Common\{encode_frame, decode_message};
use const HelloWs\Common\{
    PORT, SERVER_LANG,
    MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
    MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
    MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
    MSG_DISCONNECT, MSG_ERROR,
    ERR_DECODE, ERR_UNKNOWN_MSG_TYPE,
    PING_INTERVAL, SESSION_TIMEOUT, TIME_INTERVAL, KISS_INTERVAL
};

class WsServerHandler implements MessageComponentInterface
{
    private \SplObjectStorage $sessions;

    public function __construct()
    {
        $this->sessions = new \SplObjectStorage();
    }

    public function onOpen(ConnectionInterface $conn): void
    {
        $userId = $conn->httpRequest->getHeaderLine('userId') ?: 'php-' . now_ms();
        $sessionId = (string)now_ms();

        $session = [
            'userId' => $userId,
            'sessionId' => $sessionId,
            'clientLanguage' => 'unknown',
            'connectedAt' => now_ms(),
            'lastPongTs' => now_ms(),
            'active' => true,
            'timers' => [],
        ];

        log_msg('ws-server', "[$userId] session+");

        $loop = Loop::get();

        // PING every 1s
        $session['timers'][] = $loop->addPeriodicTimer(PING_INTERVAL, function () use ($conn, &$session) {
            if (!$session['active']) return;
            try {
                $w = new ByteWriter();
                $w->writeI64(now_ms());
                sendBinary($conn, encode_frame(\HelloWs\Common\MSG_PING, $w->data()));
            } catch (\Exception $e) {
                $session['active'] = false;
            }
        });

        // TIME_NOTIFICATION every 5s
        $session['timers'][] = $loop->addPeriodicTimer(TIME_INTERVAL, function () use ($conn, &$session) {
            if (!$session['active']) return;
            try {
                $w = new ByteWriter();
                $w->writeI64(now_ms());
                $w->writeString(now_iso());
                sendBinary($conn, encode_frame(\HelloWs\Common\MSG_TIME_NOTIFICATION, $w->data()));
            } catch (\Exception $e) {
                $session['active'] = false;
            }
        });

        // KISS_REQUEST every 5s
        $session['timers'][] = $loop->addPeriodicTimer(KISS_INTERVAL, function () use ($conn, &$session) {
            if (!$session['active']) return;
            try {
                $w = new ByteWriter();
                $w->writeString(php_uname('s'));
                $w->writeString(php_uname('r'));
                $w->writeString(php_uname('v'));
                $w->writeString(php_uname('m'));
                sendBinary($conn, encode_frame(\HelloWs\Common\MSG_KISS_REQUEST, $w->data()));
            } catch (\Exception $e) {
                $session['active'] = false;
            }
        });

        // Timeout check every 5s
        $session['timers'][] = $loop->addPeriodicTimer(5.0, function () use ($conn, &$session) {
            if (!$session['active']) return;
            if (now_ms() - $session['lastPongTs'] > SESSION_TIMEOUT * 1000) {
                log_msg('ws-server', "[{$session['userId']}] session timeout");
                $session['active'] = false;
                $conn->close();
            }
        });

        $this->sessions->attach($conn, $session);
    }

    public function onMessage(ConnectionInterface $from, $msg): void
    {
        $session = $this->sessions[$from] ?? null;
        if ($session === null) return;

        $data = (string)$msg;
        try {
            $msgObj = decode_message($data);
        } catch (\Exception $e) {
            log_msg('ws-server', "Decode error: " . $e->getMessage());
            $w = new ByteWriter();
            $w->writeI32(ERR_DECODE);
            $w->writeString($e->getMessage());
            try { sendBinary($from, encode_frame(\HelloWs\Common\MSG_ERROR, $w->data())); } catch (\Exception $ex) {}
            $from->close();
            return;
        }

        switch ($msgObj->type) {
            case MSG_HELLO:
                $session['clientLanguage'] = $msgObj->clientLanguage;
                log_msg('ws-server', "HELLO from {$msgObj->clientLanguage}, session={$session['sessionId']}, time=" . now_ms());
                $this->sessions->attach($from, $session);
                $w = new ByteWriter();
                $w->writeString(SERVER_LANG);
                sendBinary($from, encode_frame(MSG_BONJOUR, $w->data()));
                break;

            case MSG_ECHO_REQUEST:
                log_msg('ws-server', "ECHO_REQUEST id={$msgObj->echoId} meta={$msgObj->echoMeta} data={$msgObj->echoData}");
                $w = new ByteWriter();
                $w->writeI32(200);
                $w->writeU32(1);
                $w->writeI64(now_ms());
                $w->writeU8(0);
                $w->writeKv([
                    'id' => (string)$msgObj->echoId,
                    'data' => $msgObj->echoData ?? '',
                    'meta' => $msgObj->echoMeta ?? '',
                    'lang' => $session['clientLanguage'],
                ]);
                sendBinary($from, encode_frame(MSG_ECHO_RESPONSE, $w->data()));
                break;

            case MSG_KISS_RESPONSE:
                log_msg('ws-server', "KISS_RESPONSE lang={$msgObj->kissLanguage} enc={$msgObj->kissEncoding} tz={$msgObj->kissTimeZone}");
                break;

            case MSG_PONG:
                $session['lastPongTs'] = $msgObj->timestampMs;
                $this->sessions->attach($from, $session);
                break;

            case MSG_RANDOM_NUMBER:
                log_msg('ws-server', "RANDOM_NUMBER id={$msgObj->randomId} number={$msgObj->randomNumber}");
                $hash = hash_number($msgObj->randomNumber);
                $w = new ByteWriter();
                $w->writeI64($msgObj->randomId);
                $w->writeString($hash);
                sendBinary($from, encode_frame(MSG_HASH_RESPONSE, $w->data()));
                break;

            case MSG_DISCONNECT:
                log_msg('ws-server', "DISCONNECT reason={$msgObj->disconnectReason}");
                $session['active'] = false;
                $this->sessions->attach($from, $session);
                $from->close();
                break;

            case MSG_ERROR:
                log_msg('ws-server', "ERROR code={$msgObj->errorCode} msg={$msgObj->errorMessage}");
                break;

            default:
                $hex = sprintf('0x%02x', $msgObj->type);
                log_msg('ws-server', "Unknown message type: $hex");
                $w = new ByteWriter();
                $w->writeI32(ERR_UNKNOWN_MSG_TYPE);
                $w->writeString("unknown type $hex");
                sendBinary($from, encode_frame(MSG_ERROR, $w->data()));
                break;
        }
    }

    public function onClose(ConnectionInterface $conn): void
    {
        $session = $this->sessions[$conn] ?? null;
        if ($session) {
            $session['active'] = false;
            $loop = Loop::get();
            foreach ($session['timers'] as $timer) {
                $loop->cancelTimer($timer);
            }
            log_msg('ws-server', "[{$session['userId']}] session-");
            $this->sessions->detach($conn);
        }
    }

    public function onError(ConnectionInterface $conn, \Exception $e): void
    {
        log_msg('ws-server', "Error: " . $e->getMessage());
        $conn->close();
    }
}

$port = (int)(getenv('WS_PORT') ?: PORT);
log_msg('ws-server', "Starting PHP WebSocket server on port $port");

$server = IoServer::factory(
    new HttpServer(
        new WsServer(
            new WsServerHandler()
        )
    ),
    $port,
    '0.0.0.0'
);

$server->run();
