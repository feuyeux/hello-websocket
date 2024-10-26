import json
from dataclasses import dataclass
from typing import Dict, List
from enum import Enum
import random
import uuid

TCP_PORT = 9800
TLS_PORT = 9886
HOST = "127.0.0.1"

@dataclass
class EchoRequest:
    id: int
    meta: str
    data: str

    def to_bytes(self) -> bytes:
        id_bytes = self.id.to_bytes(4, byteorder='big')
        meta_bytes = self.meta.encode('utf-8')
        data_bytes = self.data.encode('utf-8')
        return id_bytes + len(meta_bytes).to_bytes(4, byteorder='big') + meta_bytes + len(data_bytes).to_bytes(4, byteorder='big') + data_bytes

    @staticmethod
    def from_bytes(data: bytes) -> 'EchoRequest':
        id_bytes = data[:4]
        request_id = int.from_bytes(id_bytes, byteorder='big')
        meta_length = int.from_bytes(data[4:8], byteorder='big')
        meta = data[8:8 + meta_length].decode('utf-8')
        data_length = int.from_bytes(data[8 + meta_length:12 + meta_length], byteorder='big')
        data_str = data[12 + meta_length:12 + meta_length + data_length].decode('utf-8')
        return EchoRequest(id=request_id, meta=meta, data=data_str)

class EchoType(Enum):
    OK = 0
    ERROR = 1
@dataclass
class EchoResult:
    idx: int
    type: EchoType
    kv: Dict[str, str]

    def to_bytes(self) -> bytes:
        idx_bytes = self.idx.to_bytes(8, byteorder='big')
        type_bytes = self.type.value.to_bytes(4, byteorder='big')
        kv_bytes = json.dumps(self.kv).encode('utf-8')
        kv_length_bytes = len(kv_bytes).to_bytes(4, byteorder='big')
        return idx_bytes + type_bytes + kv_length_bytes + kv_bytes

    @staticmethod
    def from_bytes(data: bytes) -> ('EchoResult', bytes):
        idx = int.from_bytes(data[:8], byteorder='big')
        type_value = int.from_bytes(data[8:12], byteorder='big')
        type = EchoType(type_value)
        kv_length = int.from_bytes(data[12:16], byteorder='big')
        kv = json.loads(data[16:16 + kv_length].decode('utf-8'))
        remaining_data = data[16 + kv_length:]
        return EchoResult(idx=idx, type=type, kv=kv), remaining_data

@dataclass
class EchoResponse:
    status: int
    results: List[EchoResult]

    def to_bytes(self) -> bytes:
        status_bytes = self.status.to_bytes(4, byteorder='big')
        results_bytes = b''.join(result.to_bytes() for result in self.results)
        results_length_bytes = len(results_bytes).to_bytes(4, byteorder='big')
        return status_bytes + results_length_bytes + results_bytes

    @staticmethod
    def from_bytes(data: bytes) -> 'EchoResponse':
        status = int.from_bytes(data[:4], byteorder='big')
        results_length = int.from_bytes(data[4:8], byteorder='big')
        results_bytes = data[8:8 + results_length]
        results = []
        while results_bytes:
            result, results_bytes = EchoResult.from_bytes(results_bytes)
            results.append(result)
        return EchoResponse(status=status, results=results)

HELLO_LIST = ["Hello", "Bonjour", "Hola", "こんにちは", "Ciao", "안녕하세요"]
ANS_MAP = {
    "你好": "非常感谢",
    "Hello": "Thank you very much",
    "Bonjour": "Merci beaucoup",
    "Hola": "Muchas Gracias",
    "こんにちは": "どうも ありがとう ございます",
    "Ciao": "Mille Grazie",
    "안녕하세요": "대단히 감사합니다"
}

def get_hello_list() -> List[str]:
    return HELLO_LIST

def get_answer_map() -> Dict[str, str]:
    return ANS_MAP

def build_link_requests() -> List[EchoRequest]:
    requests = []
    for _ in range(3):
        requests.insert(0, EchoRequest(message="JAVA", id=get_random_id()))
    return requests

def get_random_ids(max_value: int) -> List[str]:
    return [get_random_id() for _ in range(max_value)]

def get_random_id() -> str:
    return str(random.randint(0, 5))

def build_results(*idxs: str) -> List[EchoResult]:
    return [build_result(id) for id in idxs]

def build_result(idx: str) -> EchoResult:
    try:
        index = int(idx)
    except ValueError:
        index = 0

    if index > 5:
        hello = "你好"
    else:
        hello = get_hello_list()[index]

    kv = {
        "id": str(uuid.uuid4()),
        "idx": idx,
        "data": f"{hello},{get_answer_map().get(hello)}",
        "meta": "JAVA"
    }
    return EchoResult(idx=int(uuid.uuid1().time), type=EchoType.OK, kv=kv)