package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"hello-websocket/common"

	"log"
	"net/http"
	"time"

	"github.com/gorilla/mux"
	"github.com/gorilla/websocket"
)

var addr = flag.String("addr", "localhost:2088", "http service address")

var upgrader = websocket.Upgrader{} // use default options

func echoHandler(w http.ResponseWriter, request *http.Request) {
	c, err := upgrader.Upgrade(w, request, nil)
	if err != nil {
		log.Print("upgrade:", err)
		return
	}
	defer c.Close()
	for {
		vars := mux.Vars(request)
		name := vars["name"]
		messageType, messageData, err := c.ReadMessage()
		if err != nil {
			log.Println("read:", err)
			break
		}
		// log.Printf("recv: %s", message)
		switch messageType {
		case websocket.TextMessage: //文本数据
			fmt.Println(string(messageData), name)
			err = c.WriteMessage(websocket.TextMessage, messageData)
			if err != nil {
				log.Println("write:", err)
			}
		case websocket.BinaryMessage: //二进制数据
			start := time.Now()
			var in common.Inbound
			err = json.Unmarshal(messageData, &in)
			if err != nil {
				log.Println("unmarshal:", err)
			}
			log.Printf("recv: %v", in)
			out, err := json.Marshal(common.Outbound{Id: in.Id, Data: in.Data, Elapse: time.Since(start).Milliseconds()})
			if err != nil {
				log.Println("marshal:", err)
			}
			err = c.WriteMessage(websocket.BinaryMessage, out)
			if err != nil {
				log.Println("write:", err)
			}
		case websocket.CloseMessage: //关闭
		case websocket.PingMessage: //Ping
		case websocket.PongMessage: //Pong
		default:

		}
	}
}

func main() {
	flag.Parse()
	log.SetFlags(0)
	router := mux.NewRouter()
	router.HandleFunc("/websocket/{name}", echoHandler)
	log.Fatal(http.ListenAndServe(*addr, router))
}
