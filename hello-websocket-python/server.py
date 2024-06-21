import asyncio
import websockets
import time

active_connections = set()


async def broadcast_time():
    """定期向所有活跃连接广播当前时间"""
    while True:
        current_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())
        for websocket in active_connections.copy():
            try:
                await websocket.send(current_time)
            except websockets.exceptions.ConnectionClosedError:
                # 如果连接已关闭，则从集合中移除
                active_connections.remove(websocket)
        await asyncio.sleep(5)  # 每5秒广播一次


async def pingpong(websocket, path):
    """处理每个连接的消息"""
    session_id = id(websocket)
    active_connections.add(websocket)

    try:
        async for message in websocket:
            if message == "hello":
                await websocket.send("bonjour")
            elif message == "ping":
                print(f"Received ping from session {session_id}")
                await websocket.send("pong")
            else:
                print(f"Unknown message: {message} from session {session_id}")
    finally:
        active_connections.remove(websocket)


async def main():
    """主入口点，启动WebSocket服务器和时间广播任务"""
    start_server = websockets.serve(pingpong, "localhost", 8765)
    await start_server
    time_broadcast_task = asyncio.create_task(broadcast_time())

    await time_broadcast_task  # 等待直到时间广播任务结束（实际上不会，因为它一直在循环）


if __name__ == "__main__":
    asyncio.run(main())