import locale
import platform
import random
import time
import uuid
from datetime import datetime
from typing import Dict, List
from hello_protocol import EchoRequest, EchoResult, EchoType, KissRequest, KissResponse
import logging


def setup_logger(name: str, level: int = logging.INFO) -> logging.Logger:
    logger = logging.getLogger(name)
    logger.setLevel(level)
    if not logger.handlers:
        console = logging.StreamHandler()
        console.setLevel(level)
        formatter = logging.Formatter(
            "%(asctime)s [%(levelname)s] - %(message)s")
        console.setFormatter(formatter)
        logger.addHandler(console)
    return logger


HELLO_LIST = ["Hello", "Bonjour", "Hola", "こんにちは", "Ciao", "안녕하세요"]
ANS_MAP = {
    "你好": "非常感谢",
    "Hello": "Thank you very much",
    "Bonjour": "Merci beaucoup",
    "Hola": "Muchas Gracias",
    "こんにちは": "どうも ありがとう ございます",
    "Ciao": "Mille Grazie",
    "안녕하세요": "대단히 감사합니다",
}


def get_hello_list() -> List[str]:
    return HELLO_LIST


def get_answer_map() -> Dict[str, str]:
    return ANS_MAP


def build_link_requests() -> List[EchoRequest]:
    requests = []
    for _ in range(3):
        requests.insert(0, EchoRequest(
            timestamp_ns(), "JAVA", get_random_id()))
    return requests


def timestamp_ns() -> int:
    return time.time_ns()


def timestamp():
    return int(round(time.time() * 1000))


def get_random_ids(max_value: int) -> List[str]:
    return [get_random_id() for _ in range(max_value)]


def get_random_id() -> str:
    return str(random.randint(0, 5))


def build_results(*ids: str) -> List[EchoResult]:
    return [build_result(idx) for idx in ids]


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
        "meta": "PYTHON",
    }
    return EchoResult(idx=int(uuid.uuid1().time), type=EchoType.OK, kv=kv)


def build_kiss_response() -> KissResponse:
    language, encoding = locale.getdefaultlocale()
    time_zone = time.tzname[0]
    return KissResponse(language, encoding, time_zone)


def build_kiss_request() -> KissRequest:
    os_version = platform.platform()
    os_name = platform.system()
    os_release = platform.release()
    os_architecture = platform.machine()
    return KissRequest(os_name, os_version, os_release, os_architecture)
