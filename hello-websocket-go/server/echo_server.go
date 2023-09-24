package main

import (
	"fmt"
	"hello-websocket/common"
	"log"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

var conns = sync.Map{}
var names = sync.Map{}

func main() {
	var upgrader = websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
	}
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		clientAddr, clientPort, _ := net.SplitHostPort(r.RemoteAddr)
		websocket, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Println(err)
			return
		}
		key := clientAddr + "-" + clientPort
		conns.Store(key, websocket)
		name := r.Header.Get(common.ClientName)
		names.Store(key, name)
		fmt.Printf("Websocket Connected(%s:%s) X=%s\n", clientAddr, clientPort, name)
		listen(websocket)
	})
	http.ListenAndServe(":8080", nil)
}

func listen(conn *websocket.Conn) {
	for {
		clientAddr, clientPort, _ := net.SplitHostPort(conn.RemoteAddr().String())
		key := clientAddr + "-" + clientPort
		// read a message
		messageType, messageContent, err := conn.ReadMessage()
		if err != nil {
			log.Println("read:", err)
			conns.Delete(key)
			names.Delete(key)
			return
		}
		//printTime(err, messageContent)
		name, _ := names.Load(key)
		fmt.Printf("%s(%s:%s):%s", name, clientAddr, clientPort, messageContent)
		conns.Range(func(k, v interface{}) bool {
			if key != k {
				c := v.(*websocket.Conn)
				messageResponse := fmt.Sprintf("[Golang] [%s]:%s", name, messageContent)
				if err := c.WriteMessage(messageType, []byte(messageResponse)); err != nil {
					log.Println(err)
				}
			}
			return true
		})
	}
}

func printTime(messageContent []byte) {
	timeReceive := time.Now()
	fmt.Printf("%+v\n", timeReceive)
}
