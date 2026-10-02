#!/usr/bin/env python3
"""Fail if the docs link to a file in this repository that git does not track.

mkdocs --strict checks relative links, but the site also links to research material by absolute
GitHub URL (https://github.com/shayann07/liquidglass/blob/main/...). Those resolve only if the
path is committed; research/ is mostly local and ignored, so a link to it would 404 on the live
site. This script checks every such link in docs/**/*.md against `git ls-files`: the target must
be a tracked file, or a directory that contains tracked files.

Run from anywhere inside the repository:  python .github/scripts/check_repo_links.py
"""

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

LINK = re.compile(
    r"https://github\.com/shayann07/liquidglass/(?:blob|tree|raw)/main/([^\s)\]>\"'`]+)"
)


def main() -> int:
    root = Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    )
    tracked = set(
        subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=root, check=True, capture_output=True, text=True,
        ).stdout.split("\0")
    )
    tracked.discard("")
    dirs = {str(Path(p).parent.as_posix()) for p in tracked}
    for d in list(dirs):
        parts = d.split("/")
        for i in range(1, len(parts)):
            dirs.add("/".join(parts[:i]))

    dead = []
    checked = 0
    for md in sorted((root / "docs").rglob("*.md")):
        rel = md.relative_to(root).as_posix()
        for lineno, line in enumerate(md.read_text(encoding="utf-8").splitlines(), 1):
            for match in LINK.finditer(line):
                path = match.group(1).split("#", 1)[0].split("?", 1)[0]
                path = unquote(path).rstrip("/")
                checked += 1
                if path in tracked or path in dirs:
                    continue
                dead.append(f"{rel}:{lineno}: {path}")

    if dead:
        print(f"{len(dead)} link(s) to paths git does not track (they 404 on GitHub):")
        for entry in dead:
            print(f"  {entry}")
        return 1
    print(f"OK: {checked} GitHub link(s) in docs/ all point at tracked paths.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
