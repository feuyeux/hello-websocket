package common

import (
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"math"
	"strings"
	"time"
)

// ─── Constants ───────────────────────────────────────────────────────────

const (
	Port            = 9898
	Magic     byte  = 0x48
	Version   byte  = 0x01
	HeaderLen       = 8
	ServerLang      = "GO"
	ClientLang      = "GO"
)

// Message types
const (
	MsgHello            byte = 0x01
	MsgBonjour          byte = 0x02
	MsgEchoRequest      byte = 0x03
	MsgEchoResponse     byte = 0x04
	MsgKissRequest      byte = 0x05
	MsgKissResponse     byte = 0x06
	MsgPing             byte = 0x07
	MsgPong             byte = 0x08
	MsgTimeNotification byte = 0x09
	MsgRandomNumber     byte = 0x0A
	MsgHashResponse     byte = 0x0B
	MsgDisconnect       byte = 0x0C
	MsgError            byte = 0x7F
)

// Error codes
const (
	ErrDecode            int32 = 0x01
	ErrUnknownMsgType   int32 = 0x02
	ErrTruncatedPayload int32 = 0x03
	ErrBadMagic         int32 = 0x04
	ErrBadVersion       int32 = 0x05
	ErrSessionNotFound  int32 = 0x06
	ErrInternal         int32 = 0x07
)

// Intervals
const (
	PingInterval       = 1 * time.Second
	SessionTimeout     = 60 * time.Second
	TimeInterval       = 5 * time.Second
	RandomInterval     = 5 * time.Second
	KissInterval       = 5 * time.Second
)

// ─── Primitive Encoders ──────────────────────────────────────────────────

func WriteU8(buf *[]byte, v uint8) {
	*buf = append(*buf, v)
}

func WriteU16(buf *[]byte, v uint16) {
	var b [2]byte
	binary.BigEndian.PutUint16(b[:], v)
	*buf = append(*buf, b[:]...)
}

func WriteU32(buf *[]byte, v uint32) {
	var b [4]byte
	binary.BigEndian.PutUint32(b[:], v)
	*buf = append(*buf, b[:]...)
}

func WriteI32(buf *[]byte, v int32) {
	WriteU32(buf, uint32(v))
}

func WriteI64(buf *[]byte, v int64) {
	var b [8]byte
	binary.BigEndian.PutUint64(b[:], uint64(v))
	*buf = append(*buf, b[:]...)
}

func WriteString(buf *[]byte, s string) {
	b := []byte(s)
	WriteU32(buf, uint32(len(b)))
	*buf = append(*buf, b...)
}

func WriteKV(buf *[]byte, m map[string]string) {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	WriteU32(buf, uint32(len(keys)))
	for _, k := range keys {
		WriteString(buf, k)
		WriteString(buf, m[k])
	}
}

// ─── Primitive Decoders ──────────────────────────────────────────────────

type Reader struct {
	data []byte
	pos  int
}

func NewReader(data []byte) *Reader {
	return &Reader{data: data}
}

func (r *Reader) ReadU8() (uint8, error) {
	if r.pos+1 > len(r.data) {
		return 0, fmt.Errorf("unexpected end of data at offset %d reading u8", r.pos)
	}
	v := r.data[r.pos]
	r.pos++
	return v, nil
}

func (r *Reader) ReadU16() (uint16, error) {
	if r.pos+2 > len(r.data) {
		return 0, fmt.Errorf("unexpected end of data at offset %d reading u16", r.pos)
	}
	v := binary.BigEndian.Uint16(r.data[r.pos:])
	r.pos += 2
	return v, nil
}

func (r *Reader) ReadU32() (uint32, error) {
	if r.pos+4 > len(r.data) {
		return 0, fmt.Errorf("unexpected end of data at offset %d reading u32", r.pos)
	}
	v := binary.BigEndian.Uint32(r.data[r.pos:])
	r.pos += 4
	return v, nil
}

func (r *Reader) ReadI32() (int32, error) {
	u, err := r.ReadU32()
	return int32(u), err
}

