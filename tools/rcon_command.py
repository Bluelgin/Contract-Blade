#!/usr/bin/env python3
"""Tiny dependency-free Minecraft RCON client used by preview CI."""

from __future__ import annotations

import argparse
import socket
import struct


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        chunk = sock.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("RCON connection closed unexpectedly")
        chunks.extend(chunk)
    return bytes(chunks)


def encode_packet(request_id: int, packet_type: int, payload: str) -> bytes:
    body = (
        struct.pack("<ii", request_id, packet_type)
        + payload.encode("utf-8")
        + b"\x00\x00"
    )
    return struct.pack("<i", len(body)) + body


def receive_packet(sock: socket.socket) -> tuple[int, int, str]:
    length = struct.unpack("<i", recv_exact(sock, 4))[0]
    if length < 10 or length > 10_000_000:
        raise ValueError(f"invalid RCON packet length {length}")
    body = recv_exact(sock, length)
    request_id, packet_type = struct.unpack("<ii", body[:8])
    payload = body[8:-2].decode("utf-8", errors="replace")
    return request_id, packet_type, payload


def run(host: str, port: int, password: str, command: str) -> str:
    with socket.create_connection((host, port), timeout=10) as sock:
        sock.settimeout(10)

        auth_id = 100
        sock.sendall(encode_packet(auth_id, 3, password))
        response_id, _response_type, response = receive_packet(sock)
        if response_id == -1:
            raise PermissionError("RCON authentication failed")
        if response_id != auth_id:
            raise RuntimeError(
                f"unexpected RCON auth response id {response_id}: {response}"
            )

        command_id = 101
        sock.sendall(encode_packet(command_id, 2, command))
        response_id, _response_type, response = receive_packet(sock)
        if response_id != command_id:
            raise RuntimeError(
                f"unexpected RCON command response id {response_id}: {response}"
            )
        return response


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", required=True)
    args = parser.parse_args()

    response = run(args.host, args.port, args.password, args.command)
    if response:
        print(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
