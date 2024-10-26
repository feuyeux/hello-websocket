import asyncio
import json
import time
import websockets
import hashlib
import logging
from datetime import datetime

from common import EchoRequest, build_result, EchoResponse, TCP_PORT, HOST

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
    headers = websocket.request_headers
    user_id = headers.get('userId')
    sessions[session_id] = {
        'path': path,
        'user_id': user_id,
        'last_pong': current_timestamp()
    }
    logger.info("%s connect(%s[%s])", session_id, user_id, path)
    try:
        while True:
            await ping(websocket, session_id)
            await send_time(websocket)
            message = await websocket.recv()
            try:
                echo_request = EchoRequest.from_bytes(message)
                logger.info("Received request: %s", echo_request)
                echo_result = build_result(echo_request.data)
                response = EchoResponse(status=0, results=[echo_result])
                await websocket.send(response.to_bytes())
            except:
                message_dict = json.loads(message)
                if message_dict['body']['type'] == 'pong':
                    sessions[session_id]['last_pong'] = current_timestamp()
                elif message_dict['body']['type'] == 'req':
                    await handle_req(websocket, message_dict, session_id)
    except websockets.exceptions.ConnectionClosedOK:
        logger.info("Client [%s] disconnected", session_id)
        del sessions[session_id]
        await websocket.close()

async def handle_req(websocket, message_dict, session_id):
    content = message_dict['body'].get('content')
    if content:
        # 创建 EchoRequest 对象
        echo_request = EchoRequest(message=content, id=int(session_id))

        # 生成 EchoResult
        echo_result = build_result(echo_request.id)

        # Log the received message
        logger.info("Received request from session %s: %s", session_id, content)

        # Prepare the response message
        response_msg = {
            "body": {
                "type": "resp",
                "content": echo_result.kv['data']
            }
        }

        # Send the response back to the client
        await websocket.send(json.dumps(response_msg))
    else:
        logger.warning("Received request with no content from session %s", session_id)


def current_timestamp():
    return int(round(time.time() * 1000))


def str_timestamp():
    return str(current_timestamp())

async def send_time(websocket):
    current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    time_msg = {
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
            "body": {
                "type": "ping"
            }
        }
        await websocket.send(json.dumps(ping_msg))


async def main():
    async with websockets.serve(handle_client, HOST, TCP_PORT):
        try:
            logger.info(f"Hello server started on {TCP_PORT}")
            await asyncio.Future()
        except (asyncio.exceptions.CancelledError,
                websockets.exceptions.ConnectionClosed,
                KeyboardInterrupt) as e:
            pass
        finally:
            logger.info("Hello Websocket server stopped")


if __name__ == "__main__":
    asyncio.run(main())
