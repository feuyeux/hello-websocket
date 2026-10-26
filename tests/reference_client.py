#!/usr/bin/env python3
import asyncio
import hashlib
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "hello-websocket-python"))

import websockets
from common.codec import (
    Bonjour, EchoRequest, HashResponse, Hello, Ping, Pong, RandomNumber,
    MSG_BONJOUR, MSG_ECHO_RESPONSE, MSG_HASH_RESPONSE, MSG_PING,
    decode_message,
)


async def main() -> None:
    host = os.environ.get("WS_SERVER", "127.0.0.1")
    port = int(os.environ.get("WS_PORT", "9898"))
    path = os.environ.get("WS_PATH", "/ws")
    async with websockets.connect(f"ws://{host}:{port}{path}", max_size=1024 * 1024) as ws:
        await ws.send(Hello(client_language="REFERENCE").encode())
        await ws.send(EchoRequest(id=7, meta="REFERENCE", data="你好").encode())
        await ws.send(RandomNumber(id=9, number=-1).encode())

        seen = set()
        for _ in range(20):
            raw = await asyncio.wait_for(ws.recv(), timeout=2)
            if isinstance(raw, str):
                continue
            msg = decode_message(raw)
            if msg.type == MSG_BONJOUR:
                seen.add("bonjour")
            elif msg.type == MSG_PING:
                await ws.send(Pong(timestamp_ms=msg.ping.timestamp_ms).encode())
                seen.add("ping")
            elif msg.type == MSG_ECHO_RESPONSE:
                seen.add("echo")
            elif msg.type == MSG_HASH_RESPONSE:
                expected = hashlib.sha256(b"-1").hexdigest()[:10]
                if msg.hash.id != 9 or msg.hash.hash_hex != expected:
                    raise AssertionError(f"bad hash response: {msg.hash}")
                seen.add("hash")
            if seen == {"bonjour", "ping", "echo", "hash"}:
                print("reference conformance passed")
                return
        raise AssertionError(f"missing exchanges: {seen}")


if __name__ == "__main__":
    asyncio.run(main())
