package main

import (
	"encoding/json"
	"flag"
	"github.com/gorilla/websocket"
	hello "hello-ws"
	"log"
	"net/url"
	"os"
	"os/signal"
	"time"
)

var addr = flag.String("addr", "localhost"+":"+"20808", "http service address")

func main() {
	flag.Parse()
	log.SetFlags(0)

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	u := url.URL{Scheme: "ws", Host: *addr, Path: "/echo/me"}
	log.Printf("connecting to %s", u.String())

	c, _, err := websocket.DefaultDialer.Dial(u.String(), nil)
	if err != nil {
		log.Fatal("dial:", err)
	}
	defer c.Close()

	done := make(chan struct{})

	go func() {
		defer close(done)
		for {
			messageType, messageData, err := c.ReadMessage()
			if err != nil {
				log.Println("read:", err)
				break
			}
			// log.Printf("recv: %s", message)
			switch messageType {
			case websocket.TextMessage: //文本数据
				log.Printf("recv: %s", string(messageData))
			case websocket.BinaryMessage: //二进制数据
				var out hello.Outbound
				err = json.Unmarshal(messageData, &out)
				if err != nil {
					log.Println("unmarshal:", err)
				}
				log.Printf("recv: %v", out)
			default:

			}
		}
	}()

	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-done:
			return
		case t := <-ticker.C:
			err = c.WriteMessage(websocket.TextMessage, []byte(t.String()+" Hello:Eric"))
			if err != nil {
				log.Println("write:", err)
				return
			}

			in, _ := json.Marshal(hello.Inbound{Id: 81, Data: "わかった"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
			in, _ = json.Marshal(hello.Inbound{Id: 82, Data: "알았어"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
			in, _ = json.Marshal(hello.Inbound{Id: 44, Data: "Got it"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
			in, _ = json.Marshal(hello.Inbound{Id: 33, Data: "Je l'ai"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
			in, _ = json.Marshal(hello.Inbound{Id: 7, Data: "Понял"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
			in, _ = json.Marshal(hello.Inbound{Id: 30, Data: "Το έπιασα"})
			_ = c.WriteMessage(websocket.BinaryMessage, in)
		case <-interrupt:
			log.Println("interrupt")

			// Cleanly close the connection by sending a close message and then
			// waiting (with timeout) for the server to close the connection.
			err := c.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			if err != nil {
				log.Println("write close:", err)
				return
			}
			select {
			case <-done:
			case <-time.After(time.Second):
			}
			return
		}
	}
}
