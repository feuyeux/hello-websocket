import asyncio
import websockets
import hashlib
import random
import time


async def send_hello(websocket):
    """建立连接后立即发送hello"""
    await websocket.send("hello")


async def send_random_number(websocket):
    while True:
        await asyncio.sleep(10)
        number = random.randint(1, 100)
        print(f"Sending random number: {number}")
        await websocket.send(str(number))


async def receive_and_log(websocket, path):
    sequence = 0
    while True:
        try:
            message = await websocket.recv()
            header = websocket.response_headers
            if "sequence" in header:
                sequence = int(header["sequence"])
                print(f"Received message (seq: {sequence}): {message}")
                if message == "bonjour":
                    print("Server responded with bonjour!")
                elif message.startswith("pong"):
                    print("Pong received!")
                elif message.isdigit():
                    number = int(message)
                    print(f"Hash of received number {number}: {hashlib.sha256(str(number).encode()).hexdigest()}")
                else:
                    print(f"Unexpected message: {message}")
        except websockets.exceptions.ConnectionClosedError:
            print("The WebSocket connection is closed.")
            break


async def main():
    uri = "ws://localhost:8765"
    async with websockets.connect(uri) as websocket:
        # 建立连接后发送hello
        await send_hello(websocket)
        send_task = asyncio.create_task(send_random_number(websocket))
        receive_task = asyncio.create_task(receive_and_log(websocket, ""))

        done, pending = await asyncio.wait(
            [send_task, receive_task],
            return_when=asyncio.FIRST_COMPLETED
        )
        for task in pending:
            task.cancel()


if __name__ == "__main__":
    asyncio.run(main())