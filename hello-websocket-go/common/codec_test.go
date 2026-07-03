package common

import (
	"bytes"
	"testing"
)

// Test the worked example from PROTOCOL.md section 9:
// HELLO with client_language = "Go" should produce exactly:
// 48 01 01 00 00 00 00 06 00 00 00 02 47 6F
func TestHelloWorkedExample(t *testing.T) {
	hello := &Hello{ClientLanguage: "Go"}
	data := hello.Encode()
	expected := []byte{0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F}
	if !bytes.Equal(data, expected) {
		t.Errorf("HELLO encode mismatch:\n  got:  %x\n  want: %x", data, expected)
	}
}

// Round-trip all message types
func TestRoundTripHello(t *testing.T) {
	orig := &Hello{ClientLanguage: "Rust"}
	data := orig.Encode()
	msg, err := DecodeMessage(data)
	if err != nil {
		t.Fatalf("DecodeMessage error: %v", err)
	}
	if msg.Type != MsgHello {
		t.Fatalf("expected MsgHello, got 0x%02x", msg.Type)
	}
	if msg.Hello.ClientLanguage != "Rust" {
		t.Fatalf("expected Rust, got %s", msg.Hello.ClientLanguage)
	}
}

func TestRoundTripBonjour(t *testing.T) {
	orig := &Bonjour{ServerLanguage: "Java"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.Bonjour.ServerLanguage != "Java" {
		t.Fatalf("expected Java, got %s", msg.Bonjour.ServerLanguage)
	}
}

func TestRoundTripEchoRequest(t *testing.T) {
	orig := &EchoRequest{ID: 42, Meta: "Python", Data: "hello"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.EchoReq.ID != 42 || msg.EchoReq.Meta != "Python" || msg.EchoReq.Data != "hello" {
		t.Fatalf("mismatch: %+v", msg.EchoReq)
	}
}

func TestRoundTripEchoResponse(t *testing.T) {
	orig := &EchoResponse{
		Status: 200,
		Results: []EchoResult{
			{Idx: 123, Type: 0, KV: map[string]string{"id": "1", "data": "Hello"}},
		},
	}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.EchoResp.Status != 200 || len(msg.EchoResp.Results) != 1 {
		t.Fatalf("mismatch: %+v", msg.EchoResp)
	}
	if msg.EchoResp.Results[0].KV["id"] != "1" {
		t.Fatalf("kv mismatch: %v", msg.EchoResp.Results[0].KV)
	}
}

func TestRoundTripKissRequest(t *testing.T) {
	orig := &KissRequest{OSName: "Linux", OSVersion: "6.6", OSRelease: "arch", OSArchitecture: "AMD64"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.KissReq.OSName != "Linux" || msg.KissReq.OSArchitecture != "AMD64" {
		t.Fatalf("mismatch: %+v", msg.KissReq)
	}
}

func TestRoundTripKissResponse(t *testing.T) {
	orig := &KissResponse{Language: "zh_CN", Encoding: "UTF-8", TimeZone: "Asia/Shanghai"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.KissResp.Language != "zh_CN" || msg.KissResp.TimeZone != "Asia/Shanghai" {
		t.Fatalf("mismatch: %+v", msg.KissResp)
	}
}

func TestRoundTripPingPong(t *testing.T) {
	ping := &Ping{TimestampMs: 1700000000000}
	msg, _ := DecodeMessage(ping.Encode())
	if msg.Ping.TimestampMs != 1700000000000 {
		t.Fatalf("ping mismatch: %d", msg.Ping.TimestampMs)
	}

	pong := &Pong{TimestampMs: 1700000000001}
	msg2, _ := DecodeMessage(pong.Encode())
	if msg2.Pong.TimestampMs != 1700000000001 {
		t.Fatalf("pong mismatch: %d", msg2.Pong.TimestampMs)
	}
}

func TestRoundTripTimeNotification(t *testing.T) {
	orig := &TimeNotification{TimestampMs: 1700000000000, ISO8601: "2023-11-14T22:13:20Z"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.TimeNotif.ISO8601 != "2023-11-14T22:13:20Z" {
		t.Fatalf("mismatch: %+v", msg.TimeNotif)
	}
}

func TestRoundTripRandomHash(t *testing.T) {
	rn := &RandomNumber{ID: 99, Number: 42}
	msg, _ := DecodeMessage(rn.Encode())
	if msg.Random.ID != 99 || msg.Random.Number != 42 {
		t.Fatalf("random mismatch: %+v", msg.Random)
	}

	hr := &HashResponse{ID: 99, HashHex: "7688b6ef5a"}
	msg2, _ := DecodeMessage(hr.Encode())
	if msg2.Hash.HashHex != "7688b6ef5a" {
		t.Fatalf("hash mismatch: %s", msg2.Hash.HashHex)
	}
}

func TestRoundTripDisconnect(t *testing.T) {
	orig := &Disconnect{Reason: "bye"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.Disconnect.Reason != "bye" {
		t.Fatalf("mismatch: %s", msg.Disconnect.Reason)
	}
}

func TestRoundTripError(t *testing.T) {
	orig := &ErrorMsg{Code: ErrUnknownMsgType, Message: "bad type"}
	msg, err := DecodeMessage(orig.Encode())
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if msg.Error.Code != ErrUnknownMsgType || msg.Error.Message != "bad type" {
		t.Fatalf("mismatch: %+v", msg.Error)
	}
}

// Negative tests
func TestBadMagic(t *testing.T) {
	data := []byte{0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00}
	_, _, err := DecodeFrame(data)
	if err == nil {
		t.Fatal("expected error for bad magic")
	}
}

func TestBadVersion(t *testing.T) {
	data := []byte{0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00}
	_, _, err := DecodeFrame(data)
	if err == nil {
		t.Fatal("expected error for bad version")
	}
}

func TestTruncatedPayload(t *testing.T) {
	data := []byte{0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF}
	_, _, err := DecodeFrame(data)
	if err == nil {
		t.Fatal("expected error for truncated payload")
	}
}

func TestHashNumber(t *testing.T) {
	// Verify hash of 42 produces a 10-char hex string
	hash := HashNumber(42)
	if len(hash) != 10 {
		t.Fatalf("expected 10 hex chars, got %d: %s", len(hash), hash)
	}
	// Verify it's deterministic
	hash2 := HashNumber(42)
	if hash != hash2 {
		t.Fatalf("hash not deterministic: %s vs %s", hash, hash2)
	}
}
