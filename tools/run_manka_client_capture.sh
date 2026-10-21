#!/usr/bin/env bash
# Genuine client framebuffer capture; output stays outside the Git repository.
set -euo pipefail
seed_world="$1"
output="$2"
mkdir -p "$output"
{
  uname -a
  lscpu
  free -m
  java -version
  python --version
  ffmpeg -version
} > "$output/environment.txt" 2>&1
python tools/run_manka_client.py --gradle gradle --seed-world "$seed_world" --output "$output" &
client_pid=$!
trap 'kill "$client_pid" 2>/dev/null || true' EXIT
python tools/run_manka_capture_validation.py --log "$output/client.log" --output "$output/videos"
qa_status=0
python tools/run_manka_client_qa.py --client "$output/client" --output "$output/qa" || qa_status=$?
python tools/summarize_manka_metrics.py "$output/client/logs/latest.log" --output "$output/metrics.json"
python tools/summarize_manka_metrics.py "$output/client/logs/latest.log" --capture "$output/videos/manka_performance_test.capture.json" --output "$output/videos/performance-metrics.json"
if [ -f "$output/qa/manka_two_clients.capture.json" ]; then
  python tools/summarize_manka_metrics.py "$output/client/logs/latest.log" --capture "$output/qa/manka_two_clients.capture.json" --output "$output/qa/host-two-client-metrics.json"
  python tools/summarize_manka_metrics.py "$output/qa/observer/logs/latest.log" --capture "$output/qa/manka_two_clients.capture.json" --output "$output/qa/observer-two-client-metrics.json"
fi
python tools/write_manka_acceptance_report.py --output "$output" --seed-world "$seed_world"
# Save and quit through the native GUI at GUI scale 2, then close the title screen.
xdotool key Escape
sleep 1
xdotool mousemove 640 445 click 1
python - "$output/client.log" <<'PYWAIT'
import sys,time
from pathlib import Path
p=Path(sys.argv[1]);deadline=time.monotonic()+90
while time.monotonic()<deadline:
 s=p.read_text(errors='replace')
 if s.rfind('MANKA_SCREEN TitleScreen')>s.rfind('MANKA_SCREEN PauseScreen'):break
 time.sleep(.5)
else:raise SystemExit('Client did not return to title after saving')
PYWAIT
xdotool mousemove 744 464 click 1
wait "$client_pid"
trap - EXIT
exit "$qa_status"
