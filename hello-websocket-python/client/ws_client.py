#!/usr/bin/env python3
"""Hello WebSocket Client - Python implementation."""

import asyncio
import os
import random
import sys
import uuid
from datetime import timezone

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import websockets
from common.codec import *


async def run_client(host: str, port: int):
    url = f"ws://{host}:{port}"
    user_id = f"python-client-{uuid.uuid4().hex[:8]}"

    log("ws-client", f"Starting Python WebSocket client [version: 1.0.0]")
    log("ws-client", f"Connecting to {url}")

    async with websockets.connect(url, additional_headers={"userId": user_id}) as ws:
        log("ws-client", "Connected")

        # Send HELLO
        hello = Hello(client_language=CLIENT_LANG)
        await ws.send(hello.encode())

        done = asyncio.Event()

        async def random_task():
            rid = 1
            while not done.is_set():
                await asyncio.sleep(RANDOM_INTERVAL)
                num = random.randint(0, 2**63 - 1)
                rn = RandomNumber(id=rid, number=num)
                await ws.send(rn.encode())
                log("ws-client", f"RANDOM_NUMBER id={rid} number={num}")
                rid += 1

        random_t = asyncio.create_task(random_task())

        try:
            async for raw in ws:
                if isinstance(raw, str):
                    continue
                try:
                    msg = decode_message(raw)
                except Exception as e:
                    log("ws-client", f"Decode error: {e}")
                    continue

                if msg.type == MSG_BONJOUR:
                    log("ws-client", f"BONJOUR server_language={msg.bonjour.server_language}")

                elif msg.type == MSG_PING:
                    log("ws-client", f"PING ts={msg.ping.timestamp_ms}")
                    pong = Pong(timestamp_ms=msg.ping.timestamp_ms)
                    await ws.send(pong.encode())
                    log("ws-client", f"PONG ts={msg.pong.timestamp_ms}")

                elif msg.type == MSG_TIME_NOTIFICATION:
                    log("ws-client", f"TIME_NOTIFICATION ts={msg.time_notif.timestamp_ms} iso={msg.time_notif.iso8601}")

                elif msg.type == MSG_KISS_REQUEST:
                    kr = msg.kiss_req
                    log("ws-client", f"KISS_REQUEST os={kr.os_name} ver={kr.os_version} rel={kr.os_release} arch={kr.os_architecture}")
                    resp = KissResponse(
                        language="en_US",
                        encoding="UTF-8",
                        time_zone="UTC")
                    await ws.send(resp.encode())
                    log("ws-client", f"KISS_RESPONSE lang={resp.language} enc={resp.encoding} tz={resp.time_zone}")

                elif msg.type == MSG_ECHO_RESPONSE:
                    er = msg.echo_resp
                    log("ws-client", f"ECHO_RESPONSE status={er.status} results={len(er.results)}")
                    for i, r in enumerate(er.results):
                        log("ws-client", f"  Result #{i+1}: idx={r.idx} type={r.type} kv={r.kv}")

                elif msg.type == MSG_HASH_RESPONSE:
                    log("ws-client", f"HASH_RESPONSE id={msg.hash.id} hash={msg.hash.hash_hex}")

                elif msg.type == MSG_ERROR:
                    log("ws-client", f"ERROR code={msg.error.code} msg={msg.error.message}")

                else:
                    log("ws-client", f"Unknown message type: 0x{msg.type:02x}")

        finally:
            done.set()
            random_t.cancel()
            disconnect = Disconnect(reason="client shutdown")
            try:
                await ws.send(disconnect.encode())
            except Exception:
                pass


async def main():
    host = os.environ.get("WS_SERVER", "127.0.0.1")
    port = int(os.environ.get("WS_PORT", PORT))

    for attempt in range(1, 4):
        log("ws-client", f"Connection attempt {attempt}/3 to ws://{host}:{port}")
        try:
            await run_client(host, port)
            return
        except Exception as e:
            log("ws-client", f"Error: {e}")
            if attempt < 3:
                await asyncio.sleep(2)

    log("ws-client", "Failed to connect after 3 attempts")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log("ws-client", "Shutting down...")
