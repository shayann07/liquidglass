#!/usr/bin/env bash
# Replay a scene-coordinate touch script (`select`, `<ms> down|move|up <x> <y>`, `end <ms>`)
# on a connected Pixel 7 via a single `adb shell` session while capturing a screenrecord,
# then pull the recording and cut 60 fps bar-cropped PNG frames (`f%05d.png`).
#
# Usage:
#   replay_script.sh <script.txt> [out-dir] [--print-events]
#
# Environment overrides:
#   ADB       adb command + flags (default: D:/DevSetup/Android/Sdk/platform-tools/adb.exe -s 29211FDH200FT8)
#   PKG       target package/activity guard (default: com.wexpa.liquidglass.sample)
#   SCALE     device_px / scene_px scale factor (default: 0.875, i.e. 2.625 / 3)
#   X_OFFSET  added after scaling x in device px (default: 0; PhoneActivity 1080x2400 uses ~28)
#   Y_OFFSET  added after scaling y in device px (default: 0; PhoneActivity 1080x2400 uses ~1723)
#   TAG       output/recording tag (default: script basename without extension)
#   CROP      ffmpeg crop `w:h:x:y` for bar region, excluding status bar (default: 1080:614:0:1786)
#   LEAD_SEC  seconds to wait after starting screenrecord before injecting input (default: 0.8)

set -euo pipefail

export MSYS_NO_PATHCONV=1
export PYTHONDONTWRITEBYTECODE=1
export PYTHONUTF8=1

PRINT_EVENTS=0
POSITIONAL=()
for arg in "$@"; do
  case "$arg" in
    --print-events) PRINT_EVENTS=1 ;;
    -h|--help)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    *) POSITIONAL+=("$arg") ;;
  esac
done

