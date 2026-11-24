#!/usr/bin/env python3
"""Opt-in native server smoke. Never operates on or copies a player world."""
import os
import queue
import signal
import secrets
import socket
import struct
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "run" / "port-smoke-server"
WORLD.mkdir(parents=True, exist_ok=True)
# Keep RCON on the local disposable CI server; never expose or log credentials.
with socket.socket() as reserved:
    reserved.bind(("127.0.0.1", 0))
    RCON_PORT = reserved.getsockname()[1]
RCON_PASSWORD = secrets.token_hex(24)
(WORLD / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(WORLD / "server.properties").write_text(
    "online-mode=false\n"
    "server-ip=127.0.0.1\n"
    "enable-rcon=true\n"
    f"rcon.port={RCON_PORT}\n"
    f"rcon.password={RCON_PASSWORD}\n"
    "broadcast-rcon-to-ops=false\n"
    "level-name=contract-port-smoke-world\n"
    "motd=Contract Blade port test - disposable world\n"
    "enable-command-block=false\n"
    "max-players=1\n", encoding="utf-8")
def recv_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        part = sock.recv(size - len(data))
        if not part:
            raise OSError("RCON connection closed")
        data.extend(part)
    return bytes(data)

def send_packet(sock, request_id, kind, value):
    data = struct.pack("<ii", request_id, kind) + value.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)

def read_packet(sock):
    size = struct.unpack("<i", recv_exact(sock, 4))[0]
    if not 10 <= size <= 65536:
        raise OSError("Invalid RCON packet size")
    data = recv_exact(sock, size)
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind

def rcon_command(command):
    deadline = time.monotonic() + 15
    while True:
        try:
            connection = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=2)
            break
        except OSError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.25)
    with connection as sock:
        sock.settimeout(110)
        send_packet(sock, 1, 3, RCON_PASSWORD)
        request_id, kind = read_packet(sock)
        if request_id != 1 or kind != 2:
            raise OSError("RCON authentication rejected")
        send_packet(sock, 2, 2, command)
        request_id, kind = read_packet(sock)
        if request_id != 2 or kind != 0:
            raise OSError("RCON command acknowledgement missing")

def boot_and_verify(command, markers):
    output = queue.Queue()
    print(f"Launching isolated NeoForge + TLM: {command}", flush=True)
    proc = subprocess.Popen(["./gradlew", "--no-daemon", "runServer"], cwd=ROOT,
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1, start_new_session=True)

    def reader():
        for line in proc.stdout:
            print(line, end="", flush=True)
            output.put(line)
        output.put(None)

    threading.Thread(target=reader, daemon=True).start()
    success = False
    try:
        deadline = time.monotonic() + 330
        loaded = ready = False
        while time.monotonic() < deadline:
            try:
                line = output.get(timeout=.5)
            except queue.Empty:
                if proc.poll() is not None:
                    raise RuntimeError("Server exited during startup")
                continue
            if line is None:
                raise RuntimeError("Server output closed during startup")
            if "Contract Blade NeoForge port probe loaded" in line:
                loaded = True
            if "Done (" in line and "For help" in line:
                ready = True
                break
            if "Crash report saved" in line or "Failed to start the minecraft server" in line:
                raise RuntimeError("Server startup crash")
        if not loaded or not ready:
            raise RuntimeError("Native NeoForge + TLM startup failed")
        rcon_command(command)
        print("PORT_NATIVE_COMMAND_ACK: " + command, flush=True)
        missing = set(markers)
        deadline = time.monotonic() + 120
        while missing and time.monotonic() < deadline:
            try:
                line = output.get(timeout=.5)
            except queue.Empty:
                if proc.poll() is not None:
                    break
                continue
            if line is None:
                break
            if "PORT_NATIVE_TLM_ROUNDTRIP_FAIL" in line or "PORT_NATIVE_RESTART_FAIL" in line:
                raise RuntimeError("Native verification failed")
            missing = {marker for marker in missing if marker not in line}
        if missing:
            raise RuntimeError("Missing verification markers: " + ", ".join(sorted(missing)))
        # Explicit clean stop, not SIGTERM, before restarting the SAME world.
        try:
            rcon_command("stop")
        except OSError:
            # The server may close its RCON connection during shutdown.
            pass
        if proc.wait(timeout=90) != 0:
            raise RuntimeError("Native server did not exit cleanly")
        success = True
        print("PORT_NATIVE_CLEAN_STOP: " + command, flush=True)
    finally:
        if not success and proc.poll() is None:
            try:
                os.killpg(proc.pid, signal.SIGTERM)
                proc.wait(timeout=12)
            except (ProcessLookupError, subprocess.TimeoutExpired):
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass

boot_and_verify("contractportverify", {
    "PORT_NATIVE_TLM_ROUNDTRIP_PASS",
    "PORT_NATIVE_TLM_WORLD_TRANSFER_PASS",
    "PORT_NATIVE_CHANNEL_LIFECYCLE_PASS",
    "PORT_NATIVE_ULTIMATE_BAUBLES_PASS",
    "PORT_NATIVE_RESTART_FIXTURE_PREPARED",
})
boot_and_verify("contractportverifyrestart", {"PORT_NATIVE_RESTART_PASS"})
print("PORT_NATIVE_ROUNDTRIP_READY: native transfer, channels, baubles and actual clean restart verified", flush=True)
