import asyncio
import json
import random

import websockets

from hello_common import build_kiss_response, build_link_requests, setup_logger
from hello_protocol import EchoRequest, EchoResponse, TCP_PORT, HOST

logger = setup_logger("websocket-client")


async def connect_to_server():
    hello_server_uri = f"ws://{HOST}:{TCP_PORT}"
    hello_headers = [
        ("client_id", "python_client_" + str(random.randint(100, 1000))),
    ]

    async with websockets.connect(
        hello_server_uri, extra_headers=hello_headers
    ) as websocket:
        try:
            logger.info("connected to %s", hello_server_uri)
            await asyncio.wait_for(send_hello(websocket), timeout=3)
        except asyncio.TimeoutError:
            logger.error("Timeout, no bonjour received, client canceled")
            return
        while True:
            message = await websocket.recv()
            await handle_resp(websocket, message)


async def handle_resp(websocket, message):
    try:
        echo_response = EchoResponse.from_bytes(message)
        logger.info("Received echo: %s", echo_response)
    except:
        message_dict = json.loads(message)
        resp_type = message_dict["body"]["type"]
        if resp_type == "ping":
            pong_msg = {"body": {"type": "pong"}}
            await websocket.send(json.dumps(pong_msg))
        elif resp_type == "kiss":
            kiss_request = message_dict["body"].get("content")
            logger.info("Received kiss: %s", kiss_request)
            response_dict = build_kiss_response().to_dict()
            kiss_response = {"body": {"type": "kiss", "content": response_dict}}
            await websocket.send(json.dumps(kiss_response))


async def send_hello(websocket):
    if websocket.open:
        for echo_request in build_link_requests():
            await websocket.send(echo_request.to_bytes())


async def main():
    try:
        await connect_to_server()
    except (
        asyncio.exceptions.CancelledError,
        websockets.exceptions.ConnectionClosed,
    ) as e:
        pass
    finally:
        logger.info("Hello Websocket client stopped")


if __name__ == "__main__":
    asyncio.run(main())