if (( ${#POSITIONAL[@]} < 1 || ${#POSITIONAL[@]} > 2 )); then
  echo "Usage: $0 <script.txt> [out-dir] [--print-events]" >&2
  exit 2
fi

SCRIPT="${POSITIONAL[0]}"
if [[ ! -f "$SCRIPT" ]]; then
  echo "ERROR: script not found: $SCRIPT" >&2
  exit 1
fi

SCRIPT_BASE="$(basename "$SCRIPT")"
TAG="${TAG:-${SCRIPT_BASE%.*}}"
OUT_DIR="${POSITIONAL[1]:-.local/pixel/cap/${TAG}}"
ADB="${ADB:-D:/DevSetup/Android/Sdk/platform-tools/adb.exe -s 29211FDH200FT8}"
PKG="${PKG:-com.wexpa.liquidglass.sample}"
SCALE="${SCALE:-0.875}"
X_OFFSET="${X_OFFSET:-0}"
Y_OFFSET="${Y_OFFSET:-0}"
CROP="${CROP:-1080:614:0:1786}"
LEAD_SEC="${LEAD_SEC:-0.8}"

read -r -a ADB_CMD <<< "$ADB"

find_python() {
  local cand
  for cand in python python3; do
    if command -v "$cand" >/dev/null 2>&1 && "$cand" -B -c "import sys" >/dev/null 2>&1; then
      echo "$cand"
      return 0
    fi
  done
  echo "ERROR: no working python/python3 interpreter found on PATH" >&2
  return 1
}

PY_BIN="$(find_python)"

CONVERTED="$("$PY_BIN" -B - "$SCRIPT" "$SCALE" "$X_OFFSET" "$Y_OFFSET" <<'PYEOF'
import sys

script_path, scale_s, xoff_s, yoff_s = sys.argv[1:5]
scale = float(scale_s)
x_off = float(xoff_s)
y_off = float(yoff_s)

events = []
end_ms = None
with open(script_path, encoding='utf-8') as f:
    for lineno, raw in enumerate(f, 1):
        w = raw.strip().split()
        if not w or w[0].startswith('#'):
            continue
        if w[0] == 'select':
            continue
        if w[0] == 'end':
            end_ms = int(w[1])
            continue
        if len(w) >= 4 and w[1] in ('down', 'move', 'up'):
            events.append((int(w[0]), w[1].upper(), float(w[2]), float(w[3])))
        else:
            sys.exit(f"Unrecognized line {lineno} in {script_path}: {raw.strip()}")

if not events:
    sys.exit(f"No touch events found in {script_path}")

lines = []
prev_ms = 0
is_down = False
last_xy = None
safety_up = False

for t_ms, action, x_sc, y_sc in events:
    dt_ms = t_ms - prev_ms
    if dt_ms < 0:
        sys.exit(f"Non-monotonic timestamp {t_ms} < {prev_ms} in {script_path}")
    if dt_ms > 0:
        lines.append(f"sleep {dt_ms / 1000.0:.3f}")
    x_dev = int(round(x_sc * scale + x_off))
    y_dev = int(round(y_sc * scale + y_off))
    lines.append(f"input motionevent {action} {x_dev} {y_dev}")
    if action == 'DOWN':
        is_down = True
    elif action == 'UP':
        is_down = False
    last_xy = (x_dev, y_dev)
    prev_ms = t_ms

final_ms = end_ms if end_ms is not None and end_ms >= prev_ms else prev_ms
if end_ms is not None and end_ms > prev_ms:
    dt_ms = end_ms - prev_ms
    lines.append(f"sleep {dt_ms / 1000.0:.3f}")
    prev_ms = end_ms

if is_down and last_xy is not None:
    lines.append(f"# safety UP synthesized at end {prev_ms} ms (script ended with pointer DOWN)")
    lines.append(f"input motionevent UP {last_xy[0]} {last_xy[1]}")
    safety_up = True

sys.stderr.write(
    f"replay_script: events={len(events)} end_ms={final_ms} scale={scale} "
    f"offset=({x_off:g},{y_off:g}) safety_up={safety_up}\n"
)
sys.stdout.write('\n'.join(lines) + '\n')
PYEOF
)"

if (( PRINT_EVENTS == 1 )); then
  printf '%s\n' "$CONVERTED"
  exit 0
fi

# Verify foreground activity before touching screenrecord or input injection.
TOP_ACTIVITY="$("${ADB_CMD[@]}" shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'topResumedActivity|mResumedActivity' || true)"
if [[ "$TOP_ACTIVITY" != *"PhoneActivity"* && "$TOP_ACTIVITY" != *"${PKG}"* && "$TOP_ACTIVITY" != *"wexpa.liquidglass"* ]]; then
  echo "ERROR: Refusing input injection; expected PhoneActivity or ${PKG} in foreground, got: ${TOP_ACTIVITY:-<none>}" >&2
  exit 1
fi

END_MS="$("$PY_BIN" -B - "$SCRIPT" <<'PYEOF'
import sys
end_ms = 5000
last_ms = 0
with open(sys.argv[1], encoding='utf-8') as f:
    for line in f:
        w = line.strip().split()
        if w and w[0] == 'end':
            end_ms = int(w[1])
        elif len(w) >= 4 and w[1] in ('down', 'move', 'up'):
            last_ms = max(last_ms, int(w[0]))
print(max(end_ms, last_ms))
PYEOF
)"

REC_LIMIT=$(( (END_MS + 999) / 1000 + 3 ))
if (( REC_LIMIT < 4 )); then REC_LIMIT=4; fi

mkdir -p "$OUT_DIR"
REMOTE_MP4="${REMOTE_MP4:-/sdcard/replay.mp4}"
LOCAL_MP4="${OUT_DIR}/replay_${TAG}.mp4"
REC_PID=""

cleanup() {
  if [[ -n "$REC_PID" ]]; then
    "${ADB_CMD[@]}" shell pkill -INT screenrecord >/dev/null 2>&1 || true
    wait "$REC_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

"${ADB_CMD[@]}" shell rm -f "$REMOTE_MP4"
"${ADB_CMD[@]}" shell screenrecord --time-limit "$REC_LIMIT" "$REMOTE_MP4" &
REC_PID=$!

sleep "$LEAD_SEC"

"${ADB_CMD[@]}" shell <<EOF
$CONVERTED
EOF

sleep 0.3
"${ADB_CMD[@]}" shell pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$REC_PID" >/dev/null 2>&1 || true
REC_PID=""
sleep 0.4

"${ADB_CMD[@]}" pull "$REMOTE_MP4" "$LOCAL_MP4"
"${ADB_CMD[@]}" shell rm -f "$REMOTE_MP4"

rm -f "${OUT_DIR}"/f*.png
ffmpeg -v error -y -i "$LOCAL_MP4" -vf "fps=60,crop=${CROP}" "${OUT_DIR}/f%05d.png"
FRAME_COUNT="$(find "$OUT_DIR" -maxdepth 1 -name 'f*.png' | wc -l | tr -d ' ')"
echo "Saved ${FRAME_COUNT} frames to ${OUT_DIR} (crop=${CROP})"
