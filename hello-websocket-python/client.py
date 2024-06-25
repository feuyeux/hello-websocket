import asyncio
import json
from datetime import datetime

import websockets
import logging
import random
import time

logger = logging.getLogger('websocket-server')
logger.setLevel(logging.INFO)
console = logging.StreamHandler()
console.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s [%(levelname)s] - %(message)s')
console.setFormatter(formatter)
logger.addHandler(console)


async def connect_to_server():
    session = {
        'client_id': "client_" + str(random.randint(100, 1000)),
    }

    hello_headers = [
        ("userId", session['client_id']),
    ]

    async with websockets.connect("ws://localhost:58789", extra_headers=hello_headers) as websocket:

        try:
            await asyncio.wait_for(send_hello(websocket, session), timeout=3)
        except asyncio.TimeoutError:
            logger.error('Timeout, no bonjour received, client canceled')
            return
        while True:
            response = await websocket.recv()
            response_dict = json.loads(response)
            await handle_resp(websocket, response_dict)
            await send_random_number(websocket, session)


async def send_hello(websocket, session):
    hello_msg = {
        "body": {
            "type": "req",
            "content": "hello"
        }
    }
    request = json.dumps(hello_msg)
    logger.info("<< Hello")
    await websocket.send(request)


async def send_random_number(websocket, session):
    seq = int(round(time.time() * 1000))
    number = str(random.randint(1, 100))
    random_msg = {
        "body": {
            "type": "req",
            "content": number
        }
    }
    request = json.dumps(random_msg)
    logger.info("<< random, seq:%s number:%s", seq, number)
    await websocket.send(request)
    await asyncio.sleep(5)


async def handle_resp(websocket, message):
    resp_type = message['body']['type']
    content = message['body'].get('content')
    #
    if resp_type == 'ping':
        pong_msg = {
            "body": {
                "type": "pong"
            }
        }
        await websocket.send(json.dumps(pong_msg))
    elif resp_type == 'resp':
        if content == 'bonjour':
            logger.info(">> Bonjour")
        else:
            logger.info(">> random, hash: %s",  content)
    else:
        logger.info(">> timestamp: %s", content)


async def main():
    try:
        await connect_to_server()
    except (asyncio.exceptions.CancelledError, websockets.exceptions.ConnectionClosed) as e:
        pass
    finally:
        logger.info("Hello Websocket client stopped")


if __name__ == "__main__":
    asyncio.run(main())
