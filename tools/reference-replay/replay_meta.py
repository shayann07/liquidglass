"""Which input script a replay was rendered from (#101 item 98).

Replays of one recording are only comparable when they come from the same script: a baseline rendered from
`script3.txt` and a candidate from `v2-script.txt` differ in finger timing, so any difference between them
is mostly the script. `render.sh` writes `replay-meta.json` beside the frames; scorers call `mismatch` and
refuse to compare across scripts.

    python tools/reference-replay/replay_meta.py check <replay dir> <script>   # exit 1 on a different script
    python tools/reference-replay/replay_meta.py pair  <replay dir A> <replay dir B>
"""
import hashlib, json, os, sys

META = 'replay-meta.json'


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 16), b''):
            h.update(block)
    return h.hexdigest()


def read(replay_dir):
    """The replay's metadata, or None for one rendered before this existed (nothing is claimed about it)."""
    p = os.path.join(replay_dir, META)
    return json.load(open(p)) if os.path.exists(p) else None


def mismatch(replay_dir, script):
    """None if the replay used `script` or does not say; else why it did not."""
    meta = read(replay_dir)
    if meta is None:
        return None
    want = sha256(script)
    if meta['script_sha256'] == want:
        return None
    return (f"{replay_dir} was rendered from {meta['script']} (sha256 {meta['script_sha256'][:12]}), "
            f"not from {script} ({want[:12]})")


def mismatch_pair(a, b):
    ma, mb = read(a), read(b)
    if ma is None or mb is None or ma['script_sha256'] == mb['script_sha256']:
        return None
    return f"{a} used {ma['script']} but {b} used {mb['script']}: render both from one script"


if __name__ == '__main__':
    if len(sys.argv) != 4 or sys.argv[1] not in ('check', 'pair'):
        print(__doc__); sys.exit(2)
    why = mismatch(sys.argv[2], sys.argv[3]) if sys.argv[1] == 'check' else mismatch_pair(sys.argv[2], sys.argv[3])
    print(why or 'ok (same script, or no metadata)')
    sys.exit(1 if why else 0)
