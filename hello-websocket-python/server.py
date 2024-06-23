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
    sessions[session_id] = {
        'path': path,
        'last_pong': current_timestamp()
    }
    logger.info("%s connect(%s)", session_id, path)
    try:
        while True:
            await ping(websocket, session_id)
            await send_time(websocket)
            message = await websocket.recv()
            message_dict = json.loads(message)
            if message_dict['body']['type'] == 'pong':
                sessions[session_id]['last_pong'] = current_timestamp()
            elif message_dict['body']['type'] == 'req':
                await handle_req(websocket, message_dict, session_id)
    except websockets.exceptions.ConnectionClosedOK:
        logger.info("Client [%s] disconnected", session_id)
        del sessions[session_id]
        await websocket.close()


def current_timestamp():
    return int(round(time.time() * 1000))


def str_timestamp():
    return str(current_timestamp())


async def handle_req(websocket, message, session_id):
    start_time = time.time()
    content = message['body'].get('content')
    user_id = message['header'].get('userId')
    seq = message['header'].get('seq')
    if content == 'hello':
        logger.info("%s[%s] << Hello", session_id, user_id)
        bonjour_msg = {
            "header": {
                "latency": int((time.time() - start_time) * 1000),
                "seq": seq
            },
            "body": {
                "type": "resp",
                "content": "bonjour"
            }
        }
        response = json.dumps(bonjour_msg)
        await websocket.send(response)
    else:
        hash_value = hashlib.sha256(content.encode()).hexdigest()
        logger.info("%s[%s] >> random: seq:%s,number:%s",
                    session_id, user_id, seq, content)
        latency = int((time.time() - start_time) * 1000)
        hash_msg = {
            "header": {
                "latency": latency,
                "seq": seq
            },
            "body": {
                "type": "resp",
                "content": hash_value
            }
        }
        response = json.dumps(hash_msg)
        logger.info("%s[%s] << random, seq:%s, hash: %s %dms", session_id, user_id, seq, hash_value, latency)
        await websocket.send(response)


async def send_time(websocket):
    current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    time_msg = {
        "header": {
            "userId": "server",
            "seq": str_timestamp()
        },
        "body": {
            "type": "req",
            "content": current_time
        }
    }
    request = json.dumps(time_msg)
    await websocket.send(request)
    await asyncio.sleep(5)


async def ping(websocket, session_id):
    await asyncio.sleep(10)
    now = time.time()
    if now - sessions[session_id]['last_pong'] > 60 * 1000:
        logger.info("Client [%s] is not responding(now:%d, last pong:%d), closing connection",
                    session_id, sessions[session_id]['last_pong'])
        del sessions[session_id]
        await websocket.close()
    else:
        ping_msg = {
            "header": {
                "userId": "server",
                "seq": str_timestamp()
            },
            "body": {
                "type": "ping"
            }
        }
        await websocket.send(json.dumps(ping_msg))


async def main():
    async with websockets.serve(handle_client, "localhost", 58789):
        logger.info("Hello Websocket server started")
        try:
            await asyncio.Future()
        except (asyncio.exceptions.CancelledError,
                websockets.exceptions.ConnectionClosed,
                KeyboardInterrupt) as e:
            pass
        finally:
            logger.info("Hello Websocket server stopped")


if __name__ == "__main__":
    asyncio.run(main())
