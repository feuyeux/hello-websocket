import asyncio
import websockets

async def echo(websocket, path):
    print('echo')
    async for message in websocket:
        print(message,'received from client')
        greeting = f"Hello {message}!"
        await websocket.send(greeting)
        print(f"> {greeting}")

asyncio.get_event_loop().run_until_complete(
    websockets.serve(echo, 'localhost', 8765))
asyncio.get_event_loop().run_forever()