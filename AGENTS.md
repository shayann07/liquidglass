# Working in this repository

liquidglass is a Compose Multiplatform library whose optics are **measured**, not styled. The
measurement material lives in this repository, so a change to the material can always be checked
against the evidence that produced it. Read this before moving files or changing a number.

## Current workflow

After an interruption or usage limit, read [NEXT-SESSION.md](dev/NEXT-SESSION.md) and
[TODO.md](dev/TODO.md) before starting tests or changing source. They identify the current
unverified experiment, completed checks and exact resume action; preserve all dirty work.

Use [WORKSPACE.md](dev/WORKSPACE.md). Atlas Studio lives in `apps/desktop/` and shares the
Loupe screen with the Android sample. The old independent workspaces are retired;
keep historical records as provenance and follow the single canonical workflow.

## Where everything is

| Path | What it is | Tracked? |
| :-- | :-- | :-- |
| `liquidglass/` | **the library module.** `src/commonMain` (Kotlin + SkSL shader sources), `src/androidMain` / `src/skikoMain` (the platform actuals; `skikoMain` is shared by desktop JVM and web Wasm), `src/jvmTest` (the render and behaviour suite) | yes |
| `liquidglass-material3/`, `liquidglass-bom/`, `gradle-plugin/` | glass Material 3 components, the BOM, the `dev.shayxo.liquidglass` Gradle plugin | yes |
| `ports/` | the library on other stacks: `npm` (web component), `skia` (CanvasKit), `web`, `react-native`, `flutter`, `dotnet` (SkiaSharp, WinForms, WPF, Avalonia, MAUI) | yes |
| `apps/` | the apps: `apps/sample` (Android sample, with the deterministic device fixtures), `apps/desktop` (Atlas Studio), `apps/web` (the Wasm demo). Gradle paths stay `:sample`, `:desktop`, `:web` | yes |
| `dev/` | maintainer working notes: `NEXT-SESSION.md`, `TODO.md`, `PROGRESS.md`, `WORKSPACE.md`, and `archive/` (retired workflows) | yes |
| `tools/` | maintainer scripts (`workspace.ps1`, `launch_desktop.bat`, probes and comparison tools) | yes |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew*` | **build and dependencies.** Versions are declared in `gradle/libs.versions.toml`; coordinates and POM in `gradle.properties` (`GROUP`, `VERSION_NAME`) | yes |
| `.github/` | CI: build, docs publish, and the tag-driven Maven Central release | yes |
| `docs/` | **the published documentation site** (mkdocs, `strict: true`, deployed by CI). `docs/research/` holds the research *write-ups* that are meant to be read on the web | yes |
| `review/` | **curated, published review evidence**: the review write-ups (`ASTRA-r13.md`, `ASTRA-r14.md`), test results, frames, videos and input evidence. Not part of the mkdocs site | yes |
| `research/` | **the research material** and its record: captures, decoded measurements, tools, figures, packets, competitor sources. Not published. `research/README.md` is the full record — model, code, tests, results | partly: `research/README.md` and a few `analysis/` documents are committed; the material and most of the writing stay local (see `.gitignore`) |
| `build/`, `.gradle/`, `.kotlin/`, `local.properties` | build output and machine-local settings | no |

The split that matters: **`docs/` is what readers see, `research/` is what the claims rest on.**
Never put capture material, APKs or frame dumps under `docs/` — mkdocs builds everything there.

## Rules

### Keep documentation and visuals current

The owner requires documentation on the go. Every meaningful API/behavior change must update
its user guide, CHANGELOG, `docs/development-status.md`, and any affected README/support table
in the same checkpoint. Update dev/PROGRESS, dev/TODO and dev/NEXT-SESSION as work proceeds, including failures.
Do not postpone these until final delivery. This requirement also applies to future goal work.

For visible changes, include inspected app-owned visual evidence under `review/`, with captions
stating source revision/backend, capture method and limits. Link/embed it in relevant guides;
do not copy raw/private recordings into `docs/`. Distinguish source measurements, authored
parameters, calibrated comparisons, regression passes and owner acceptance. Keep old evidence
labelled rather than silently replacing a failed baseline. Reuse unchanged valid tests.

Before publication run strict MkDocs, repository-link/visual-reference checks and staged whitespace
checks. Keep `research/README.md` and `docs/research/the-record.md` synchronized when either changes.
If the private generator is unavailable, apply the matching addendum manually as documented below.

### Repository changes

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
8. **New optics are on by default (from 1.1.0).** The owner retired the opt-in rule on 16 November 2026:
   new behaviour ships as the default, every change to the default look is listed under "Default look
   changed" in `CHANGELOG.md` with its evidence, and a previously verified path that moves gets its
   expected value re-stated with the reason, never a silently widened tolerance.
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
