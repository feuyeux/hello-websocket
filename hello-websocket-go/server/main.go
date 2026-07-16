package main

import (
	"fmt"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"time"

	"hello-websocket-go/common"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		origin := r.Header.Get("Origin")
		if origin == "" {
			return true
		}
		u, err := url.Parse(origin)
		return err == nil && strings.EqualFold(u.Host, r.Host)
	},
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
}

type Session struct {
	ID             string
	UserID         string
	ClientLanguage string
	ConnectedAt    int64
	LastPongTs     int64
	mu             sync.Mutex
	conn           *websocket.Conn
	closed         bool
}

func (s *Session) Send(data []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return fmt.Errorf("session closed")
	}
	return s.conn.WriteMessage(websocket.BinaryMessage, data)
}

func (s *Session) Close() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.closed {
		s.closed = true
		s.conn.Close()
	}
}

func main() {
	port := common.Port
	if p := os.Getenv("WS_PORT"); p != "" {
		if v, err := fmt.Sscanf(p, "%d", &port); err != nil || v != 1 {
			port = common.Port
		}
	}

	common.Log("ws-server", fmt.Sprintf("Starting Go WebSocket server on port %d", port))

	mux := http.NewServeMux()
	path := os.Getenv("WS_PATH")
	if path == "" {
		path = "/ws"
	}
	mux.HandleFunc(path, handleWebSocket)

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: mux,
	}

	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh
		common.Log("ws-server", "Shutting down...")
		server.Close()
	}()

	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		common.Log("ws-server", fmt.Sprintf("Server error: %v", err))
		os.Exit(1)
	}
}

func handleWebSocket(w http.ResponseWriter, r *http.Request) {
	userID := r.Header.Get("userId")
	if userID == "" {
		userID = uuid.New().String()
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		common.Log("ws-server", fmt.Sprintf("Upgrade error: %v", err))
		return
	}

	session := &Session{
		ID:          uuid.New().String(),
		UserID:      userID,
		ConnectedAt: common.NowMs(),
		LastPongTs:  common.NowMs(),
		conn:        conn,
	}
	conn.SetReadLimit(1 << 20)

	common.Log("ws-server", fmt.Sprintf("[%s] session+", session.UserID))

	// Background tasks
	ctx := &taskCtx{session: session, done: make(chan struct{})}
	go pingTask(ctx)
	go timeTask(ctx)
	go kissTask(ctx)
	go timeoutTask(ctx)

	// Receive loop
	defer func() {
		close(ctx.done)
		session.Close()
		common.Log("ws-server", fmt.Sprintf("[%s] session-", session.UserID))
	}()

	for {
		_, data, err := conn.ReadMessage()
		if err != nil {
			break
		}
		msg, err := common.DecodeMessage(data)
		if err != nil {
			common.Log("ws-server", fmt.Sprintf("Decode error: %v", err))
			unknown := strings.HasPrefix(err.Error(), "unknown message type")
			code := int32(common.ErrDecode)
			if unknown {
				code = common.ErrUnknownMsgType
			}
			errMsg := &common.ErrorMsg{Code: code, Message: err.Error()}
			session.Send(errMsg.Encode())
			if !unknown {
				break
			}
			continue
		}
		handleMessage(session, msg)
	}
}

type taskCtx struct {
	session *Session
	done    chan struct{}
}

func (c *taskCtx) isDone() bool {
	select {
	case <-c.done:
		return true
	default:
		return false
	}
}

