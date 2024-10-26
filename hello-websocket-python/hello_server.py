import asyncio
import json
import time
from datetime import datetime

import websockets

from hello_common import build_kiss_request, build_result, setup_logger, timestamp
from hello_protocol import EchoRequest, EchoResponse, TCP_PORT, HOST

logger = setup_logger("websocket-server")
sessions = {}


async def handle_client(websocket, path):
    session_id = id(websocket)
    headers = websocket.request_headers
    client_id = headers.get("client_id", "unknown")
    sessions[session_id] = {
        "path": path,
        "client_id": client_id,
        "last_pong": timestamp(),
    }
    logger.info("sessions[%s]<-|%s|<-%s", session_id, path, client_id)
    try:
        while True:
            await ping(websocket, session_id)
            await handle_req(websocket, session_id)
            await send_req(websocket)

    except websockets.exceptions.ConnectionClosedOK:
        logger.info("Client [%s] disconnected", session_id)
        del sessions[session_id]
        await websocket.close()


async def handle_req(websocket, session_id):
    message = await websocket.recv()
    try:
        echo_request = EchoRequest.from_bytes(message)
        logger.info("Received echo: %s", echo_request)
        echo_result = build_result(echo_request.data)
        echo_response = EchoResponse(status=0, results=[echo_result])
        await websocket.send(echo_response.to_bytes())
    except:
        message_dict = json.loads(message)
        type = message_dict["body"]["type"]
        if type == "pong":
            sessions[session_id]["last_pong"] = timestamp()
        elif type == "kiss":
            kiss_response = message_dict["body"].get("content")
            logger.info("Received kiss %s: %s", session_id, kiss_response)
        else:
            logger.warning("Received request %s: %s", session_id, message_dict)


async def send_req(websocket):
    kiss_request = build_kiss_request()
    kiss_msg = {"body": {"type": "kiss", "content": kiss_request.to_dict()}}
    await websocket.send(json.dumps(kiss_msg))
    await asyncio.sleep(5)


async def ping(websocket, session_id):
    await asyncio.sleep(10)
    now = time.time()
    if now - sessions[session_id]["last_pong"] > 60 * 1000:
        logger.info(
            "Client [%s] is not responding(now:%d, last pong:%d), closing connection",
            session_id,
            sessions[session_id]["last_pong"],
        )
        del sessions[session_id]
        await websocket.close()
    else:
        ping_msg = {"body": {"type": "ping"}}
        await websocket.send(json.dumps(ping_msg))


async def main():
    async with websockets.serve(handle_client, HOST, TCP_PORT):
        try:
            logger.info(f"Hello server started on {TCP_PORT}")
            await asyncio.Future()
        except (
            asyncio.exceptions.CancelledError,
            websockets.exceptions.ConnectionClosed,
            KeyboardInterrupt,
        ) as e:
            pass
        finally:
            logger.info("Hello Websocket server stopped")


if __name__ == "__main__":
    asyncio.run(main())
