# Hello Websocket for python

```sh
python -m venv ws_env
source ws_env/bin/activate
pip install --upgrade pip
pip install websockets
```

```sh
source ws_env/bin/activate
python server.py
```

```sh
source ws_env/bin/activate
python client.py
```

使用websockets库，实现python websocket的服务端和客户端代码：

1. 两端都支持header读写，一个key为sequence、value为整形的header参数，每次发送请求时sequence加1
2. 客户端建立连接后请求hello，服务端返回bonjour
3. 服务端和客户端实现pingpong
4. 服务端维护session，在建立连接时加入、在pingpong失效或者断开连接时摘除
5. 服务端每5秒发送当前时间给客户端，客户端打印日志
6. 客户端每10秒发送一个随机数给服务端，服务端打印日志并响应这个随机数的hash值给端

将上述功能写入server.py和client.py中，并提供下载链接。