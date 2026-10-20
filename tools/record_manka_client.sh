#!/usr/bin/env bash
set -euo pipefail
: "${DISPLAY:?A running Minecraft X11 display is required}"
file=${1:?Absolute output MP4 path required}
seconds=${2:-180}
case "$file" in /*.mp4) ;; *) echo 'Use an absolute MP4 path' >&2; exit 2;; esac
python3 "$(dirname "$0")/record_manka_live.py" "$file" --seconds "$seconds"
