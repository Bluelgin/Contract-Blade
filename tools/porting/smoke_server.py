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
output = queue.Queue()
cmd = ["./gradlew", "--no-daemon", "runServer"]
print("Launching isolated NeoForge + TLM probe in run/port-smoke-server", flush=True)
proc = subprocess.Popen(cmd, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)

def reader():
    for line in proc.stdout:
        print(line, end="", flush=True)
        output.put(line)
    output.put(None)

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

threading.Thread(target=reader, daemon=True).start()
deadline = time.monotonic() + 330
loaded = False
ready = False
failure = ""
try:
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=0.5)
        except queue.Empty:
            if proc.poll() is not None:
                failure = "NeoForge process exited before the server was ready"
                break
            continue
        if line is None:
            failure = "Server output closed before successful startup"
            break
        if "Contract Blade NeoForge port probe loaded" in line:
            loaded = True
        if "Done (" in line and "For help" in line:
            ready = True
            break
        if "Crash report saved" in line or "Failed to start the minecraft server" in line:
            failure = "Native dedicated server reported startup crash"
            break
    if not failure and not ready:
        failure = "Native dedicated server startup deadline exceeded"
    if ready and not loaded:
        failure = "Server booted but the Contract Blade port probe did not register"
    if failure:
        print("PORT_NATIVE_SERVER_FAIL: " + failure, file=sys.stderr)
        raise SystemExit(1)
    print("PORT_NATIVE_SERVER_READY: NeoForge 1.21.1 + TLM + probe booted", flush=True)
    # Gradle's run task need not forward stdin to Minecraft. Use an explicit
    # acknowledged local command transport, then require both test markers.
    try:
        rcon_command("contractportverify")
    except OSError as error:
        print("PORT_NATIVE_COMMAND_FAIL: " + str(error), file=sys.stderr)
        raise SystemExit(1)
    print("PORT_NATIVE_COMMAND_ACK: contractportverify", flush=True)
    test_deadline = time.monotonic() + 100
    verified = False
    world_verified = False
    channels_verified = False
    ultimate_verified = False
    while time.monotonic() < test_deadline:
        try:
            line = output.get(timeout=0.5)
        except queue.Empty:
            if proc.poll() is not None:
                break
            continue
        if line is None:
            break
        if "PORT_NATIVE_TLM_ROUNDTRIP_PASS" in line:
            verified = True
        if "PORT_NATIVE_TLM_WORLD_TRANSFER_PASS" in line:
            world_verified = True
        if "PORT_NATIVE_CHANNEL_LIFECYCLE_PASS" in line:
            channels_verified = True
        if "PORT_NATIVE_ULTIMATE_BAUBLES_PASS" in line:
            ultimate_verified = True
        if verified and world_verified and channels_verified and ultimate_verified:
            break
        if "PORT_NATIVE_TLM_ROUNDTRIP_FAIL" in line:
            failure = "Native TLM snapshot round-trip rejected"
            break
    if not verified or not world_verified or not channels_verified or not ultimate_verified:
        print("PORT_NATIVE_ROUNDTRIP_FAIL: " + (failure or "No round-trip success marker"), file=sys.stderr)
        raise SystemExit(1)
    print("PORT_NATIVE_ROUNDTRIP_READY: in-memory and real-world TLM transfer tests passed", flush=True)
finally:
    if proc.poll() is None:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
            proc.wait(timeout=12)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try: os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError: pass
