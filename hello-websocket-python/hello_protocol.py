import json
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List

TCP_PORT = 9800
TLS_PORT = 9886
HOST = "127.0.0.1"


@dataclass
class EchoRequest:
    id: int
    meta: str
    data: str

    def to_bytes(self) -> bytes:
        id_bytes = self.id.to_bytes(
            8, byteorder="big"
        )  # Use 8 bytes for larger integers
        meta_bytes = self.meta.encode("utf-8")
        data_bytes = self.data.encode("utf-8")
        return (
            id_bytes
            + len(meta_bytes).to_bytes(4, byteorder="big")
            + meta_bytes
            + len(data_bytes).to_bytes(4, byteorder="big")
            + data_bytes
        )

    @staticmethod
    def from_bytes(data: bytes) -> "EchoRequest":
        id_bytes = data[:8]  # Adjust to 8 bytes
        request_id = int.from_bytes(id_bytes, byteorder="big")
        meta_length = int.from_bytes(data[8:12], byteorder="big")
        meta = data[12 : 12 + meta_length].decode("utf-8")
        data_length = int.from_bytes(
            data[12 + meta_length : 16 + meta_length], byteorder="big"
        )
        data_str = data[16 + meta_length : 16 + meta_length + data_length].decode(
            "utf-8"
        )
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
        idx_bytes = self.idx.to_bytes(8, byteorder="big")
        type_bytes = self.type.value.to_bytes(4, byteorder="big")
        kv_bytes = json.dumps(self.kv).encode("utf-8")
        kv_length_bytes = len(kv_bytes).to_bytes(4, byteorder="big")
        return idx_bytes + type_bytes + kv_length_bytes + kv_bytes

    @staticmethod
    def from_bytes(data: bytes) -> ("EchoResult", bytes):
        idx = int.from_bytes(data[:8], byteorder="big")
        type_value = int.from_bytes(data[8:12], byteorder="big")
        type = EchoType(type_value)
        kv_length = int.from_bytes(data[12:16], byteorder="big")
        kv = json.loads(data[16 : 16 + kv_length].decode("utf-8"))
        remaining_data = data[16 + kv_length :]
        return EchoResult(idx=idx, type=type, kv=kv), remaining_data


@dataclass
class EchoResponse:
    status: int
    results: List[EchoResult]

    def to_bytes(self) -> bytes:
        status_bytes = self.status.to_bytes(4, byteorder="big")
        results_bytes = b"".join(result.to_bytes() for result in self.results)
        results_length_bytes = len(results_bytes).to_bytes(4, byteorder="big")
        return status_bytes + results_length_bytes + results_bytes

    @staticmethod
    def from_bytes(data: bytes) -> "EchoResponse":
        status = int.from_bytes(data[:4], byteorder="big")
        results_length = int.from_bytes(data[4:8], byteorder="big")
        results_bytes = data[8 : 8 + results_length]
        results = []
        while results_bytes:
            result, results_bytes = EchoResult.from_bytes(results_bytes)
            results.append(result)
        return EchoResponse(status=status, results=results)


class KissRequest:
    def __init__(self, os_name, os_version, os_release, os_architecture):
        self.os_name = os_name
        self.os_version = os_version
        self.os_release = os_release
        self.os_architecture = os_architecture

    def to_dict(self):
        return {
            "os_name": self.os_name,
            "os_version": self.os_version,
            "os_release": self.os_release,
            "os_architecture": self.os_architecture,
        }


class KissResponse:
    def __init__(self, language, encoding, time_zone):
        self.language = language
        self.encoding = encoding
        self.time_zone = time_zone

    def to_dict(self):
        return {
            "language": self.language,
            "encoding": self.encoding,
            "time_zone": self.time_zone,
        }
