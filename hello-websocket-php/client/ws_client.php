<?php
/**
 * Hello WebSocket Protocol - PHP Client.
 *
 * Implements the full PROTOCOL.md client lifecycle: connect, HELLO/BONJOUR,
 * ping/pong, time notification, kiss request/response, random/hash, disconnect.
 */

require __DIR__ . '/../vendor/autoload.php';

use Ratchet\Client\Connector;
use Ratchet\RFC6455\Messaging\Frame;
use React\EventLoop\Loop;
use HelloWs\Common\ByteWriter;

function sendBinary($conn, string $data): void {
    $conn->send(new Frame($data, true, Frame::OP_BINARY));
}
use function HelloWs\Common\{now_ms, now_iso, log_msg};
use function HelloWs\Common\{encode_frame, decode_message};
use const HelloWs\Common\{
    PORT, CLIENT_LANG,
    MSG_HELLO, MSG_BONJOUR, MSG_ECHO_RESPONSE,
    MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
    MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
    MSG_DISCONNECT, MSG_ERROR,
    RANDOM_INTERVAL
};

$host = getenv('WS_SERVER') ?: '127.0.0.1';
$port = (int)(getenv('WS_PORT') ?: PORT);

log_msg('ws-client', "Starting PHP WebSocket client [version: 1.0.0]");
$path = getenv('WS_PATH') ?: '/ws';
$url = "ws://$host:$port$path";
log_msg('ws-client', "Connecting to $url");

$loop = Loop::get();
$connected = false;
$maxAttempts = 3;

function tryConnect(string $host, int $port, int $attempt): void
{
    global $loop, $connected, $maxAttempts;
    $path = getenv('WS_PATH') ?: '/ws';
    $url = "ws://$host:$port$path";
    log_msg('ws-client', "Connection attempt $attempt/$maxAttempts to $url");

    $userId = 'php-client-' . now_ms();
    $active = true;

    $connector = new Connector($loop);

    $connector($url, [], ['userId' => $userId])
        ->then(function ($conn) use (&$active, &$connected, $loop, $host, $port, $attempt, $maxAttempts) {
            $connected = true;
            log_msg('ws-client', "Connected");

            // Send HELLO
            $w = new ByteWriter();
            $w->writeString(\HelloWs\Common\CLIENT_LANG);
            sendBinary($conn, encode_frame(\HelloWs\Common\MSG_HELLO, $w->data()));

            // Background: RANDOM_NUMBER every 5s
            $randomId = 1;
            $randomTimer = $loop->addPeriodicTimer(RANDOM_INTERVAL, function () use ($conn, &$active, &$randomId) {
                if (!$active) return;
                try {
                    $num = random_int(PHP_INT_MIN, PHP_INT_MAX);
                    $w = new ByteWriter();
                    $w->writeI64($randomId);
                    $w->writeI64($num);
                    sendBinary($conn, encode_frame(\HelloWs\Common\MSG_RANDOM_NUMBER, $w->data()));
                    log_msg('ws-client', "RANDOM_NUMBER id=$randomId number=$num");
                    $randomId++;
                } catch (\Exception $e) {
                    $active = false;
                }
            });

            // Receive handler
            $conn->on('message', function ($msg) use ($conn, &$active, $loop, $randomTimer) {
                $data = (string)$msg;
                try {
                    $msgObj = decode_message($data);
                } catch (\Exception $e) {
                    log_msg('ws-client', "Decode error: " . $e->getMessage());
                    return;
                }

                switch ($msgObj->type) {
                    case MSG_BONJOUR:
                        log_msg('ws-client', "BONJOUR server_language={$msgObj->serverLanguage}");
                        break;

                    case MSG_PING:
                        $w = new ByteWriter();
                        $w->writeI64($msgObj->timestampMs);
                        sendBinary($conn, encode_frame(\HelloWs\Common\MSG_PONG, $w->data()));
                        break;

                    case MSG_TIME_NOTIFICATION:
                        log_msg('ws-client', "TIME_NOTIFICATION ts={$msgObj->timestampMs} iso={$msgObj->iso8601}");
                        break;

                    case MSG_KISS_REQUEST:
                        log_msg('ws-client', "KISS_REQUEST os={$msgObj->osName} ver={$msgObj->osVersion} rel={$msgObj->osRelease} arch={$msgObj->osArch}");
                        $w = new ByteWriter();
                        $w->writeString(setlocale(LC_ALL, '0') ?: 'en_US');
                        $w->writeString('UTF-8');
                        $w->writeString(date_default_timezone_get());
                        sendBinary($conn, encode_frame(\HelloWs\Common\MSG_KISS_RESPONSE, $w->data()));
                        break;

                    case MSG_ECHO_RESPONSE:
                        log_msg('ws-client', "ECHO_RESPONSE status={$msgObj->echoStatus} results=" . count($msgObj->echoResults ?? []));
                        break;

                    case MSG_HASH_RESPONSE:
                        log_msg('ws-client', "HASH_RESPONSE id={$msgObj->randomId} hash={$msgObj->hashHex}");
                        break;

                    case MSG_ERROR:
                        log_msg('ws-client', "ERROR code={$msgObj->errorCode} msg={$msgObj->errorMessage}");
                        break;

                    case MSG_DISCONNECT:
                        log_msg('ws-client', "DISCONNECT reason={$msgObj->disconnectReason}");
                        $active = false;
                        $loop->cancelTimer($randomTimer);
                        $conn->close();
                        break;

                    default:
                        $hex = sprintf('0x%02x', $msgObj->type);
                        log_msg('ws-client', "Unknown message type: $hex");
                        break;
                }
            });

            $conn->on('close', function () use (&$active, &$connected, $loop, $randomTimer, $host, $port, $attempt, $maxAttempts) {
                if ($active) {
                    log_msg('ws-client', "Disconnected");
                }
                $active = false;
                $connected = false;
                try { $loop->cancelTimer($randomTimer); } catch (\Exception $e) {}
                // Schedule retry if we haven't exceeded max attempts
                if ($attempt < $maxAttempts) {
                    $loop->addTimer(2.0, function () use ($host, $port, $attempt) {
                        tryConnect($host, $port, $attempt + 1);
                    });
                } else {
                    log_msg('ws-client', "Failed to connect after $maxAttempts attempts");
                    $loop->stop();
                }
            });

            $conn->on('error', function ($e) use (&$active, $loop, $randomTimer) {
                log_msg('ws-client', "Exception: " . $e->getMessage());
                $active = false;
                try { $loop->cancelTimer($randomTimer); } catch (\Exception $ex) {}
            });
        })
        ->otherwise(function ($e) use ($attempt, $maxAttempts, $loop, $host, $port) {
            log_msg('ws-client', "Error: " . $e->getMessage());
            if ($attempt < $maxAttempts) {
                $loop->addTimer(2.0, function () use ($host, $port, $attempt) {
                    tryConnect($host, $port, $attempt + 1);
                });
            } else {
                log_msg('ws-client', "Failed to connect after $maxAttempts attempts");
                $loop->stop();
            }
        });
}

tryConnect($host, $port, 1);

$loop->run();