func (r *Reader) ReadI64() (int64, error) {
	if r.pos+8 > len(r.data) {
		return 0, fmt.Errorf("unexpected end of data at offset %d reading i64", r.pos)
	}
	v := int64(binary.BigEndian.Uint64(r.data[r.pos:]))
	r.pos += 8
	return v, nil
}

func (r *Reader) ReadString() (string, error) {
	ln, err := r.ReadU32()
	if err != nil {
		return "", err
	}
	if r.pos+int(ln) > len(r.data) {
		return "", fmt.Errorf("string length %d exceeds remaining data", ln)
	}
	s := string(r.data[r.pos : r.pos+int(ln)])
	r.pos += int(ln)
	return s, nil
}

func (r *Reader) ReadKV() (map[string]string, error) {
	count, err := r.ReadU32()
	if err != nil {
		return nil, err
	}
	m := make(map[string]string, count)
	for i := uint32(0); i < count; i++ {
		k, err := r.ReadString()
		if err != nil {
			return nil, err
		}
		v, err := r.ReadString()
		if err != nil {
			return nil, err
		}
		m[k] = v
	}
	return m, nil
}

// ─── Frame Codec ─────────────────────────────────────────────────────────

func EncodeFrame(msgType byte, payload []byte) []byte {
	buf := make([]byte, HeaderLen+len(payload))
	buf[0] = Magic
	buf[1] = Version
	buf[2] = msgType
	buf[3] = 0x00 // flags
	binary.BigEndian.PutUint32(buf[4:], uint32(len(payload)))
	copy(buf[HeaderLen:], payload)
	return buf
}

func DecodeFrame(data []byte) (msgType byte, payload []byte, err error) {
	if len(data) < HeaderLen {
		return 0, nil, fmt.Errorf("frame too short: %d bytes", len(data))
	}
	if data[0] != Magic {
		return 0, nil, fmt.Errorf("bad magic: 0x%02x", data[0])
	}
	if data[1] != Version {
		return 0, nil, fmt.Errorf("bad version: 0x%02x", data[1])
	}
	msgType = data[2]
	payloadLen := binary.BigEndian.Uint32(data[4:])
	if int(payloadLen) > len(data)-HeaderLen {
		return 0, nil, fmt.Errorf("truncated payload: declared %d, available %d", payloadLen, len(data)-HeaderLen)
	}
	payload = data[HeaderLen : HeaderLen+int(payloadLen)]
	return msgType, payload, nil
}

// ─── Message Types ───────────────────────────────────────────────────────

type Hello struct {
	ClientLanguage string
}

func (m *Hello) Encode() []byte {
	var buf []byte
	WriteString(&buf, m.ClientLanguage)
	return EncodeFrame(MsgHello, buf)
}

func DecodeHello(r *Reader) (*Hello, error) {
	lang, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &Hello{ClientLanguage: lang}, nil
}

type Bonjour struct {
	ServerLanguage string
}

func (m *Bonjour) Encode() []byte {
	var buf []byte
	WriteString(&buf, m.ServerLanguage)
	return EncodeFrame(MsgBonjour, buf)
}

func DecodeBonjour(r *Reader) (*Bonjour, error) {
	lang, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &Bonjour{ServerLanguage: lang}, nil
}

type EchoRequest struct {
	ID   int64
	Meta string
	Data string
}

func (m *EchoRequest) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.ID)
	WriteString(&buf, m.Meta)
	WriteString(&buf, m.Data)
	return EncodeFrame(MsgEchoRequest, buf)
}

func DecodeEchoRequest(r *Reader) (*EchoRequest, error) {
	id, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	meta, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	data, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &EchoRequest{ID: id, Meta: meta, Data: data}, nil
}

type EchoResult struct {
	Idx  int64
	Type uint8
	KV   map[string]string
}

type EchoResponse struct {
	Status  int32
	Results []EchoResult
}

func (m *EchoResponse) Encode() []byte {
	var buf []byte
	WriteI32(&buf, m.Status)
	WriteU32(&buf, uint32(len(m.Results)))
	for _, res := range m.Results {
		WriteI64(&buf, res.Idx)
		WriteU8(&buf, res.Type)
		WriteKV(&buf, res.KV)
	}
	return EncodeFrame(MsgEchoResponse, buf)
}

