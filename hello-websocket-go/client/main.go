package main

import (
	"fmt"
	"math/rand"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"hello-websocket-go/common"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

func main() {
	host := os.Getenv("WS_SERVER")
	if host == "" {
		host = "127.0.0.1"
	}
	port := common.Port
	if p := os.Getenv("WS_PORT"); p != "" {
		if v, err := fmt.Sscanf(p, "%d", &port); err != nil || v != 1 {
			port = common.Port
		}
	}

	common.Log("ws-client", fmt.Sprintf("Starting Go WebSocket client [version: 1.0.0]"))
	common.Log("ws-client", fmt.Sprintf("Connecting to ws://%s:%d/ws", host, port))

	url := fmt.Sprintf("ws://%s:%d/ws", host, port)
	header := http.Header{}
	header.Set("userId", "go-client-"+uuid.New().String()[:8])

	conn, _, err := websocket.DefaultDialer.Dial(url, header)
	if err != nil {
		common.Log("ws-client", fmt.Sprintf("Connection error: %v", err))
		os.Exit(1)
	}
	defer conn.Close()

	common.Log("ws-client", "Connected")

	// Send HELLO
	hello := &common.Hello{ClientLanguage: common.ClientLang}
	if err := conn.WriteMessage(websocket.BinaryMessage, hello.Encode()); err != nil {
		common.Log("ws-client", fmt.Sprintf("Send HELLO error: %v", err))
		os.Exit(1)
	}

	// Background task: random number
	done := make(chan struct{})
	go randomTask(conn, done)

	// Graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		common.Log("ws-client", "Shutting down...")
		disconnect := &common.Disconnect{Reason: "client shutdown"}
		conn.WriteMessage(websocket.BinaryMessage, disconnect.Encode())
		close(done)
		conn.Close()
		os.Exit(0)
	}()

	// Receive loop
	for {
		_, data, err := conn.ReadMessage()
		if err != nil {
			break
		}
		msg, err := common.DecodeMessage(data)
		if err != nil {
			common.Log("ws-client", fmt.Sprintf("Decode error: %v", err))
			continue
		}
		handleMessage(conn, msg)
	}
	close(done)
}

func handleMessage(conn *websocket.Conn, msg *common.Message) {
	switch msg.Type {
	case common.MsgBonjour:
		common.Log("ws-client", fmt.Sprintf("BONJOUR server_language=%s", msg.Bonjour.ServerLanguage))

	case common.MsgPing:
		common.Log("ws-client", fmt.Sprintf("PING ts=%d", msg.Ping.TimestampMs))
		pong := &common.Pong{TimestampMs: msg.Ping.TimestampMs}
		conn.WriteMessage(websocket.BinaryMessage, pong.Encode())
		common.Log("ws-client", fmt.Sprintf("PONG ts=%d", pong.TimestampMs))

	case common.MsgTimeNotification:
		common.Log("ws-client", fmt.Sprintf("TIME_NOTIFICATION ts=%d iso=%s", msg.TimeNotif.TimestampMs, msg.TimeNotif.ISO8601))

	case common.MsgKissRequest:
		kr := msg.KissReq
		common.Log("ws-client", fmt.Sprintf("KISS_REQUEST os=%s ver=%s rel=%s arch=%s", kr.OSName, kr.OSVersion, kr.OSRelease, kr.OSArchitecture))
		resp := &common.KissResponse{
			Language: "en_US",
			Encoding: "UTF-8",
			TimeZone: time.Now().Location().String(),
		}
		conn.WriteMessage(websocket.BinaryMessage, resp.Encode())
		common.Log("ws-client", fmt.Sprintf("KISS_RESPONSE lang=%s enc=%s tz=%s", resp.Language, resp.Encoding, resp.TimeZone))

	case common.MsgEchoResponse:
		er := msg.EchoResp
		common.Log("ws-client", fmt.Sprintf("ECHO_RESPONSE status=%d results=%d", er.Status, len(er.Results)))
		for i, r := range er.Results {
			common.Log("ws-client", fmt.Sprintf("  Result #%d: idx=%d type=%d kv=%v", i+1, r.Idx, r.Type, r.KV))
		}

	case common.MsgHashResponse:
		common.Log("ws-client", fmt.Sprintf("HASH_RESPONSE id=%d hash=%s", msg.Hash.ID, msg.Hash.HashHex))

	case common.MsgError:
		common.Log("ws-client", fmt.Sprintf("ERROR code=%d msg=%s", msg.Error.Code, msg.Error.Message))

	default:
		common.Log("ws-client", fmt.Sprintf("Unknown message type: 0x%02x", msg.Type))
	}
}

func randomTask(conn *websocket.Conn, done chan struct{}) {
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	id := int64(1)
	ticker := time.NewTicker(common.RandomInterval)
	defer ticker.Stop()
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			num := rng.Int63()
			rn := &common.RandomNumber{ID: id, Number: num}
			conn.WriteMessage(websocket.BinaryMessage, rn.Encode())
			common.Log("ws-client", fmt.Sprintf("RANDOM_NUMBER id=%d number=%d", id, num))
			id++
		}
	}
}
