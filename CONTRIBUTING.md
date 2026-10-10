# Contributing

Contributions should make the material easier to use, more predictable, or better supported by evidence.
Discuss broad API or renderer changes in an issue before implementing an incompatible interface.

## Development

Use JDK 21 and Android SDK 37 (`ANDROID_HOME` or `local.properties`). The checked-in Gradle wrapper
is the build entry point. On the owner's machine, follow [WORKSPACE.md](WORKSPACE.md) for the private
Gradle/Maven directories. Do not commit caches, signing credentials, device identifiers or local paths.

```sh
./gradlew :desktop:run
./gradlew :liquidglass:jvmTest --rerun
./gradlew :desktop:desktopTest :sample:assembleDebug
python -m pip install -r requirements-docs.txt
python .github/scripts/check_repo_links.py
python -m mkdocs build --strict
```

The renderer suite uses real Skia pixels and can take several minutes. Atlas's desktop tests render
complex scenes in software: their wall time is not native GPU performance. Use focused test filters
while iterating, then run the complete suite before proposing an optical or interaction change.
When both are requested, Gradle runs Atlas tests after the library's renderer suite to avoid competing
CPU rendering workers. Running only `:desktop:desktopTest` does not force the library suite to run.

For portable changes, run `npm ci --ignore-scripts` and `npm test` in the affected `ports/web` or
`ports/skia` directory. `npm run example` in the Skia directory renders the high-level integration
example. Production shader changes also require `GlassShaderBundleTest`: checked-in SkSL and reference
pixels are declared Gradle inputs, so editing them invalidates cached verification.
Browser tests live in `ports/web`; install both portable packages before `npm run test:browser` there,
since the suite checks the small WebGL renderer and the production Skia painter separately.

## Evidence and compatibility

- Label constants **measured**, **inherited** or **authored**. Never turn a visual approximation into
  a claimed Apple measurement. Untimed screenshots cannot establish finger velocity or spring timing.
- Include a minimal reproduction and a rendered regression for optical defects. Use ramps, text,
  grids and both light/dark backdrops; a pleasing screenshot on one background is insufficient.
- For gestures, cover cancellation, a competing scroll, reversal, release, extreme screen-edge travel,
  density changes and reduced motion. Separate layout movement, press expansion and material strain.
- Preserve historical public presets unless a breaking change is explicitly proposed. New behaviour
  should have a small, documented opt-in path before becoming a default.
- Prefer modifiers for surfaces. A host such as `GlassScene` may own backdrop lifetime and connect
  modifiers; it should not duplicate your app's layout, button semantics or navigation logic.
- Keep new rendering models independent of Android activities or demo-specific coordinates. A port
  must document its source-texture, colour, alpha and gesture contracts; see [porting](docs/porting.md).

Use Kotlin's existing formatting and keep public KDoc, API docs and examples together. Avoid adding
dependencies for utilities the standard library already provides. Keep runtime work allocation-aware;
measure performance before claiming an improvement.

## Pull requests

The selector regression writes `liquidglass/build/reports/atlas/selector-travel.csv`.
To regenerate its scientific plot, install matplotlib in a local virtual environment and run
`python tools/plot_selector_travel.py` (verified with matplotlib 3.11.2). This optional plotting
dependency is not part of the library runtime or normal CI build.
After `GlassRestTapReferenceTest`, `python tools/plot_selector_arrival.py` plots resting-tap
travel and the separate arrival recovery using the same optional environment.

Explain the user-visible problem, resulting behaviour, evidence and limitations. Include relevant
screenshots or traces under `review/`; only capture the app under test and remove private information.
Use the PR template. Tests and docs must pass, conversations must be resolved and the required review
must be obtained. Dependency bots propose changes; they do not approve or merge their own updates.

## Documentation, licensing and provenance

User documentation belongs in `docs/`, which builds the public site. Update README, the API reference
and CHANGELOG for new public features. If the research record is edited, keep its generated site twin
in sync. Check all links, including GitHub links to assets that must actually be tracked.

Contributions are accepted under the project's Apache-2.0 license. Submit only code you have the right
to contribute. Keep existing notices, attribute borrowed implementations in NOTICE, and document third-party
asset terms. Do not add extracted Apple shaders, system assets or SF Symbols to the runtime. User-supplied
reference images are evidence; they do not grant a license to redistribute Apple's UI as application assets.

See [SECURITY.md](SECURITY.md) for private vulnerability reports and [publishing](docs/publishing.md)
for maintainer-only releases. A source PR does not authorize a Maven release or a version tag.