func DecodeEchoResponse(r *Reader) (*EchoResponse, error) {
	status, err := r.ReadI32()
	if err != nil {
		return nil, err
	}
	count, err := r.ReadU32()
	if err != nil {
		return nil, err
	}
	results := make([]EchoResult, 0, count)
	for i := uint32(0); i < count; i++ {
		idx, err := r.ReadI64()
		if err != nil {
			return nil, err
		}
		typ, err := r.ReadU8()
		if err != nil {
			return nil, err
		}
		kv, err := r.ReadKV()
		if err != nil {
			return nil, err
		}
		results = append(results, EchoResult{Idx: idx, Type: typ, KV: kv})
	}
	return &EchoResponse{Status: status, Results: results}, nil
}

type KissRequest struct {
	OSName         string
	OSVersion      string
	OSRelease      string
	OSArchitecture string
}

func (m *KissRequest) Encode() []byte {
	var buf []byte
	WriteString(&buf, m.OSName)
	WriteString(&buf, m.OSVersion)
	WriteString(&buf, m.OSRelease)
	WriteString(&buf, m.OSArchitecture)
	return EncodeFrame(MsgKissRequest, buf)
}

func DecodeKissRequest(r *Reader) (*KissRequest, error) {
	name, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	ver, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	rel, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	arch, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &KissRequest{OSName: name, OSVersion: ver, OSRelease: rel, OSArchitecture: arch}, nil
}

type KissResponse struct {
	Language string
	Encoding string
	TimeZone string
}

func (m *KissResponse) Encode() []byte {
	var buf []byte
	WriteString(&buf, m.Language)
	WriteString(&buf, m.Encoding)
	WriteString(&buf, m.TimeZone)
	return EncodeFrame(MsgKissResponse, buf)
}

func DecodeKissResponse(r *Reader) (*KissResponse, error) {
	lang, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	enc, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	tz, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &KissResponse{Language: lang, Encoding: enc, TimeZone: tz}, nil
}

type Ping struct {
	TimestampMs int64
}

func (m *Ping) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.TimestampMs)
	return EncodeFrame(MsgPing, buf)
}

func DecodePing(r *Reader) (*Ping, error) {
	ts, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	return &Ping{TimestampMs: ts}, nil
}

type Pong struct {
	TimestampMs int64
}

func (m *Pong) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.TimestampMs)
	return EncodeFrame(MsgPong, buf)
}

func DecodePong(r *Reader) (*Pong, error) {
	ts, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	return &Pong{TimestampMs: ts}, nil
}

type TimeNotification struct {
	TimestampMs int64
	ISO8601     string
}

func (m *TimeNotification) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.TimestampMs)
	WriteString(&buf, m.ISO8601)
	return EncodeFrame(MsgTimeNotification, buf)
}

func DecodeTimeNotification(r *Reader) (*TimeNotification, error) {
	ts, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	iso, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &TimeNotification{TimestampMs: ts, ISO8601: iso}, nil
}

type RandomNumber struct {
	ID     int64
	Number int64
}

func (m *RandomNumber) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.ID)
	WriteI64(&buf, m.Number)
	return EncodeFrame(MsgRandomNumber, buf)
}

func DecodeRandomNumber(r *Reader) (*RandomNumber, error) {
	id, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	num, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	return &RandomNumber{ID: id, Number: num}, nil
}

type HashResponse struct {
	ID      int64
	HashHex string
}

func (m *HashResponse) Encode() []byte {
	var buf []byte
	WriteI64(&buf, m.ID)
	WriteString(&buf, m.HashHex)
	return EncodeFrame(MsgHashResponse, buf)
}

func DecodeHashResponse(r *Reader) (*HashResponse, error) {
	id, err := r.ReadI64()
	if err != nil {
		return nil, err
	}
	hash, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &HashResponse{ID: id, HashHex: hash}, nil
}

type Disconnect struct {
	Reason string
}

func (m *Disconnect) Encode() []byte {
	var buf []byte
	WriteString(&buf, m.Reason)
	return EncodeFrame(MsgDisconnect, buf)
}

