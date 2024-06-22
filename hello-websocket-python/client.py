import asyncio
import json
import websockets
import logging
import random

logger = logging.getLogger('websocket-server')
logger.setLevel(logging.INFO)
console = logging.StreamHandler()
console.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s [%(levelname)s] - %(message)s')
console.setFormatter(formatter)
logger.addHandler(console)


async def connect_to_server():
    async with websockets.connect("ws://localhost:58789") as websocket:
        await send_hello(websocket)
        await receive_bonjour(websocket)
        await send_time_request(websocket)
        await send_random_number(websocket)


async def send_hello(websocket):
    hello_msg = {"head": {"userId": "client", "seq": 0},
                 "body": {"type": "hello"}}
    await websocket.send(json.dumps(hello_msg))


async def receive_bonjour(websocket):
    response = await websocket.recv()
    response_dict = json.loads(response)
    logger.info("Received from server: %s", response_dict)


async def send_time_request(websocket):
    while True:
        await asyncio.sleep(5)
        time_msg = {"head": {"userId": "client", "seq": 1},
                    "body": {"type": "time"}}
        await websocket.send(json.dumps(time_msg))
        response = await websocket.recv()
        response_dict = json.loads(response)
        logger.info("Server time:  %s", response_dict)


async def send_random_number(websocket):
    while True:
        await asyncio.sleep(5)
        random_num = random.randint(1, 100)
        random_msg = {"head": {"userId": "client", "seq": 2},
                      "body": {"type": "random", "content": random_num}}
        await websocket.send(json.dumps(random_msg))
        response = await websocket.recv()
        response_dict = json.loads(response)
        logger.info("Hash value received:  %s", response_dict)


async def main():
    await connect_to_server()


if __name__ == "__main__":
    asyncio.run(main())
