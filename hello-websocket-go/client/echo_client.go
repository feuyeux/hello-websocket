package main

import (
	"bufio"
	"fmt"
	"hello-websocket/common"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"time"

	"github.com/gorilla/websocket"
)

var in = bufio.NewReader(os.Stdin)

func getInput(input chan string) {
	result, err := in.ReadString('\n')
	if err != nil {
		log.Println(err)
		return
	}
	input <- result
}

func main() {
	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	input := make(chan string, 1)
	go getInput(input)
	host := "localhost:8080"
	URL := url.URL{Scheme: "ws", Host: host}
	firstArg := os.Args[1]
	header := http.Header{}
	header.Set(common.ClientName, firstArg)
	c, _, err := websocket.DefaultDialer.Dial(URL.String(), header)
	if err != nil {
		log.Println("Error:", err)
		return
	}
	defer func(c *websocket.Conn) {
		_ = c.Close()
	}(c)

	done := make(chan struct{})
	go func() {
		defer close(done)
		for {
			_, message, err := c.ReadMessage()
			if err != nil {
				log.Println("ReadMessage() error:", err)
				return
			}
			fmt.Printf("%s", message)
		}
	}()

	for {
		select {
		case <-done:
			return
		case t := <-input:
			if len(t) > 1 {
				err := c.WriteMessage(websocket.TextMessage, []byte(t))
				if err != nil {
					log.Println("Write error:", err)
					return
				}
			}
			go getInput(input)
		case <-interrupt:
			log.Println("Caught interrupt signal - quitting!")
			err := c.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))

			if err != nil {
				log.Println("Write close error:", err)
				return
			}
			select {
			case <-done:
			case <-time.After(2 * time.Second):
			}
			return
		}
	}
}
