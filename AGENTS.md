# Working in this repository

liquidglass is a Compose Multiplatform library whose optics are **measured**, not styled. The
measurement material lives in this repository, so a change to the material can always be checked
against the evidence that produced it. Read this before moving files or changing a number.

## Where everything is

| Path | What it is | Tracked? |
| :-- | :-- | :-- |
| `liquidglass/` | **the library module.** `src/commonMain` (Kotlin + SkSL shader sources), `src/androidMain` / `src/jvmMain` (the platform actuals), `src/jvmTest` (the render and behaviour suite) | yes |
| `sample/` | the Android sample app, including the deterministic device fixtures | yes |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew*` | **build and dependencies.** Versions are declared in `gradle/libs.versions.toml`; coordinates and POM in `gradle.properties` (`GROUP`, `VERSION_NAME`) | yes |
| `.github/` | CI: build, docs publish, and the tag-driven Maven Central release | yes |
| `docs/` | **the published documentation site** (mkdocs, `strict: true`, deployed by CI). `docs/research/` holds the research *write-ups* that are meant to be read on the web | yes |
| `review/` | **curated, published review evidence**: the review write-ups (`ASTRA-r13.md`, `ASTRA-r14.md`), test results, frames, videos and input evidence. Not part of the mkdocs site | yes |
| `research/` | **the research material** and its record: captures, decoded measurements, tools, figures, packets, competitor sources. Not published. `research/README.md` is the full record — model, code, tests, results | partly: `research/README.md` and a few `analysis/` documents are committed; the material and most of the writing stay local (see `.gitignore`) |
| `build/`, `.gradle/`, `.kotlin/`, `local.properties` | build output and machine-local settings | no |

The split that matters: **`docs/` is what readers see, `research/` is what the claims rest on.**
Never put capture material, APKs or frame dumps under `docs/` — mkdocs builds everything there.

## Rules

1. **No commits, pushes, tags or releases unless the owner asks in that session.** Leaving the tree
   dirty is normal here; a release is a tag, and tags publish to Maven Central.
2. **Change the material only in this repository**, never in a consumer. Then
   `./gradlew publishToMavenLocal`, bump `liquidglass` in the consumer's `libs.versions.toml`, and
   verify on a device.
3. **Run the suite before claiming anything**: `./gradlew :liquidglass:jvmTest --rerun`. The tests
   render real frames and score them; they are the reason defects get found by machine rather than
   by eye.
4. **"It compiles" is not evidence.** Optical and motion changes are confirmed on a physical device,
   with a capture. Check `topResumedActivity` before injecting input — the device is shared and may
   be in use. Leave the candidate build installed, never a timing baseline.
5. **Keep provenance straight.** Every number is *measured*, *inherited* or *authored*, and the three
   are never mixed in prose. See `research/README.md` and
   `research/analysis/v3/MODEL-DECISIONS.md`.
6. **Do not silently re-fit a measured constant** to make a candidate look better; state the change
   and what it was scored against.
7. **Renaming folders under `research/` is expensive.** Hundreds of documents and scripts reference
   `analysis/`, `tools/` and their children by path. If you must, rewrite the references in the same
   pass and record it, as `research/analysis/MOVED.md` does.
8. **Opt-in for new optics.** New behaviour arrives behind a style flag with its default unchanged,
   so every previously verified path keeps its tolerance.
9. **The record has a generated twin.** `research/README.md` is the source of truth;
   `docs/research/the-record.md` is its copy on the site, with file links rewritten to GitHub.
   After editing the record, run `python research/tools/build_site_record.py --check` — it
   regenerates the page and runs `mkdocs build --strict`, and fails loudly if a link would break or
   a figure went missing.
   That script lives only in the owner's local research tree, not in git. In a fresh clone, edit
   `research/README.md` and `docs/research/the-record.md` together by hand, then run
   `mkdocs build --strict` and `python .github/scripts/check_repo_links.py` (which fails on any
   GitHub link to a path git does not track).

## Fast orientation

- What the library does and how: `docs/README.md`, `docs/how-it-works.md`.
- What is measured, how it was measured, and how confident it is: `research/README.md` — the
  complete record, opening with the mathematics of the V3 model. The published summaries are in
  `docs/research/`.
- The latest delivery: `review/ASTRA-r14.md`, with `review/ASTRA-r13.md` for the last
  physical-device evidence. `research/analysis/v3/V3-HANDOFF.md` is the historical V3 record
  (superseded); its failing endpoint gate is still open and still reported as failing.
- Releasing: `docs/publishing.md` (the signing passphrase must stay ASCII).
