from dataclasses import dataclass
from typing import Dict, List
from enum import Enum
import random
import uuid

class EchoType(Enum):
    OK = 0
    ERROR = 1

@dataclass
class EchoRequest:
    message: str
    id: int

@dataclass
class EchoResult:
    idx: int
    type: EchoType
    kv: Dict[str, str]

@dataclass
class EchoResponse:
    status: int
    results: List[EchoResult]

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

def get_random_ids(max: int) -> List[str]:
    return [get_random_id() for _ in range(max)]

def get_random_id() -> str:
    return str(random.randint(0, 5))

def build_results(*ids: str) -> List[EchoResult]:
    return [build_result(id) for id in ids]

def build_result(id: str) -> EchoResult:
    try:
        index = int(id)
    except ValueError:
        index = 0

    if index > 5:
        hello = "你好"
    else:
        hello = get_hello_list()[index]

    kv = {
        "id": str(uuid.uuid4()),
        "idx": id,
        "data": f"{hello},{get_answer_map().get(hello)}",
        "meta": "JAVA"
    }
    return EchoResult(idx=int(uuid.uuid1().time), type=EchoType.OK, kv=kv)