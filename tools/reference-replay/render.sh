#!/bin/sh
# render.sh <script> <outdir> [variant]: render a replay and record which script it used (#101 item 98).
# <script> and <outdir> are ABSOLUTE paths. Run from anywhere inside a checkout (or worktree); that checkout
# is the one built. Needs JAVA_HOME and ANDROID_HOME where the machine needs them, as for any Gradle run here.
# Writes <outdir>/replay-meta.json: the script's path and sha256, the variant, the checkout's commit and
# whether it was dirty. replay_meta.py reads it, and measure_rim_fringe.py refuses to score a replay whose
# script is not the one it scores against, so a baseline and a candidate cannot silently use different scripts.
set -e
script=$1; out=$2; variant=${3:-calm}
[ -n "$script" ] && [ -n "$out" ] || { echo "usage: render.sh <abs script> <abs outdir> [variant]" >&2; exit 2; }
root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
powershell -NoProfile -ExecutionPolicy Bypass -File "$root/tools/workspace.ps1" library :desktop:phoneReplay \
    "-Patlas.replay.script=$script" "-Patlas.replay.out=$out" "-Patlas.replay.variant=$variant" --console=plain \
    > "$out.log" 2>&1 || { echo "render failed, see $out.log" >&2; exit 1; }
sha=$(sha256sum "$script" | cut -d' ' -f1)
rev=$(git -C "$root" rev-parse HEAD)
dirty=false; [ -n "$(git -C "$root" status --porcelain --untracked-files=no)" ] && dirty=true
printf '{"script": "%s", "script_sha256": "%s", "variant": "%s", "commit": "%s", "dirty": %s}\n' \
    "$script" "$sha" "$variant" "$rev" "$dirty" > "$out/replay-meta.json"
echo "frames=$(ls "$out" | grep -c '^f.*png$') script=$sha variant=$variant commit=$rev dirty=$dirty"