func DecodeDisconnect(r *Reader) (*Disconnect, error) {
	reason, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &Disconnect{Reason: reason}, nil
}

type ErrorMsg struct {
	Code    int32
	Message string
}

func (m *ErrorMsg) Encode() []byte {
	var buf []byte
	WriteI32(&buf, m.Code)
	WriteString(&buf, m.Message)
	return EncodeFrame(MsgError, buf)
}

func DecodeErrorMsg(r *Reader) (*ErrorMsg, error) {
	code, err := r.ReadI32()
	if err != nil {
		return nil, err
	}
	msg, err := r.ReadString()
	if err != nil {
		return nil, err
	}
	return &ErrorMsg{Code: code, Message: msg}, nil
}

// ─── Message Dispatch ────────────────────────────────────────────────────

type Message struct {
	Type     byte
	Hello    *Hello
	Bonjour  *Bonjour
	EchoReq  *EchoRequest
	EchoResp *EchoResponse
	KissReq  *KissRequest
	KissResp *KissResponse
	Ping     *Ping
	Pong     *Pong
	TimeNotif *TimeNotification
	Random   *RandomNumber
	Hash     *HashResponse
	Disconnect *Disconnect
	Error    *ErrorMsg
}

func DecodeMessage(data []byte) (*Message, error) {
	msgType, payload, err := DecodeFrame(data)
	if err != nil {
		return nil, err
	}
	r := NewReader(payload)
	msg := &Message{Type: msgType}
	switch msgType {
	case MsgHello:
		msg.Hello, err = DecodeHello(r)
	case MsgBonjour:
		msg.Bonjour, err = DecodeBonjour(r)
	case MsgEchoRequest:
		msg.EchoReq, err = DecodeEchoRequest(r)
	case MsgEchoResponse:
		msg.EchoResp, err = DecodeEchoResponse(r)
	case MsgKissRequest:
		msg.KissReq, err = DecodeKissRequest(r)
	case MsgKissResponse:
		msg.KissResp, err = DecodeKissResponse(r)
	case MsgPing:
		msg.Ping, err = DecodePing(r)
	case MsgPong:
		msg.Pong, err = DecodePong(r)
	case MsgTimeNotification:
		msg.TimeNotif, err = DecodeTimeNotification(r)
	case MsgRandomNumber:
		msg.Random, err = DecodeRandomNumber(r)
	case MsgHashResponse:
		msg.Hash, err = DecodeHashResponse(r)
	case MsgDisconnect:
		msg.Disconnect, err = DecodeDisconnect(r)
	case MsgError:
		msg.Error, err = DecodeErrorMsg(r)
	default:
		return nil, fmt.Errorf("unknown message type: 0x%02x", msgType)
	}
	if err != nil {
		return nil, err
	}
	return msg, nil
}

// ─── Utility ─────────────────────────────────────────────────────────────

func NowMs() int64 {
	return time.Now().UnixMilli()
}

func NowISO() string {
	return time.Now().UTC().Format("2006-01-02T15:04:05Z")
}

func HashNumber(num int64) string {
	h := sha256.Sum256([]byte(fmt.Sprintf("%d", num)))
	return fmt.Sprintf("%x", h[:5])
}

func Log(name, msg string) {
	ts := time.Now().Format("2006-01-02 15:04:05")
	fmt.Printf("[%s] [INFO] [%s] %s\n", ts, name, msg)
}

func Logf(name, format string, args ...interface{}) {
	Log(name, fmt.Sprintf(format, args...))
}

// FloatToBits and back (unused but available for future extensions)
func Float64ToBits(f float64) uint64 {
	return math.Float64bits(f)
}

func BitsToFloat64(b uint64) float64 {
	return math.Float64frombits(b)
}

// SafeI64ToString converts an int64 to string without using strconv (for minimal deps)
func I64ToString(v int64) string {
	return fmt.Sprintf("%d", v)
}

// JoinStrings joins strings with a separator
func JoinStrings(parts []string, sep string) string {
	return strings.Join(parts, sep)
}
