#!/usr/bin/env python3
"""Codec unit tests for the Python implementation."""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.codec import *


def test_hello_worked_example():
    """Test the worked example from PROTOCOL.md section 9."""
    hello = Hello(client_language="Go")
    data = hello.encode()
    expected = bytes([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F])
    assert data == expected, f"HELLO encode mismatch:\n  got:  {data.hex()}\n  want: {expected.hex()}"


def test_round_trip_hello():
    orig = Hello(client_language="Rust")
    msg = decode_message(orig.encode())
    assert msg.type == MSG_HELLO
    assert msg.hello.client_language == "Rust"


def test_round_trip_bonjour():
    orig = Bonjour(server_language="Java")
    msg = decode_message(orig.encode())
    assert msg.bonjour.server_language == "Java"


def test_round_trip_echo_request():
    orig = EchoRequest(id=42, meta="Python", data="hello")
    msg = decode_message(orig.encode())
    assert msg.echo_req.id == 42
    assert msg.echo_req.meta == "Python"
    assert msg.echo_req.data == "hello"


def test_round_trip_echo_response():
    orig = EchoResponse(status=200, results=[
        EchoResult(idx=123, type=0, kv={"id": "1", "data": "Hello"})])
    msg = decode_message(orig.encode())
    assert msg.echo_resp.status == 200
    assert len(msg.echo_resp.results) == 1
    assert msg.echo_resp.results[0].kv["id"] == "1"


def test_round_trip_kiss():
    orig = KissRequest(os_name="Linux", os_version="6.6", os_release="arch", os_architecture="AMD64")
    msg = decode_message(orig.encode())
    assert msg.kiss_req.os_name == "Linux"
    assert msg.kiss_req.os_architecture == "AMD64"

    resp = KissResponse(language="zh_CN", encoding="UTF-8", time_zone="Asia/Shanghai")
    msg2 = decode_message(resp.encode())
    assert msg2.kiss_resp.language == "zh_CN"
    assert msg2.kiss_resp.time_zone == "Asia/Shanghai"


def test_round_trip_ping_pong():
    ping = Ping(timestamp_ms=1700000000000)
    msg = decode_message(ping.encode())
    assert msg.ping.timestamp_ms == 1700000000000

    pong = Pong(timestamp_ms=1700000000001)
    msg2 = decode_message(pong.encode())
    assert msg2.pong.timestamp_ms == 1700000000001


def test_round_trip_time_notification():
    orig = TimeNotification(timestamp_ms=1700000000000, iso8601="2023-11-14T22:13:20Z")
    msg = decode_message(orig.encode())
    assert msg.time_notif.iso8601 == "2023-11-14T22:13:20Z"


def test_round_trip_random_hash():
    rn = RandomNumber(id=99, number=42)
    msg = decode_message(rn.encode())
    assert msg.random.id == 99
    assert msg.random.number == 42

    hr = HashResponse(id=99, hash_hex="7688b6ef5a")
    msg2 = decode_message(hr.encode())
    assert msg2.hash.hash_hex == "7688b6ef5a"


def test_round_trip_disconnect():
    orig = Disconnect(reason="bye")
    msg = decode_message(orig.encode())
    assert msg.disconnect.reason == "bye"


def test_round_trip_error():
    orig = ErrorMsg(code=ERR_UNKNOWN_MSG_TYPE, message="bad type")
    msg = decode_message(orig.encode())
    assert msg.error.code == ERR_UNKNOWN_MSG_TYPE
    assert msg.error.message == "bad type"


def test_bad_magic():
    try:
        decode_frame(bytes([0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]))
        assert False, "expected error"
    except ValueError:
        pass


def test_bad_version():
    try:
        decode_frame(bytes([0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]))
        assert False, "expected error"
    except ValueError:
        pass


def test_truncated_payload():
    try:
        decode_frame(bytes([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF]))
        assert False, "expected error"
    except ValueError:
        pass


def test_hash_number():
    h = hash_number(42)
    assert len(h) == 10
    assert h == hash_number(42)


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = 0
    failed = 0
    for t in tests:
        try:
            t()
            print(f"  PASS: {t.__name__}")
            passed += 1
        except Exception as e:
            print(f"  FAIL: {t.__name__}: {e}")
            failed += 1
    print(f"\n{passed} passed, {failed} failed, {len(tests)} total")
    sys.exit(0 if failed == 0 else 1)
