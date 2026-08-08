#!/bin/sh

set -eu

battle_background_asset="${1:-public/assets/cleaning-court-clear.png}"

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to verify the battle background" >&2
  exit 2
fi

battle_background_stats="$({
  ffmpeg \
    -hide_banner \
    -loglevel error \
    -i "$battle_background_asset" \
    -vf "crop=720:135:476:295,signalstats,metadata=print:file=-" \
    -frames:v 1 \
    -f null -
} 2>/dev/null)"

battle_background_ylow="$(printf '%s\n' "$battle_background_stats" | sed -n 's/^lavfi\.signalstats\.YLOW=//p')"
battle_background_yavg="$(printf '%s\n' "$battle_background_stats" | sed -n 's/^lavfi\.signalstats\.YAVG=//p')"

awk \
  -v ylow="$battle_background_ylow" \
  -v yavg="$battle_background_yavg" \
  'BEGIN {
    if (ylow < 100 || yavg < 145) {
      printf "Writing field is obstructed (YLOW %.1f, YAVG %.1f)\n", ylow, yavg > "/dev/stderr"
      exit 1
    }
    printf "Writing field is clear (YLOW %.1f, YAVG %.1f)\n", ylow, yavg
  }'
