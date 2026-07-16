#!/usr/bin/env python3
"""Hello WebSocket Server - Python implementation."""

import asyncio
import os
import platform
import sys
import uuid

# Add parent dir to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import websockets
from common.codec import *


async def handle_client(websocket):
    user_id = websocket.request_headers.get("userId", str(uuid.uuid4())) if hasattr(websocket, "request_headers") else str(uuid.uuid4())
    session_id = str(uuid.uuid4())
    connected_at = now_ms()
    last_pong_ts = connected_at
    client_language = "unknown"

    log("ws-server", f"[{user_id}] session+")
    tasks = []

    async def send_msg(msg_bytes: bytes):
        try:
            await websocket.send(msg_bytes)
        except Exception:
            pass

    async def ping_task():
        while True:
            await asyncio.sleep(PING_INTERVAL)
            ping = Ping(timestamp_ms=now_ms())
            await send_msg(ping.encode())

    async def time_task():
        while True:
            await asyncio.sleep(TIME_INTERVAL)
            tn = TimeNotification(timestamp_ms=now_ms(), iso8601=now_iso())
            await send_msg(tn.encode())

    async def kiss_task():
        while True:
            await asyncio.sleep(KISS_INTERVAL)
            kr = KissRequest(
                os_name=platform.system(),
                os_version=platform.version(),
                os_release=platform.release(),
                os_architecture=platform.machine())
            await send_msg(kr.encode())

    async def timeout_task():
        nonlocal last_pong_ts
        while True:
            await asyncio.sleep(5)
            if now_ms() - last_pong_ts > int(SESSION_TIMEOUT * 1000):
                log("ws-server", f"[{user_id}] session timeout")
                await websocket.close()
                return

    tasks = [asyncio.create_task(t()) for t in [ping_task, time_task, kiss_task, timeout_task]]

    try:
        async for raw in websocket:
            if isinstance(raw, str):
                continue
            try:
                msg = decode_message(raw)
            except Exception as e:
                log("ws-server", f"Decode error: {e}")
                unknown = str(e).startswith("unknown message type")
                err = ErrorMsg(code=ERR_UNKNOWN_MSG_TYPE if unknown else ERR_DECODE, message=str(e))
                await send_msg(err.encode())
                if not unknown:
                    await websocket.close(code=1002, reason="invalid protocol frame")
                    break
                continue

            if msg.type == MSG_HELLO:
                client_language = msg.hello.client_language
                log("ws-server", f"HELLO from {client_language}, session={session_id}, time={now_ms()}")
                bonjour = Bonjour(server_language=SERVER_LANG)
                await send_msg(bonjour.encode())

            elif msg.type == MSG_ECHO_REQUEST:
                req = msg.echo_req
                log("ws-server", f"ECHO_REQUEST id={req.id} meta={req.meta} data={req.data}")
                result = EchoResult(idx=now_ms(), type=0, kv={
                    "id": str(req.id), "idx": req.data, "data": req.data, "meta": client_language})
                resp = EchoResponse(status=200, results=[result])
                await send_msg(resp.encode())

            elif msg.type == MSG_KISS_RESPONSE:
                kr = msg.kiss_resp
                log("ws-server", f"KISS_RESPONSE lang={kr.language} enc={kr.encoding} tz={kr.time_zone}")

            elif msg.type == MSG_PONG:
                last_pong_ts = now_ms()
                log("ws-server", f"PONG ts={msg.pong.timestamp_ms}")

            elif msg.type == MSG_RANDOM_NUMBER:
                rn = msg.random
                log("ws-server", f"RANDOM_NUMBER id={rn.id} number={rn.number}")
                h = hash_number(rn.number)
                resp = HashResponse(id=rn.id, hash_hex=h)
                await send_msg(resp.encode())
                log("ws-server", f"HASH_RESPONSE id={rn.id} hash={h}")

            elif msg.type == MSG_DISCONNECT:
                log("ws-server", f"DISCONNECT reason={msg.disconnect.reason}")
                break

            elif msg.type == MSG_ERROR:
                log("ws-server", f"ERROR code={msg.error.code} msg={msg.error.message}")

            else:
                log("ws-server", f"Unknown message type: 0x{msg.type:02x}")
                err = ErrorMsg(code=ERR_UNKNOWN_MSG_TYPE, message=f"unknown type 0x{msg.type:02x}")
                await send_msg(err.encode())

    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        for t in tasks:
            t.cancel()
        log("ws-server", f"[{user_id}] session-")


async def main():
    port = int(os.environ.get("WS_PORT", PORT))
    log("ws-server", f"Starting Python WebSocket server on port {port}")

    async with websockets.serve(handle_client, "0.0.0.0", port, max_size=2**20):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log("ws-server", "Shutting down...")
