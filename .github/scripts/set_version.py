"""Set the library's version everywhere it is written down.

    python .github/scripts/set_version.py 0.2.0             # before tagging v0.2.0
    python .github/scripts/set_version.py 0.3.0-SNAPSHOT    # after the release, back to development

One number covers every artifact: gradle.properties VERSION_NAME is read by the Gradle builds (the
library, Material 3, BOM and plugin), the NuGet packages (ports/dotnet/Package.props) and the npm job
(which stamps it at publish time). The .NET lock files record it too, for the demos' project
references, so they are rewritten here rather than by a restore. The Flutter package (pub.dev
`liquiglass`) writes it in ports/flutter/pubspec.yaml with -SNAPSHOT spelt -dev, as Dart does, and heads
its CHANGELOG with it (a -dev heading is renamed, a released one gets a new heading above it); the
Unity package's package.json takes the same spelling.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
# SemVer 2.0 without build metadata: what Maven, npm and NuGet all accept.
VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?")


def read(path: pathlib.Path) -> str:
    with open(path, encoding="utf-8", newline="") as f:  # keep the file's own line endings
        return f.read()


def write(path: pathlib.Path, text: str) -> None:
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def pub_version(version: str) -> str:
    """The Flutter package's spelling: pub.dev orders -dev before the release, as Maven does -SNAPSHOT."""
    return version.replace("-SNAPSHOT", "-dev")


def main() -> int:
    if len(sys.argv) != 2 or not VERSION.fullmatch(sys.argv[1]):
        print(__doc__)
        return 2
    new = sys.argv[1]
    props = ROOT / "gradle.properties"
    text = read(props)
    old = re.search(r"^VERSION_NAME=([^\r\n]+)", text, re.M).group(1).strip()
    if old == new:
        print(f"Already {new}")
        return 0
    changed = [props]
    write(props, text.replace(f"VERSION_NAME={old}", f"VERSION_NAME={new}", 1))
    for lock in sorted((ROOT / "ports" / "dotnet").glob("*/packages.lock.json")):
        body = read(lock)
        pinned = f'"[{old}, )"'
        if pinned in body:
            write(lock, body.replace(pinned, f'"[{new}, )"'))
            changed.append(lock)
    old_pub, new_pub = pub_version(old), pub_version(new)
    pubspec = ROOT / "ports" / "flutter" / "pubspec.yaml"
    body = read(pubspec)
    line = re.search(r"(?m)^version: (\S+)", body)
    if line and line.group(1) == old_pub:
        write(pubspec, body[:line.start(1)] + new_pub + body[line.end(1):])
        changed.append(pubspec)
    upm = ROOT / "ports" / "unity" / "com.shayxo.liquidglass" / "package.json"
    if upm.exists():
        body = read(upm)
        line = re.search(r'"version": "([^"]+)"', body)
        if line and line.group(1) == old_pub:
            write(upm, body[:line.start(1)] + new_pub + body[line.end(1):])
            changed.append(upm)
    log = ROOT / "ports" / "flutter" / "CHANGELOG.md"
    body = read(log)
    heading = re.search(r"(?m)^## (\S+)[ \t]*$", body)
    if heading and heading.group(1) != new_pub:
        if heading.group(1).endswith("-dev"):  # the unreleased entry takes the new number
            body = body[:heading.start(1)] + new_pub + body[heading.end(1):]
        else:  # keep the released entry, open a new one above it
            note = "In development." if new_pub.endswith("-dev") else f"Published with the library's {new}."
            body = body[:heading.start()] + f"## {new_pub}\n\n- {note}\n\n" + body[heading.start():]
        write(log, body)
        changed.append(log)
    for path in changed:
        print(f"{old} -> {new}: {path.relative_to(ROOT).as_posix()}")
    if "SNAPSHOT" not in new:
        notes = ROOT / ".github" / "release-notes" / f"v{new}.md"
        print(f"Release notes: {notes.relative_to(ROOT).as_posix()} {'found' if notes.exists() else 'MISSING (the publish workflow requires it)'}")
        print("Also give CHANGELOG.md its dated heading, commit, merge, wait for CI on main, then tag:")
        print(f"  git tag v{new} <the merge commit on main> && git push origin v{new}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