func handleMessage(s *Session, msg *common.Message) {
	switch msg.Type {
	case common.MsgHello:
		s.ClientLanguage = msg.Hello.ClientLanguage
		common.Log("ws-server", fmt.Sprintf("HELLO from %s, session=%s, time=%d", s.ClientLanguage, s.ID, common.NowMs()))
		bonjour := &common.Bonjour{ServerLanguage: common.ServerLang}
		s.Send(bonjour.Encode())

	case common.MsgEchoRequest:
		req := msg.EchoReq
		common.Log("ws-server", fmt.Sprintf("ECHO_REQUEST id=%d meta=%s data=%s", req.ID, req.Meta, req.Data))
		result := common.EchoResult{
			Idx:  common.NowMs(),
			Type: 0,
			KV: map[string]string{
				"id":   fmt.Sprintf("%d", req.ID),
				"idx":  req.Data,
				"data": req.Data,
				"meta": s.ClientLanguage,
			},
		}
		resp := &common.EchoResponse{Status: 200, Results: []common.EchoResult{result}}
		s.Send(resp.Encode())

	case common.MsgKissResponse:
		kr := msg.KissResp
		common.Log("ws-server", fmt.Sprintf("KISS_RESPONSE lang=%s enc=%s tz=%s", kr.Language, kr.Encoding, kr.TimeZone))

	case common.MsgPong:
		s.mu.Lock()
		s.LastPongTs = common.NowMs()
		s.mu.Unlock()
		common.Log("ws-server", fmt.Sprintf("PONG ts=%d", msg.Pong.TimestampMs))

	case common.MsgRandomNumber:
		rn := msg.Random
		common.Log("ws-server", fmt.Sprintf("RANDOM_NUMBER id=%d number=%d", rn.ID, rn.Number))
		hash := common.HashNumber(rn.Number)
		resp := &common.HashResponse{ID: rn.ID, HashHex: hash}
		s.Send(resp.Encode())
		common.Log("ws-server", fmt.Sprintf("HASH_RESPONSE id=%d hash=%s", rn.ID, hash))

	case common.MsgDisconnect:
		common.Log("ws-server", fmt.Sprintf("DISCONNECT reason=%s", msg.Disconnect.Reason))
		s.Close()

	case common.MsgError:
		common.Log("ws-server", fmt.Sprintf("ERROR code=%d msg=%s", msg.Error.Code, msg.Error.Message))

	default:
		common.Log("ws-server", fmt.Sprintf("Unknown message type: 0x%02x", msg.Type))
		errMsg := &common.ErrorMsg{Code: common.ErrUnknownMsgType, Message: fmt.Sprintf("unknown type 0x%02x", msg.Type)}
		s.Send(errMsg.Encode())
	}
}

func pingTask(ctx *taskCtx) {
	ticker := time.NewTicker(common.PingInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.done:
			return
		case <-ticker.C:
			if ctx.isDone() {
				return
			}
			ping := &common.Ping{TimestampMs: common.NowMs()}
			ctx.session.Send(ping.Encode())
		}
	}
}

func timeTask(ctx *taskCtx) {
	ticker := time.NewTicker(common.TimeInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.done:
			return
		case <-ticker.C:
			if ctx.isDone() {
				return
			}
			tn := &common.TimeNotification{TimestampMs: common.NowMs(), ISO8601: common.NowISO()}
			ctx.session.Send(tn.Encode())
		}
	}
}

func kissTask(ctx *taskCtx) {
	ticker := time.NewTicker(common.KissInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.done:
			return
		case <-ticker.C:
			if ctx.isDone() {
				return
			}
			kr := &common.KissRequest{
				OSName:         runtime.GOOS,
				OSVersion:      "unknown",
				OSRelease:      "unknown",
				OSArchitecture: runtime.GOARCH,
			}
			ctx.session.Send(kr.Encode())
		}
	}
}

func timeoutTask(ctx *taskCtx) {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.done:
			return
		case <-ticker.C:
			ctx.session.mu.Lock()
			last := ctx.session.LastPongTs
			ctx.session.mu.Unlock()
			if common.NowMs()-last > int64(common.SessionTimeout/time.Millisecond) {
				common.Log("ws-server", fmt.Sprintf("[%s] session timeout", ctx.session.UserID))
				ctx.session.Close()
				return
			}
		}
	}
}
