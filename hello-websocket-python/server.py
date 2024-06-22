import asyncio
import json
import time
import websockets
import hashlib
import logging
from datetime import datetime

# 创建一个logger
logger = logging.getLogger('websocket-server')
logger.setLevel(logging.INFO)
console = logging.StreamHandler()
console.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s [%(levelname)s] - %(message)s')
console.setFormatter(formatter)
logger.addHandler(console)
sessions = {}


async def handle_client(websocket, path):
    session_id = id(websocket)
    sessions[session_id] = {'last_ping': time.time(), 'seq': 0}

    try:
        await ping_pong(websocket, session_id)
        while True:
            message = await websocket.recv()
            message_dict = json.loads(message)
            if message_dict['body']['type'] == 'req':
                await handle_req(websocket, message_dict, session_id)
    except websockets.exceptions.ConnectionClosedOK:
        logger.info("Client [%s] disconnected", session_id)
        del sessions[session_id]


async def ping_pong(websocket, session_id):
    while True:
        start_time = time.time()
        ping_msg = {"head": {"userId": "server", "seq": sessions[session_id]['seq']},
                    "body": {"type": "ping"}}
        await websocket.send(json.dumps(ping_msg))
        sessions[session_id]['seq'] += 1
        await asyncio.sleep(1)
        latency = int((time.time() - start_time) * 1000)
        pong_msg = {"head": {"latency": latency, "seq": sessions[session_id]['seq']},
                    "body": {"type": "pong"}}
        await websocket.send(json.dumps(pong_msg))
        sessions[session_id]['seq'] += 1
        logger.info("Pong sent to %s with latency %dms", session_id, latency)


async def handle_req(websocket, message, session_id):
    req_type = message['body']['type']
    content = message['body'].get('content')
    if req_type == 'hello':
        logger.info("Hello request from %s at %s", session_id, datetime.now())
        bonjour_msg = {"head": {"latency": 0, "seq": sessions[session_id]['seq']},
                       "body": {"type": "bonjour", "content": "Welcome"}}
        await websocket.send(json.dumps(bonjour_msg))
        sessions[session_id]['seq'] += 1
    elif req_type == 'time':
        current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        time_msg = {"head": {"latency": 0, "seq": sessions[session_id]['seq']},
                    "body": {"type": "time", "content": current_time}}
        await websocket.send(json.dumps(time_msg))
        sessions[session_id]['seq'] += 1
    elif req_type == 'random':
        random_num = content
        hash_value = hashlib.sha256(str(random_num).encode()).hexdigest()
        logger.info("Received random number %s from %s", random_num, session_id)
        hash_msg = {"head": {"latency": 0, "seq": sessions[session_id]['seq']},
                    "body": {"type": "hash", "content": hash_value}}
        await websocket.send(json.dumps(hash_msg))
        sessions[session_id]['seq'] += 1


async def main():
    async with websockets.serve(handle_client, "localhost", 58789):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
