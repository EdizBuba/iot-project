#!/usr/bin/env python3

import argparse
import json
import socket
import sys
import threading
from dataclasses import dataclass
from typing import Optional, Set, Tuple

try:
    import serial
except ImportError:
    print("pyserial manquant. Installer avec: pip install pyserial", file=sys.stderr)
    sys.exit(1)


DisplayOrder = str
ClientAddress = Tuple[str, int]


@dataclass
class SensorData:
    sequence: int
    temperature: int
    light: int
    humidity: int
    pressure: int

    def to_payload(self) -> dict:
        return {
            "type": "data",
            "sequence": self.sequence,
            "temperature": self.temperature,
            "light": self.light,
            "humidity": self.humidity,
            "pressure": self.pressure,
            "T": self.temperature,
            "L": self.light,
            "H": self.humidity,
            "P": self.pressure,
        }


@dataclass
class ConfigAck:
    sequence: int
    order: DisplayOrder


def is_valid_display_order(order: str) -> bool:
    if not order or len(order) > 4:
        return False

    allowed = set("TLHP")
    seen = set()
    for char in order:
        if char not in allowed or char in seen:
            return False
        seen.add(char)
    return True


def parse_data_line(line: str) -> Optional[SensorData]:
    parts = line.split("|")
    if len(parts) != 6 or parts[0] != "DATA":
        return None

    try:
        return SensorData(
            sequence=int(parts[1]),
            temperature=int(parts[2]),
            light=int(parts[3]),
            humidity=int(parts[4]),
            pressure=int(parts[5]),
        )
    except ValueError:
        return None


def parse_config_ack_line(line: str) -> Optional[ConfigAck]:
    parts = line.split("|")
    if len(parts) != 3 or parts[0] != "CFG-ACK":
        return None

    try:
        sequence = int(parts[1])
    except ValueError:
        return None

    order = parts[2]
    if not is_valid_display_order(order):
        return None

    return ConfigAck(sequence=sequence, order=order)


class UdpUartGateway:
    def __init__(self, serial_port: str, baudrate: int, udp_host: str, udp_port: int):
        self.serial = serial.Serial(serial_port, baudrate=baudrate, timeout=0.1)
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.socket.bind((udp_host, udp_port))
        self.socket.setblocking(False)

        self.latest_data: Optional[SensorData] = None
        self.latest_config_ack: Optional[ConfigAck] = None
        self.subscribers: Set[ClientAddress] = set()

        self.data_lock = threading.Lock()
        self.ack_lock = threading.Lock()
        self.subscribers_lock = threading.Lock()

    def send_json(self, payload: dict, addr: ClientAddress) -> None:
        self.socket.sendto(json.dumps(payload).encode("utf-8"), addr)

    def broadcast_json(self, payload: dict) -> None:
        with self.subscribers_lock:
            subscribers = list(self.subscribers)

        dead = []
        for subscriber in subscribers:
            try:
                self.send_json(payload, subscriber)
            except OSError:
                dead.append(subscriber)

        if dead:
            with self.subscribers_lock:
                for subscriber in dead:
                    self.subscribers.discard(subscriber)

    def handle_serial_line(self, line: str) -> None:
        data = parse_data_line(line)
        if data is not None:
            with self.data_lock:
                self.latest_data = data
            self.broadcast_json(data.to_payload())
            return

        config_ack = parse_config_ack_line(line)
        if config_ack is not None:
            with self.ack_lock:
                self.latest_config_ack = config_ack
            self.broadcast_json(
                {
                    "type": "config_ack",
                    "sequence": config_ack.sequence,
                    "order": config_ack.order,
                }
            )
            return

        if line.startswith("CFG-ERR|"):
            self.broadcast_json(
                {
                    "type": "config_error",
                    "message": line.split("|", 1)[1],
                }
            )

    def send_config(self, order: str) -> None:
        self.serial.write(f"CFG|{order}\n".encode("utf-8"))
        self.serial.flush()

    def latest_data_payload(self) -> Optional[dict]:
        with self.data_lock:
            return self.latest_data.to_payload() if self.latest_data else None

    def latest_status_payload(self) -> dict:
        with self.data_lock:
            latest_data = self.latest_data.to_payload() if self.latest_data else None

        with self.ack_lock:
            latest_ack = (
                {
                    "sequence": self.latest_config_ack.sequence,
                    "order": self.latest_config_ack.order,
                }
                if self.latest_config_ack
                else None
            )

        with self.subscribers_lock:
            subscribers = len(self.subscribers)

        return {
            "type": "status",
            "latest_data": latest_data,
            "latest_config_ack": latest_ack,
            "subscribers": subscribers,
        }

    def handle_udp_message(self, message: str, addr: ClientAddress) -> None:
        normalized = message.strip()

        if normalized in {"subscribe()", "SUBSCRIBE", "SUBSCRIBE()"}:
            with self.subscribers_lock:
                self.subscribers.add(addr)
            self.send_json({"type": "subscribed"}, addr)
            return

        if normalized in {"unsubscribe()", "UNSUBSCRIBE", "UNSUBSCRIBE()"}:
            with self.subscribers_lock:
                self.subscribers.discard(addr)
            self.send_json({"type": "unsubscribed"}, addr)
            return

        if normalized in {"GET", "getValues()", "GET_VALUES"}:
            with self.subscribers_lock:
                self.subscribers.add(addr)
            payload = self.latest_data_payload()
            if payload is None:
                payload = {"type": "error", "message": "no_data_yet"}
            self.send_json(payload, addr)
            return

        if normalized in {"getStatus()", "GET_STATUS"}:
            with self.subscribers_lock:
                self.subscribers.add(addr)
            self.send_json(self.latest_status_payload(), addr)
            return

        order = normalized[4:] if normalized.startswith("CFG|") else normalized
        if is_valid_display_order(order):
            with self.subscribers_lock:
                self.subscribers.add(addr)
            self.send_config(order)
            self.send_json({"type": "config_sent", "order": order}, addr)
            return

        self.send_json({"type": "error", "message": "unsupported_command"}, addr)

    def serial_loop(self) -> None:
        while True:
            line = self.serial.readline().decode("utf-8", errors="ignore").strip()
            if not line:
                continue

            print(f"SERIAL> {line}")
            self.handle_serial_line(line)

    def udp_loop(self) -> None:
        print("Gateway server started")
        while True:
            try:
                payload, addr = self.socket.recvfrom(4096)
            except BlockingIOError:
                continue

            message = payload.decode("utf-8", errors="ignore")
            print(f"UDP<{addr[0]}:{addr[1]}> {message.strip()}")
            self.handle_udp_message(message, addr)

    def loop(self) -> None:
        thread = threading.Thread(target=self.serial_loop, daemon=True)
        thread.start()
        self.udp_loop()


def main() -> int:
    parser = argparse.ArgumentParser(description="UDP <-> UART gateway for Android and micro:bit")
    parser.add_argument("--serial-port", required=True, help="Serial device, e.g. /dev/cu.usbmodem14102")
    parser.add_argument("--baudrate", type=int, default=115200)
    parser.add_argument("--udp-host", default="0.0.0.0")
    parser.add_argument("--udp-port", type=int, default=10000)
    args = parser.parse_args()

    gateway = UdpUartGateway(args.serial_port, args.baudrate, args.udp_host, args.udp_port)
    gateway.loop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
