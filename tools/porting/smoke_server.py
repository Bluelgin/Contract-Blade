#!/usr/bin/env python3
"""Opt-in native server smoke. Never operates on or copies a player world."""
import os
import queue
import re
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "run" / "port-smoke-server"
WORLD.mkdir(parents=True, exist_ok=True)
(WORLD / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(WORLD / "server.properties").write_text(
    "online-mode=false\n"
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
finally:
    if proc.poll() is None:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
            proc.wait(timeout=12)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try: os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError: pass
