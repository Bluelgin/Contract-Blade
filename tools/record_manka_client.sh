#!/usr/bin/env bash
set -euo pipefail
: "${DISPLAY:?A running Minecraft X11 display is required}"
file=${1:?Absolute output MP4 path required}
seconds=${2:-180}
case "$file" in /*.mp4) ;; *) echo 'Use an absolute MP4 path' >&2; exit 2;; esac
ffmpeg -hide_banner -nostdin -y -f x11grab -video_size 1280x720 -framerate 30 -i "$DISPLAY" \
  -t "$seconds" -an -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -movflags +faststart "$file"
