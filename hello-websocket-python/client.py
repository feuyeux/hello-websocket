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
    async with websockets.connect("ws://localhost:58789") as websocket:
        session = {
            'client_id': "client_" + str(random.randint(100, 1000)),
        }
        await send_hello(websocket, session)

        while True:
            await send_random_number(websocket, session)
            response = await websocket.recv()
            response_dict = json.loads(response)
            await handle_resp(websocket, response_dict)


async def send_hello(websocket, session):
    hello_msg = {
        "header": {
            "userId": session['client_id'],
            "seq": datetime.now().timestamp()
        },
        "body": {
            "type": "req",
            "content": "hello"
        }
    }
    request = json.dumps(hello_msg)
    logger.info("Sending Hello to server at %s", datetime.now())
    await websocket.send(request)


async def send_random_number(websocket, session):
    random_msg = {
        "header": {
            "userId": session['client_id'],
            "seq": datetime.now().timestamp()
        },
        "body": {
            "type": "req",
            "content": str(random.randint(1, 100))
        }
    }
    request = json.dumps(random_msg)
    logger.info("Sending Random number:%s", request)
    await websocket.send(request)
    await asyncio.sleep(5)


async def handle_resp(websocket, message):
    start_time = time.time()
    resp_type = message['body']['type']
    seq = message['header'].get('seq')
    content = message['body'].get('content')
    if resp_type == 'ping':
        pong_msg = {
            "header": {
                "latency": int((time.time() - start_time) * 1000),
                "seq": seq
            },
            "body": {
                "type": "pong"
            }
        }
        await websocket.send(json.dumps(pong_msg))
    elif resp_type == 'resp':
        if content == 'bonjour':
            logger.info("Received Bonjour from server at %s", datetime.now())
        else:
            logger.info("Received Random hash: %s", message)
    else:
        logger.info("Received timestamp from server: %s", content)


async def main():
    try:
        await connect_to_server()
    except (asyncio.exceptions.CancelledError, websockets.exceptions.ConnectionClosed) as e:
        pass
    finally:
        logger.info("Hello Websocket client stopped")


if __name__ == "__main__":
    asyncio.run(main())
