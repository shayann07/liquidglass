# Resume LiquidGlass / Atlas Studio

## PAUSED 24 October — owner decisions to act on first

The owner paused here (usage limit). Resume with these, in order:

1. **#73 rim fringes: build iOS's behaviour, no opt-in.** The owner's words: "build the exact stuff like
   ios bruh no need for opt ins we need that". So this is a deliberate default change, exempt from the
   opt-in rule. Target, from review/atlas/rim-fringe-score-2026-10-24: fringes 30-100x today's strength,
   growing 5-30x with selector speed, starting 33-50 ms after a tap and peaking at 84-116 ms, peaking at
   lift on held gestures and fading 110-120 ms after, with warm hues (the white content splits too). Knobs:
   `GlassSelectorRimDispersion` (GlassTabBar.kt ~2235), the held lens's `dispersion` (~2089) and the shader
   split (GlassShaderSource.kt ~546). Score every candidate with tools/measure_rim_fringe.py on all five
   recordings, plus levels.py/hazescore.py so nothing else regresses; record before/after under review/.
2. **#77 refraction band: choose by eye against iOS.** The owner's words: "check which one looks visually
   more close to the ios references and then decide acc to that". Render the Calm lens band at 0.3
   (today), 0.6, 1.0 and 1.1 beside the iOS frames (all five recordings, including the bar's end cap and
   the fuse with the search button, where "two glasses" appeared on 18 Oct). Pick the closest, make it
   the default, and record the comparison and the reason (scorer: tools/reference-replay/refraction.py).
   #73's split scales with this band, so settle #77 first or score the two together.
   **Progress 24 Oct (second pause):** candidate replays are rendered (local, private):
   `.local/sxs/ref/<name>/r77-lband{06,1,11}` for IMG_6756, docs2, LOLL8185 and JGEY2190 (JGEY2190 1.1
   may be incomplete: check its frame count against 2882). Baseline 0.3 is `p20`, except docs2, whose
   candidates used `ref/docs2/script3.txt` rather than p20's `v2-script.txt`: render a docs2 0.3 baseline
   from script3 (`calm`) before comparing. Render with ABSOLUTE script/out paths (relative ones fail).
   Next: contact sheets iOS vs 0.3/0.6/1.0/1.1 at held, moving, end-cap and fuse moments; refraction.py
   on each; pick; change `bodyBandScale` at GlassTabBar.kt ~1671/1707; full suite; evidence.
3. Then #70, #72, the rest of #78 (load time needs a CDN/brotli decision from the owner), and #81
   (off-composition field build).

## #81 off-composition field build (24 October, cloud session, PR)

Size-only changes to a custom-path panel build the field on `Dispatchers.Default`; the previous field
stays until the new one is published, resizes during a build are conflated, a pending build is dropped
when the panel leaves. First field, empty-to-real size, and shape/pad/band/density/direction changes
stay synchronous. New `GlassPathFieldOffCompositionTest` (7, held dispatcher; the path is copied for queued builds); benchmark pixels
identical; full suite 437 total, 410 passed, 27 skips, 0 failures. ABI/Android not run locally (no
SDK): CI covers them. Remaining in #81: bounded-resolution build, device timing of the rim lag.
Record: review/atlas/pathfield-off-composition-2026-10-24/README.md.

## 24 October: open PRs landed, #89 fixed, #81 faster

All open PRs are merged (#83-#88). Issues closed: #71, #79, #80, #82, #89. Still open for 1.1.0:
#78 (load time: transfer, first-visit shader compile, never-renders cause), #81 (bounded-resolution or
off-composition field build; this session made the EDT ~15% faster, bit-identical), #70, #72, #73, #77.
#77 and #73 are measured (records below and in review/atlas/refraction-gap-2026-10-24 and
rim-fringe-score-2026-10-24); both wait on the owner's decision about an opt-in candidate.
The #86 fuse follow-up was not re-measured on the Pixel 7 (not connected); the tap path is unchanged by it.

## #73 rim fringe score (24 October, measurement only)

Scorer `tools/measure_rim_fringe.py` (= `.local/sxs/fringe.py`), record
`review/atlas/rim-fringe-score-2026-10-24/README.md`. iOS fringes are 30 to more than 100x ours on all five recordings,
grow with speed, peak at lift and fade +110-120 ms; ours near absent. Nothing changed in the library.
Resume: the owner decides whether to try the opt-in candidate described in the record; if yes, render a
sweep with rrw.sh and score each with the tool on all five (re-render IMG_6756/JGEY2190/LOLL8185 when memory allows;
they used p20, pixel-identical on docs/1 and docs/2).

## Compose cache/field fixes and PR #83 review follow-up (20 October)

CodeRabbit follow-up on `1654e90`: a fractional `recordPad` was incorrectly rounded again.
The red regression reproduced a 145/146-pixel extent mismatch at renderScale 0.75. The fix
preserves recording-grid padding exactly; size stays rounded to layout pixels. All 21 focused
tests/probes pass (41s), including scales 1, 0.75, 0.6, 0.5 and 0.25, and inspected fractional-pad
field evidence. Full renderer verification passes: 428 total, 401 passed, 27 opt-in skips, zero failures/errors
(28m45s). Android, Wasm, ABI, strict docs and all 139 repository links pass.
KDoc now describes changed lifecycle, buffer and test contracts. Keep #81 open for deferred work.

#80 and #82 are implemented; #81's capability guard, scratch reuse and documentation are done.
Bounded-resolution/off-composition work remains open. Review changes cap retained buffers at
256k pixels (~4.25 MiB), use matching size/pad for both cache and construction, add root-region and
pool-fallback regressions, and declare coroutines directly. Focused checks and the updated probe
pass on the earlier `1654e90` checkpoint (22 tests); its full renderer run passes with 427 tests, zero failures/errors and 27 opt-in
skips (one worker, 27m04s). Android sample, Wasm, ABI and strict docs/link checks also pass.
The updated allocation result is
72.5% lower for a small pooled field and 30% lower for large fields using temporary buffers;
CPU medians are slower in this run. Historical higher-cap measurements and initial test failures
are preserved in `review/atlas/compose-cache-field-2026-10-20/README.md`. No release/device claim.

Updated 14 October 2026 (Claude takeover from Codex). The previous, longer handoff is preserved
at commit `efcc3b5` and in `.local/handoffs/`. Full iOS parity and owner visual acceptance are
**not** established. Read [TODO.md](TODO.md) and the full objective before changing source.

## Web demo first load, #78 (20 October, PR #85)

Measured in headless Chrome 152 (ANGLE/D3D11, AMD Radeon integrated) with `tools/measure_web_demo.mjs`,
cold caches, three runs each. The demo downloads 4.46 MB gzip (skiko.wasm 3.28 MB, app 1.08 MB, atlas.js
0.1 MB); a first visit then spends about 2 s per opening frame compiling shaders. The black page lasted
7.6 s unthrottled, 11.5 s on fast 4G and 30 s on slow 4G; the loader now shows in 0.05-0.2 s. The time to
a usable app is unchanged (download plus shaders). Failure states (no Wasm GC, failed download or startup
error, lost WebGL context) are forced in Chromium only; Firefox, Safari and phones are not measured, and a
permanent black page was not reproduced. Evidence: review/atlas/web-demo-load-2026-10-20.

## Material 3 polish, #79 (20 October, PR #84)

- Library: `Slider` and `RangeSlider` tracks stopped half a thumb short of each end, because Material
  lays the track out between the thumb's centre positions and the glass track inset again; they now draw
  half a thumb past both ends. Half a thumb is 19 dp, derived from the authored 38 dp thumb width.
- Measured: `GlassSliderTrackTest` read the track starting 19 px in (density 1) before the fix, and at
  the slider's ends after it.
- Authored (demo layout, not measured): the mini player's 20 x 14 dp inset and 10 dp gap before the
  switch, the pause glyph, and the 280 dp cap on the hero cover on wide windows.
- Regression pass: `:liquidglass-material3:jvmTest` 20 of 20 (4 slider tests); `checkKotlinAbi` unchanged.
- Audit: every component on the page, including the menu, dialog, sheet and tooltip, inspected on the
  desktop and in the browser; desktop renders before/after, a Pixel 7 capture and browser captures in
  review/atlas/material3-polish-2026-10-20.

## RELEASED: v1.0.0 (20 Oct 2026)

Release PR #76 merged (4a71394) after CodeRabbit's review: the first-use TODO row stays open
under #71, the Flutter install is marked pending, measure.sh is portable, and the ABI dumps exclude
`@LiquidGlassDiagnosticApi`. Main CI was green; tag `v1.0.0` was pushed on 4a71394 with the owner's
pre-authorization. The Publish run (39348823435) passed every job. Confirmed: npm
`@shayxo.dev/liquidglass` and `-react-native` at 1.0.0; Maven Central POM at 1.0.0; GitHub Release
v1.0.0. NuGet accepted all five packages and their symbols; its search index was still catching up
when this was written, so check nuget.org. pub-dev stood down as designed: `liquiglass` does not exist
on pub.dev yet. Main is now 1.1.0-SNAPSHOT. **The open work is the "AFTER 1.0" list at the top of TODO.md.**

Owner, by hand: pub.dev first upload and automated publishing (docs/publishing.md "pub.dev,
once"); Pages HTTPS certificate (bad_authz; remove/re-add the custom domain, or wait); upload
docs/assets/social-card.png as the repository social preview; CodeRabbit trial (ends 2 Nov; the
repo is public, so reviews stay free); the selector review page .local/review-selector/index.html;
launch drafts drafts/launch-drafts.md.

## CURRENT RUN (20 Oct 2026) — seven items, one PR each, in order

Owner's list: 1 Flutter on pub.dev; 2 .NET MAUI (`LiquidGlass.Maui`, Pixel 7 captures); 3 Qt Quick
(ShaderEffect, shader generated from material.sksl, qsb only in CI on Linux, never locally: it opens
a GUI dialog); 4 Unity (URP/Built-in shader + UPM; Unity not installed, say what was verified);
5 iOS motion parity (open TODO rows, full dataset, `.local/sxs/hdr.py`); 6 navigation selector
review page, then ASK the owner; 7 v1.0.0 release PR, then ASK before merge/tag. No tags or
releases without the owner's word in that session. Every new publish.yml job needs
`if: needs.check.outputs.skip != 'true'`.

1. Flutter, branch `claude/flutter-pubdev` (session worktree .claude/worktrees/kind-allen-15337d):
   pub.dev name **`liquiglass`** (owner's choice; `liquidglass` is blocked by pub.dev's similarity
   rule against `liquid_glass`). pubspec metadata, CHANGELOG, doc/pixel7.png screenshot, platforms
   without iOS; dry run clean (only warning: dirty git). `set_version.py` writes the pubspec
   (-SNAPSHOT -> -dev) and the package CHANGELOG heading; CI checks it; `pub-dev` job in publish.yml
   (setup-dart OIDC, stands down until the package exists). Owner steps: docs/publishing.md
   "pub.dev, once". Status: MERGED (#61, c185827). Owner: first upload by hand, then automated
   publishing. Owner switched to direct pushes to main (no PRs) on 20 Oct.
2. MAUI, branch `claude/maui`: `LiquidGlass.Maui` (net10.0 / android / windows), MauiDemo (android),
   CI job `maui` (windows-2025, workloads maui-android + maui-windows), NuGet job packs it.
   Local SDK: `.local/dotnet` (10.0.402 + maui-android); set DOTNET_ROOT to it, pass
   -p:AndroidSdkDirectory=D:/DevSetup/Android/Sdk -p:JavaSdkDirectory=<JDK 21>. Release Android build
   needs UseDefaultPublishRuntimeIdentifier=false (else win-x64 Mono pack NU1102). Pixel 7 checked,
   evidence review/atlas/maui-2026-10-20. MAUI CaptureAsync on Android copies window pixels (glass saw
   itself): replaced by View.Draw.
Owner-approved side queue (20 Oct, after MAUI, before Qt): Pages HTTPS (cert re-requested 20 Oct,
was bad_authz), description and 20 topics DONE; tagline "Liquid Glass for every stack, measured
against iOS."; README rewrite, docs SEO + per-stack landing pages, repo reorg (ask first), help-wanted
issues + Discussions for what this machine cannot verify, launch drafts (do not post).
   Done 20 Oct: MAUI pushed (7d00749); issues #62-#67 (label needs-device; Discussions were already on);
   README rewrite, docs/for/*.md landing pages, docs/overrides/main.html (OG/Twitter), social card
   docs/assets/social-card.png (owner: upload it as the GitHub social preview, Settings > General).
   Pages cert: domain removed 02:51 and re-added 10 min later to force a new ACME order.
   Repo reorg (owner: "go hard"): apps/{sample,desktop,web} (settings.gradle.kts projectDir; Gradle
   paths unchanged), dev/{NEXT-SESSION,TODO,PROGRESS,WORKSPACE}.md + dev/archive, tools/launch_desktop.bat.
   Links rewritten (0 broken before/after); desktop, web and sample compile. Launch drafts:
   drafts/launch-drafts.md in the session worktree (untracked, git-excluded; never posted).
   MAUI CI NU1403 fixed (DisableImplicitLibraryPacksFolder; main 02e835e all green).
   Pages HTTPS: still bad_authz after API remove/re-add (02:51-03:21); owner may need Settings > Pages
   remove/save/re-add, or wait (Let's Encrypt caches failed authorizations). DNS checked correct.
3. Qt Quick, branch `claude/qt-port` (draft PR #68 = CI sandbox only; push commits to main, close it):
   ports/qt: tool/generate_shader.mjs (GLSL 440 + GlassEffect.qml), LiquidGlass/{GlassSurface.qml,
   material.js, qmldir}, examples/pyside (main.py --grab), test/material.test.mjs. CI job `qt`
   (ubuntu, PySide6 6.11.2, pyside6-qsb, xvfb + Mesa llvmpipe GL 4.5, QSG_RENDER_LOOP=basic).
   Fixed on the way: PySide can't convert QML types/enums (findChild, shaderStatus int, shiboken
   wrapInstance to QQuickWindow); FrameAnimation made opt-in (followsMovement); backdropColor (uBase)
   for transparent backdrop parts. Bar glass confirmed rendering in CI. *.qsb not committed (gitignored).
   Locally: QT_QPA_PLATFORM=offscreen uses the software adaptation (no ShaderEffect) - test in CI only.
4. Unity, same branch: ports/unity (UPM com.shayxo.liquidglass): generated LiquidGlassMaterial.hlsl
   (single-arg ctors -> casts, HLSL keywords renamed, mix/fract/inversesqrt), LiquidGlassScreen.hlsl
   (ddx/ddy Jacobian layer px -> screen uv), LiquidGlass.shader (Built-in GrabPass), LiquidGlassURP.shader,
   GlassMaterial.cs (pure, doubles), GlassSurface.cs, .meta files (tool/write_meta.mjs, md5 GUIDs).
   Checks (net8.0-windows, Vortice 3.8.3): fixture 3/3, FXC ps_5_0, D3D11 WARP render + pixel checks.
   CI job `unity` (windows-2025) passed. Never run in Unity: said so in README/docs.
5. Parity DONE 20 Oct: p20 replays (rrw.sh, WT=worktree) of all five; scoreboard in
   review/atlas/reference-replay-2026-10-13/README.md (no regression; tap pill +0.8/-2, held lens -5 docs2).
   Rejected: tapAppearanceFallOmega 20 (only lengthens the tail). wallscore.py needs a probe.mjs from an old
   scratchpad (missing). First use on Pixel 7: review/atlas/first-use-2026-10-20 (317-370 ms frame on the first
   tap after install = shader compile; fixed by holding the selector's pad, #71, review/atlas/first-tap-fix-2026-10-20).
   The library baseline profile (liquidglass/src/androidMain/baselineProfiles/) was removed under #71: no measurable gain.
   Residual issues #70-#75; all dev/TODO.md rows closed.
6. Review page: .local/review-selector/index.html in the session worktree (local only, build.py p20).
   Owner accepted the selector in advance (20 Oct) and authorized merge, tag v1.0.0 and publish.
7. v1.0.0, branch claude/release-1.0: ABI validation (api/ dumps, checkKotlinAbi in CI build job),
   docs/compatibility.md, release notes, CHANGELOG 1.0.0, nothing deprecated so nothing removed.
   Then set_version.py 1.0.0, PR, merge when green, tag v1.0.0 on the merge commit, watch publish.yml.
   Owner morning actions: pub.dev first upload + automated publishing (docs/publishing.md), Pages HTTPS if
   still bad_authz, social preview upload (docs/assets/social-card.png), CodeRabbit billing check.
   Workflow from 20 Oct night: few large PRs, CodeRabbit reviews, merge --admin when green.

## PRODUCT PLAN (18 Oct 2026, owner-approved) — read this first

Goal: a universal, star-worthy library. One-line Compose integration on Android, desktop and web,
market-standard docs with real visuals. Positioning: the most faithful Liquid Glass, measured against
iOS recordings; never claim "first" (Kyant0 Backdrop has ~4.2k stars). Never ship iOS. No Maven, npm
or NuGet publish until the owner says. Use AskUserQuestion before design decisions, comparing the options first.

Layers, all maintained, all fully customisable, iOS-measured defaults:
1. Gradle plugin `dev.shayxo.liquidglass`: one-line entry. It adds the dependencies and adopts
   Material 3 apps with the `liquidGlassAdopt` import codemod (decided 18 Oct over a compiler plugin,
   which would break on every Kotlin release and hide the running code from the IDE). A BOM and plain
   artifacts are also published.
2. Glass versions of EVERY Material 3 component: same names, one import swap. Default iOS 26 look in
   the app's colours; `layout = GlassLayout.Material` for Material proportions.
3. SwiftUI vocabulary: `Modifier.glassEffect(Glass.regular.tint().interactive(), shape)`,
   `GlassEffectContainer`, `glassEffectID`, glass button styles.
4. `LiquidGlassTheme` defaults.
REMOVE every old public API (GlassScene, LiquidGlassScene, LiquidGlassState / liquidGlassSource /
liquidGlass); the engine becomes internal.

Phases, one PR each, suite and ports green each time, the owner reviews between phases:
1. Web target (wasmJs + skikoMain + `:web` Atlas): PR #28 (branch claude/web-target), suite 401/0.
2. Engine API, branch claude/glass-api: LiquidGlassTheme (root records; nested = defaults and
   accessibility, via GlassRegion.stateFor views sharing the recording), Modifier.glassEffect (draws
   nothing in place; the root overlay draws the glass via the production engine, plus the content as a
   foreground layer; a non-consuming pointer observer feeds a GlassPressSource), Glass variants,
   GlassEffectContainer (a GlassPortal composes LiquidGlassContainer in the overlay), glassEffectID
   (MorphTracker via the ModifierLocal GlassIdNode), GlassTabBar without state (portal). Old public
   APIs removed/internal; sample/desktop/web migrated. GlassEffectTest 7/7. Backdrop decision: Hybrid
   (overlay everywhere now; Skia backdrop saveLayer for desktop/web is a follow-up, spikes in
   review/atlas/backdrop-spikes-2026-10-18, worktree .local/wt-spike). Known: the merged neck shows a
   faint crease; the calibration "fused_closed" fixture no longer fuses. 19 Oct: the fixture fuses
   again through GlassEffectContainer. A density-2 probe of two 140 dp capsules (merge 30 dp) shows no
   crease along the neck centre; only a small kink where the fillet meets the waist (left as is).
- 18 Oct evening: full-suite hangs. Three full runs on claude/glass-api and one on the pre-Phase-2 baseline
  (6121bb7, which passed at 18:43) hung inside tab bar tests (GlassTabBarGestureTest, GlassTabBarTouchFrameTest).
  The AWT thread spun in Skiko RenderNode_nDrawInto or in composition apply for minutes. Run alone or in
  small groups, those classes pass (gesture class 2m20s). Since the baseline hangs too, it is
  environmental, not Phase 2: three stale Gradle/Kotlin daemons and two Wasm previews were running. Clean
  rerun after stopping them. Defensive fix kept anyway: the overlay never draws a departed element's
  released foreground layer (GlassOverlayEntry.disposed / isReleased).
  Result: clean rerun 20:16, BUILD SUCCESSFUL in 21m58s, 410 tests, 0 failures, 27 skipped (incl.
  GlassThemeComponentsTest 2/2). Auto-adopt decision (owner delegated): the liquidGlassAdopt codemod, no compiler plugin.
- 18 Oct 21:30 Phase 3/4 status (branch claude/material3, worktree .local/wt-m3): PR #29 (Phase 2) merged
  e4cf496. Material 3 tests 4/4 pass (phone window via runDesktopComposeUiTest; default_timeout 5m
  because 1.12.1 ignores testTimeout). Gradle plugin 7/7 (TestKit). Android + wasm compile. Fixed: linked
  accessory icon hidden under the bar portal (overlay draws linked entries after portals); iOS slider
  track. Showcase renders inspected and copied to review/atlas/material3-2026-10-18 and docs/assets.
  CI: new jobs material3 and gradle-plugin. Next: core suite on this branch, then commit + PR; catalogue
  renders (GlassMaterialCatalogueTest, not yet compiled); NavigationRail render still missing.
- 18 Oct 22:10 regression: Phone replay (sxs/hold-script.txt, calm, desktop Skia) rendered with this branch
  (91cc21c) against the pre-Phase-2 baseline 6121bb7 (.local/regress/old vs new): 121 frames compared, max
  level difference 0 - pixel-identical. Phases 2-5 left the tab bar's motion and material unchanged.
   Follow-up (hybrid, desktop/web): glassEffect on skikoMain draws in place with
   `Canvas.saveLayer(SaveLayerRec(paddedRect, null, backdrop = graph))`, where graph =
   makeRuntimeShader(builder, ["content","wideContent"], [ground+shadow merged over the backdrop (null
   input), glassWideInputFilter-style blurred quarter-scale backdrop]). This is the same shader and uniforms
   as the overlay path. Verify against overlay renders (same optics where both apply), then switch
   skiko to it and keep the overlay on Android.
3. Material 3 glass components (all).
4. Gradle plugin (with the liquidGlassAdopt codemod) + BOM.
5. Docs/README rewrite, live web demo on the docs site, visuals for every component.
6. npm web component, NuGet; then release on the owner's word.
   - 18 Oct, branch claude/web-component (uncommitted, for the lead's review): `ports/npm` `<liquid-glass>`,
     package `liquidglass`, `"private": true`. HTML engine (backdrop-filter; SVG lensing in Chromium, frost
     elsewhere) and canvas engine (production shaders via the vendored ports/skia painter). Node 27/27,
     Playwright Chromium 7/7 (+ capture test), ports/skia 45/45 with the new opt-in `tint`; CI job
     `web-component`; docs/web-component.md; evidence review/web-component-2026-10-18. Open: Safari and
     device checks, Firefox/WebKit headless builds render no backdrop blur, CanvasKit painter light (0,-1)
     vs GlassLight.Default. NuGet not started.
   - 18-19 Oct status:
     - Merged: #30 (Material 3, BOM, plugin), #31 (NuGet packaging), #32 (Android sample plus the
       overlay draw order), #33 (web component; a flaky reduced-motion race fixed in element.mjs).
     - Owner: "not impressed by the visual quality" (murky, flat, smears, no shine).
     - PR #34 (claude/glass-quality): frosted Material panels (`GlassMaterialDefaults.panel`, sigma
       7.7 dp), controls on a panel take its frost (glass on glass showed a sharp copy of what the
       panel hides), and a redesigned showcase. Pixel 7 capture in review/atlas/glass-quality-2026-10-18.
     - PR #35 (claude/ios-rim, stacked on #34): opt-in `GlassStyle.outerContour`, iOS 27's measured
       dark contour outside the outline (ios27-measurements-raw.md item 7). Material adopts it with the
       measured overhead light and zero dispersion. Core suite 411/412; the one failure was the uniform
       list, now fixed; final head 413/0. Pixel 7 confirms the contour on Android. Containers draw it
       too. The first CI run hit the build job's 55 min limit because MaterialShowcaseRenderTest ran
       there; it now has its own `showcase` job (`-Patlas.skipShowcase=true` in build).
     - 19 Oct night. The owner went to sleep with: "complete everything", decide without asking, never
       publish without their word. Done that night:
       - #35 merged: the iOS 27 rim, including containers and the chamfered lobe.
       - #36 merged: every remaining Material 3 component with a container; back dismissal; the
         partial sheet stop; the ports' surface light option (`GlassLight.Default` by default); the
         `fused_closed` fixture fuses again.
       - #37 open: `LiquidGlass.Wpf` and WpfDemo `--capture`, run in CI.
       - #38 open:
         - Android tall frosted glass lost its backdrop. A chained blur is evaluated only inside the
           window, so a nested frost layer now does the blur (`backdropBlurInRecording`).
         - The showcase can start scrolled and with its sheet open. The README hero now shows the
           scrolled view, and the star button has a glass tooltip.
         - A tooltip's long press no longer clicks its anchor.
         - The glass menu stays inside the window.
       - Avalonia: Avalonia 12 accepts SkiaSharp 4; `LiquidGlass.Avalonia` and a headless demo checked
         in CI on Linux and Windows (Avalonia 11, on SkiaSharp 2.88, cannot).
       - The neck "crease": a density-2 probe found no crease, only a small kink at the waist. Left.
       - Later that night, also merged:
         - #37: WPF.
         - #38: the Android frost fix, the scrolled showcase, the tooltip and menu fixes.
         - #39: handoff.
         - #40: `GlassTabBarStyle.Ios27` takes iOS 27's measured rim (roadmap item 3).
         - #41: Escape already dismisses glass overlays on desktop and the web. Compose
           Multiplatform 1.12.1 maps Escape to back; docs corrected.
         - #42: `LiquidGlass.Avalonia`. Avalonia 12 runs on SkiaSharp 4. The headless demo is in CI
           on Linux and Windows.
       - No PRs open at the end of the night. Nothing was published; no tags.
     - 19 Oct day, release readiness (owner: "okay start" on step 1 of the plan):
       - `publish.yml`: one tag publishes Maven Central (library, Material 3, BOM, and the Gradle plugin
         with its marker), npm (provenance) and NuGet, then a GitHub release. A `check` job refuses
         unless the tag matches VERSION_NAME, the version is not a SNAPSHOT, the commit is on main with
         CI green, and `.github/release-notes/vX.Y.Z.md` exists. PRs touching release files dry-run it.
       - `.github/scripts/set_version.py` (gradle.properties plus the .NET lock files).
       - Rehearsal: library and plugin published to a scratch Maven repo; a fresh Compose desktop
         project applied `id("dev.shayxo.liquidglass")`, `liquidGlassAdopt` swapped `Button`, compiled.
         `npm publish --dry-run` of a stamped copy: 23 files, 84.9 kB.
       - Owner actions: done on 19 Oct (`NPM_TOKEN` set, NuGet trusted publishing policy, "go" to tag).
         20 Oct: v0.2.1 PUBLISHED everywhere (Maven Central, npm @shayxo.dev/*, NuGet x4, GitHub release,
         run 39318114773). npm trusted publishers set up 20 Oct (Allow npm
         publish, tokens disallowed, NPM_TOKEN deleted); v0.2.2 PUBLISHED 20 Oct (run 39324862875) through npm trusted
         publishing, no token anywhere. main back on 0.3.0-SNAPSHOT.
         v0.2.0 reached Maven Central only (npm refused the unscoped names; NuGet push glob bug).
         0.2.1 = same code, npm scoped as @shayxo.dev/*, NuGet push fixed. After 0.2.1 reaches npm: add a
         trusted publisher to both npm packages, delete the token.
     - Next:
       - Compare against the private CC/stills locally.
       - MAUI needs the MAUI workload (`SkiaSharp.Views.Maui.Controls` 4.153.1 exists).
       - Flutter, Qt and Unity need their own integrations (React Native: see 19 Oct day below).
     - 19 Oct day (owner: "do all of it"):
       - Skia in-place backdrop (desktop/web) NOT pursued: the 18 Oct spike shows glass drawn in place
         goes blank under any translucent ancestor (AnimatedVisibility/Crossfade/navigation fades), worse
         than the overlay's "glass ignores the fade"; and Android cannot use it. The overlay stays.
       - Lens haze baseline on main (hazescore m0): top band 60/64, bottom 73/75, text p90 101/102,
         p50 86/82, black interior 48/47, rms 2.8: the dark-lens top-band residual is closed.
       - React Native: `ports/react-native` (liquidglass-react-native, private): kit.js adapts React
         Native Skia's API to the CanvasKit painter (Proxy: delete->dispose, enums, matrices, offscreen
         surfaces); GlassSurface/GlassLens record an SkPicture at device px. Headless tests 3/3 (adapter
         equals CanvasKit, max channel diff <= 1); tsc types; CI job react-native. Device run pending.
       - Flutter: `ports/flutter` (package liquidglass, publish_to none). tool/generate_shader.mjs makes
         shaders/liquid_glass.frag from material.sksl (types, content/field reads, main; wideAt = 13 Gaussian
         taps). GlassSurface = BackdropFilterLayer(ImageFilter.shader) in a ClipRRect; uOrigin = global
         position * dpr. NO y flip for backdrop input on GLES (the documented flip turned the page upside
         down). flutter test 5/5 (shader loads; 75 uniforms equal ports/skia fixture). Emulator lg_verify_37
         captures in review/atlas/flutter-2026-10-19. SDK at .local/flutter (3.47.7). Vulkan checked on Pixel 7, 20 Oct.
       - React Native device demo: D:/lgrn/LGDemo (moved out of the repo path: CMake hit MAX_PATH).
       - The long iOS video-parity rows in TODO.md (M1-M3, O1) remain.
     - Do not run two Gradle builds in one worktree at once: the renders and XML results clobber each
       other (UncompletedCoroutinesError, stale XML). A test that passes alone may fail when run in
       parallel.

## Parity plan (15 Oct 2026, owner-approved; parity work accepted 18 Oct)

Two mistakes found by the owner, both affecting every pass 1-14 level measurement:

1. **Only docs/1.MP4 and docs/2.MP4 were used.** `research/datasets/` holds ~1000 s more iOS 26
   footage at the same resolution. Phone bar: `ios27-phone/bubble/IMG_6756.MP4` (101 s, Keypad bar,
   taps/holds/drags/throws, keypad keys), `bubble/JGEY2190.MP4` (102 s, Calls/Keypad bar plus
   WhatsApp, Messages and App Store bars), `LOLL8185.MP4` (3 min, Phone keypad and App Store bars).
   Material over calibration targets: `COSG3073`, `LQCO6923`, `PUSM9766`; stills and
   `iphone's-ref-bubble/`; `mac-rig/captures/`. All private: nothing from them goes to GitHub.
2. **Wrong decode.** Every recording is HDR10 (HEVC Main 10, PQ/smpte2084, BT.2020, TV range). A
   plain decode shows the PQ signal as sRGB (white ~130, everything washed out). The comparison
   scene's `PhonePalette.Recorded` was dimmed to match that, and the Calm dark material's levels
   were calibrated in it. Correct decode (research/README.md II.4): `.local/sxs/hdr.py`
   (`frame(path, t)`, `frames(path, t0, dur)`), the zscale chain to display sRGB. Geometry and
   timing measurements are unaffected; every brightness level is not.
   Correctly decoded docs/2: white labels 255 (was 131-141), resting pill 15 (was 29), bar rest 22
   (29), bar held 42 (49), held lens interior 66 (64).

The plan, in order:

1. **Fix the measuring instrument.** One decode path for every reference frame (`hdr.py`), checked
   against the grey ramp in the calibration recordings (target: within 1 level). Rebuild the row
   dumps and caches the scorers read (`v2-row568.gray`, `v2-row495.gray`, `ios2-ink.npy`;
   `wallscore.py`, `wallscorel.py`, `neck.py`, `baredge.py`, `inklag.py`, `revevents.py`, `v2script.py`
   bar release) from correctly decoded frames; re-check their thresholds in display space.
2. **Make our side comparable.** Replay the scene in real display colours (`calm-natural` variant)
   and set its page colours from the correctly decoded iOS page (text, avatars, dividers), not by
   eye, so the page under the glass matches before judging the glass.
3. **Catalogue every Phone-bar episode in the dataset**: IMG_6756, JGEY2190 and LOLL8185 as well as
   docs/1 and 2: every tap, hold, drag, throw and landing with its time, plus an inferred finger
   script for each, built by the same generator.
4. **Re-fit the Calm brightness levels** against all those episodes in display space: resting pill
   and bar (rest and held); lens interior, outline, inner glow and the veil; page text through the
   lens and the tap-transit pill. Each level fitted across all episodes with its spread reported.
   The current values stay available as a named preset for comparison; every change states what
   it was scored against.
5. **Check geometry and timing generalise.** Re-run the size/squeeze, landing, neck and timing
   scorers on the new recordings and report per recording (were passes 9-14 over-fitted to video 2?).
6. **Republish:** review images and owner videos in correct colours; docs and the record; full test
   suite and both ports; then update PR #14 (`claude/parity-2`).

After that: the light-mode App Store and WhatsApp bars from JGEY2190 and LOLL8185.

Progress:
- Step 1 DONE: `.local/sxs/hdr.py` (grey ramp within 1 level). Rebuilt `v2-row568.sdr.gray`,
  `v2-row495.sdr.gray`, `ios2-ink.sdr.npy` (1993 frames, same timestamps); scorers switched (old
  copies `*.py.pq`), pill/bar threshold 36->21. Conclusions hold: walls R 5.95 / L 4.40 px rms
  (old 6.04 / 5.41), reversal crossings +2 / +6 ms, ink timing +4 ms, neck median 0 ms (20/22).
- Step 2: decoded docs/2 page colours measured at 0.2 s: names 255, secondary text 145, dividers
  42, call-button backgrounds 15, red names (247,78,83), accent blue ~(25,144,248), avatar a
  vertical gradient (81,76,97) top to (52,44,74) bottom. Step 2 DONE: `PhonePalette.Natural` set to these
  (plus keys 32, call green (23,127,47), missed red, accent (54,156,254)); replays default to it,
  `calm-recorded` reproduces the old palette.
- Episodes to catalogue (owner notes): IMG_6756 around 3.0 s, the search button pressed AND pulled
  upward (press-and-pull of the accessory, stretched toward the finger), not just pressed. Score the
  search button's pull against it (never scored in passes 1-14).
- Step 3 (in progress): `.local/sxs/track2.py <video> <name>` writes `.local/sxs/ref/<name>/` (track.csv with a
  Phone-bar signature, pts.txt, row568/495 .sdr.gray); `gen.py <ref-dir> [--rev ...]` = v2script for any
  recording (reproduces v2-script: same 46 presses/lifts); `taps.py <ref-dir> t0 t1` = v1 tap inference
  (reproduces v1-script: 37 taps within 17 ms). Catalogued: docs1 (37 taps), docs2, IMG_6756 (0-34 s
  search-button press/pull in all directions, not tab episodes; 34.8-53 s holds and throws; 60-100 s
  keypad keys), JGEY2190 (0-3.1 s throw; 4.6-47 s 47 TAPS, owner-confirmed; later segments other apps),
  LOLL8185 (19.8-44 s throws and holds).
- External code review (owner-supplied, 15 Oct), each claim checked against the code:
  1 lift sample dropped before pointerUp: REAL; Compose, CanvasKit and .NET now feed the lift to the
    controller first. ("1-sample integration lag" is the intended sample-and-hold; not changed.)
  2 dragPressShare squared in reach + release step: REAL (LiquidGlass.kt reach); fixed. No Calm effect
    (Calm reach is 0 since pass 13).
  3 poseCorner straddles the cap start: REAL (quoted code was not the real code); now ramps in along the
    run like cornerWeight/bodyCorner. Changes cap fold/mirror strength on pose lenses: judge vs iOS.
  4 fold colour split unmasked on straight runs; held fringe order under rim mirror: REAL; masked by
    the fold weight, and the order follows the mirror where it takes over.
  5 thrown lens loses heldTravel (cap mirror) at lift: REAL; kept while a lens thrown from a hold flies
    (Kotlin and both ports). transit==formation step: crossing measured at ~0.01, not visible; not changed.
  6 frame-loop freeze after release: NOT reproduced (0 of 162 post-lift frame pairs repeat); the pass 12
    wake fix covers the mechanism.
  7 ports clobber the slop-crossing move with beginDrag(x0): REAL (CanvasKit and .NET); fixed as Compose
    does (re-apply the move). This was the "port ignores the first move" seen in pass 12.
  8 per-frame wide blur: REAL in CanvasKit only (backdrop.mjs builds a tone buffer for any sigma);
    now skipped below 1 px. .NET already passes the real kernel.
  9 Android shared RuntimeShader race: NOT a bug (each RenderEffect snapshots uniforms at creation; the
    emulator showed many distinct panels). Harness: AtlasMotionCapture's left-side edge pulls started
    on their expected tab (could not fail); now start at the opposite end.
- Second external review (owner-supplied, 15 Oct, "parity blueprint"), checked against the code:
  most of its diagnoses quote code this tree does not have (a `sampleAlongBar` with 5 jittered taps,
  `pose.vx = 0f` under a Held wall clamp, `wallGive` gated on Released, `heldMagnification` 0 on Calm and
  centred on uSize/2). In fact: Calm's lens has heldMagnification .12, centred on uPoseC (pose) or the
  body; the fine blur under 4 px is a 5-tap rotated square, the wide blur is already an ImageFilter on
  JVM; the held wall is handled by the two-edge release (pass 10+). Its items 1.2(1-2), 4.3 were the
  first review's (done). Kept as measurements to make in steps 4-5 before any optic change:
  a) ink scale inside a MOVING lens, iOS vs ours (residual "magnified less", pass 11 record);
  b) text detail under the bar (high-frequency energy, display colours) for "slightly more smeared";
  c) the bar outline next to a held lens: does iOS's bow out (lensFuse is off on V3)?;
  d) the colour split where the held map and rim mirror blend (displacement-driven split can vanish
     there): judge on frames. Android packed endpoint inputs (false) stays a known platform gap.
- Step 3 correction (15 Oct): gen.py's catalogues were wrong for the new recordings. It reads presses from
  the selected ink alone, so holds on the middle tab were invisible and long held drags read as separate
  throws with lifts between. The bar shows the touch directly: while touched iOS's whole bar lifts ~10
  levels (rows 460-472) within two frames of the press. `.local/sxs/form.py` (per-tab level, search
  level, bar ends) -> `touches.py` (touch intervals) -> `gen3.py` (scripts: `ref/<name>/script3.txt`,
  `catalogue3.txt`). Lifts from the bar end away from the lens returning (gen.py's bar_release, both
  ends), calibrated +27 ms to v2-script. Checks: docs2 downs 21/23 and lifts 16/23 within 20 ms of the
  checked v2-script (median 0; two flicks 103 ms early); docs1 taps 32/37 within 20 ms of v1-script.
  Found: IMG_6756 41.25-53.0 one touch (drags 2-1-0-1-0-1-2 with holds); 34.7-40.9 three holds at Keypad
  (lens pulled ~25 px); LOLL8185 21.1-33.2 one touch (drags across all tabs); its 36-44 s is the App
  Store's dark five-tab bar (excluded; useful for the App Store work); JGEY2190 15.3-24.4 a held scrub
  (lens formed and still at 16.4 s, bar stretched, neck out) and 11.0-13.0 a hold, inside the owner's
  "taps" window, whose short touches stay taps (`--taps 4.5 15.2 25.5 48`; a tap and a flick cannot be
  told apart by timing). Scene glyphs re-measured: labels Medium + 0.7 sp spacing (iOS 11% wider, same
  height), keypad grid 72x69 px like iOS; magnification inside the held lens already matches (iOS x1.17,
  ours x1.18), so the "ink magnified less" residual was glyph size. Search-button episodes (IMG_6756
  0-34 s) are not scripted yet.
- Search button (IMG_6756 0-34 s, `.local/sxs/ref/IMG_6756/search-catalogue.txt`): 7 presses, 4 pulled up
  (top up to 110-174 px), 3 held. Pressed: grows ~1.25x (right edge +24..26 px), lightens 33 -> ~95, fuses
  to the bar with a neck. Pulled: a tall ellipse following the finger, detached from the bar, glyph
  stretched with it, dimming with distance. Released: lands squashed wide (overshoot 23-32 px below rest)
  and rebounds. Our accessory is passive (GlassTabBarAccessory has no press or pull): model it with the
  pose controller on a circular body, opt-in, then score extent/level per frame against search.npy.
- Pass 15 scored (re-fit + review fixes + scene glyphs; docs1/docs2 from v1/v2-script, the others from
  script3): rest bar +0 on all five (44 moments; was +13); rest pill +4 on black pages, 0 on Calls pages
  (was +12..+16); held bar -3 (16); held lens +6/+7 on black pages (IMG_6756, LOLL8185), -9 on docs2,
  whose lens shows a milky veil of the page above the bar; tap pill -11 on docs1 and JGEY2190 (was +1).
  The tap pill's plateau is short of iOS's (iOS 51-53, ours 38-43), its start and tail now right
  (`tapprofile.py`). Cause: iOS lights the whole bar on every touch, a tap included (+13 at the tab a tap
  never visits, from before the ink leaves, back by ~350 ms; `tapprofile.py --bar`); ours only while held
  (+3). Pass 16: `GlassTabBarStyle.touchLift` (opt-in, Calm on), heldLift 13/255, lens grey 0x2B, pill
  black .58 (`.local/sxs/fit16.py`). Rendering p16.
- Step 5 (tracking, `trackscore.py`, holds and drags; taps excluded because iOS recolours the destination
  early and the ink centroid jumps): docs2 31.5 px rms (throws included, the validated baseline), IMG_6756
  11.0, LOLL8185 9.2, best shift -8 ms on both: the pose dynamics generalise to the new recordings.
  JGEY2190 needs p16 (its scrub script was cut 0.1 s in by the wall-lift rule; fixed: final approach only;
  its start selection came from a non-Phone frame; fixed).
- Pass 16 scored (docs1, docs2, LOLL8185 full; touchLift on, heldLift 13/255, lens 0x2B .55, pill .58):
  rest bar +0 (20), rest pill +3 black pages / -1 Calls page, held bar +0.5 (8), held lens +0 on black
  pages (LOLL8185 5 moments; IMG_6756 slice 48/48), tap pill plateau 47-51 against iOS 48-53 (was
  38-43) and the bar's tap profile matches from +50 ms (`tapprofile.py`). Residuals: (1) iOS's bar lights
  33 ms before the ink leaves, our inferred tap presses 16 ms before (finger timing, unresolved; moving
  it would undo the ink alignment v1-script was fitted to); (2) docs2's held lens: iOS 63 / 88 (top band /
  lower interior) against ours 49 / 61. iOS's held lens brightens the page under it (the avatar reads
  91-111 through it, brighter than the avatar); ours adds a constant. Lens fits by replay (`lensfit.sh`,
  the `calm+lens=a,grey,raw` replay variant): no tint/raw-share pair meets both pages (raw share
  saturates at ~75 on docs2; a brighter tint lifts black pages equally). Kept the black-page fit (13 of
  16 held moments); the page gain is a structural residual for a later pass.
- 18 Oct, claude/fix-rest (owner: "port everything and fix the rest", no Pixel: desktop app; NO Maven publish
  until the owner says so): light bar ends GlassStyle.capEdgeShadow 2 (177-186 vs iOS 178-182; left end rms
  12.1, was 45.4; 5-10 overshoot); CalmWallLanding zeta .33 TRIED AND REJECTED (19 landings' edges slightly
  better, L 4.16/8.11 R 5.51/13.01 vs 4.33/10.22 5.78/14.90, but the suite's pinned width series worse:
  rms 3.78/6.80 vs 2.81/5.17; kept .3); search pullLiftShare .65 (scripts re-inferred FOLLOW .2 CAP .99
  LEAD .05: level rms 6.4, rise within 1-2 px); App Store glyphs 1.12x + SemiBold labels; Atlas
  -Patlas.windowSize; wide/narrow captures on both GPUs (review/atlas/desktop-2026-10-18). Desktop perf is
  slower than 11 Oct (NVIDIA ~10 / Intel 21-29 ms p50), but 02f74a4 (14 Oct) times the same (Intel 21.4 vs 21.6)
  -> not today's work; cause open (older commit or machine). Glow halving rejected: conflicts with video2's
  measured band (kept 1.5; docs2 moving lens vs video2 still lens).
- 18 Oct, claude/landing-timing: WhatsApp re-scored on today's build: path rms 62.7 (79.9), best shift -17 ms
  (-42) = one 60 fps frame; UP_CAL sweep .010/.027/.048 (genwa.py env UP_CAL) cannot move it below a frame.
  Landing timing CLOSED at the recording's resolution. Renders .local/sxs/ref/whatsapp/u*.
- 18 Oct, claude/acceptance: OWNER ACCEPTED all six device-check points by finger on the Pixel 7 (18 Oct).
  Device perf on build 38cfe01 (rawBlur 50 dp): frame p50 17-18, p90 26, GPU p50 9 / p90 15 ms (13 before),
  jank 1.9-2.0% (.local/device/2026-10-18/perf.txt). Landing timing: owner declined a show-taps clip; stays
  the documented one-frame limit of inferred input.
- 18 Oct, claude/lens-band: dark Calm lens rawShare .5 / rawBlur 50 dp (hazescore 2.8, top band 60/64; grid
  rw* in .local/sxs/ref/slice); Pullable pullReleaseDampingX .3 (sideways min half-width 83 vs 78; sX*);
  search script re-inferred for follow .2 (searchscript.py FOLLOW env; old script search-script-f35.txt).
- 18 Oct, claude/bar-ends: owner point 5 DONE: GlassStyle.capFold (uFoldCorners > 1 encodes it; shader
  capLens = held map at corner arcs, outward sampling allowed; recordPad adds band*.3); Calm bar capFold 1
  (dark + light). PR #21 (b8f57d5) merged the other five points; sample installed on the Pixel 7.
- 18 Oct (owner device check, 6 points), branch claude/residuals-2: (2) icons: Calm itemsFollowBarPress=false
  (follow bar only by held formation; iOS taps hold within 0.1 px, holds move 14 px; .local/sxs/icontrack.py);
  (6) taps: tapTransitFold=0 (held optics on tap travel; fold smeared ink); (1) two glasses: lens bodyBandScale .3
  (bar outline no longer a second capsule; hazescore within 2); (4) Pullable follow .2/.124 (owner: too
  sensitive); (3) GlassTabBarAccessory.lensFusion (sample 12 dp) + shader fuse on selector bodies; (5) bar ends
  refraction OPEN (edgeFold garbles straight runs; foldAtCorners needs formation; GlassRestMap.Measured barely
  bends). Also: per-app light pills (Calm -10, CalmClear -19, App Store scene 0), light lens shadow geometry
  (contactShadowBlur/Offset/Inside), CalmClear dark blur 7 dp + legibility 2 (68/79 vs 69/80). Raw-page clamp
  tried and reverted (no effect on dark top band; haze above bar is heldGlow). Replay knobs: +tap, +lshadow,
  +barfold, +barrest, +lring, +lband, +lfuse. Suite 401/0/27; ports 40/14/15.
- 18 Oct (owner: "complete it already"), branch claude/residuals: (1) BUG FIXED: a selector's contact
  shadow was the node's rect (whole bar) -> now cast by the lens body (glassShadowCaster: pose capsule via
  A, or two-disk body); no shipped lens had a shadow, so outputs unchanged. Light Calm lens contactShadow .5
  (IMG_7048 38.5/42 s: below-lens profile rms 2.6; renders .local/sxs/ref/light/sf0.5, sf1; replay variant
  "+lshadow=<c>"). (2) Search width ratio rescored: 1.0 kept (half-width rms 3.4; 0.8 4.4; 0.6 6.6;
  sW6/sW8). The "86 vs 78" is the ~100 ms after a sideways release (iOS overshoots rest 6 px vs 2), not
  the pull. (3) App Store light pill: invisible over white (255); 217-228 was cards through it. Residuals
  now: dark lens top band 53/64, App Store text p75 98/80, light lens edge band width, sideways release
  overshoot, landing input residuals, owner acceptance, real-finger device check.
- 18 Oct (overnight, cont.): search button pullLiftShare .6 everywhere (level rms 7.1, was 8.0; widthRatio
  1.3 and limit .6 rejected); light lens innerShadow .08 (band 245 vs 244, ~4 px vs ~12); device: 8-direction
  search pulls + off-axis drags clean; perf by stage on Pixel 7: UI 6.9 / RT 1.9 / GPU 8.5 ms p50, keeps
  90 Hz, renderScale barely matters (GPU 9->8 ms). PR #19 (claude/light-and-gaps) carries all of it.
  Remaining: lens top band (dark), App Store p75 text 98 vs 80, light lens band width + shadow, App Store
  light pill darker, search sideways squash 86 vs 78, landing/neck input-size residuals, owner acceptance,
  real-finger device check.
- 17 Oct night (owner: merge everything, light mode, overnight): merged #18/#16/#15 (admin). Branch
  claude/light-and-gaps: light Calm MEASURED (IMG_7048 light recording, research/datasets/ios27-phone/light/):
  bar white .41 + 10/255 (0.59B+115), edgeShadow .12 no highlight, contactShadow .2, pill -13/255, lens
  outline 243, tap optics (light taps refract too); CalmClear legibility 2.5 (App Store text p50/p75 67/98
  vs 69/80); ports: accessoryFuse/AccessoryFuse (bar-button neck, fixture accessory-fuse.json); lens
  refraction depth does not move the top band; the "8 px offset" was wrong (outlines within 2 px);
  IMG_7049 settles tab-bar blur = one soft copy (dark+light). Device check DONE on Pixel 7 28121FDH2006ZF
  (review/atlas/android-device-2026-10-17, local recordings .local/device/2026-10-17). Search-button
  refit (sA/sB/sC: widthRatio 1.3, liftShare .6, limit .6) was started then STOPPED by owner; not scored.
- 17 Oct: contact shadow under renderScale FIXED (LiquidGlass.kt): the in-backdrop copy was drawn inside
  glassLayer's scale(rs) although recorded already scaled, and sized to `size` inside record {} (= padded
  layer, not panel). GlassContactShadowScaleTest: rs .5 quadrants 1.4/0/0/0 -> 35 x4 (rs 1: 35 x4).
- 17 Oct (owner: "do all of these"): (1) CalmClear DONE: GlassTabBarStyle.CalmClear() bar black .10 (Phone .41),
  same lift; WhatsApp card bar/pill 53/31 vs iOS 54/30 (Calm 46/24), bare page 41/19 vs chats page 43/19,
  App Store gaps 66/64/63/58 vs 67/65/64/59; ports `clear` (clearBarTint in fixture). Replay variants
  whatsapp-clear / appstore-clear / whatsapp+plain. (2) WhatsApp motion DONE (scored, no motion change):
  .local/sxs/trackwa.py (white-fill tracker, per-column activation; also runs on a replay dir),
  genwa.py (touch = level jump + ends grow; lift inferred from iOS's landing, 100 ms Lsq2 tail; taps),
  cmpwa.py (same tracker both sides): landings 14/14, settle median -21 ms, path p50 3 px / p90 140.
  Renders .local/sxs/ref/whatsapp/m5. (3) Lens bands: GlassStyle.rawBlur (new, opt-in 0); Calm dark lens
  rawShare .22 rawBlur 20dp (tag rbE): hazescore rms 5.6 (was 9.7); top band 53 vs 64 remains (local to
  iOS's lens; iOS lens ~8 px higher). Ports blur the share (rawBlur in material fixture).
  (4) Needs owner: iOS tab bar over text screenshot; physical device (adb lists none); admin merge PR #14.
- Lens haze (17 Oct, DONE): Calm dark lens rawShare 0 (was .30). hazescore (shipped, tag hzShip):
  top 64/52, bottom 75/63, text p90 102/91 (was 155), p50 82/75, black 47/48, rms 9.7 (was 24.9).
  Residual: rim bands 11-12 low over content only. Suite 395/0 fail/27 skip; ports regenerated.
- WhatsApp bar (JGEY2190 50-74 s, 17 Oct, measured only): five tabs NOT evenly spaced (icon centres x
  187/370/584/799/~977: gaps 183/214/215/178, slots follow label widths, "Communities" widest); selected
  ink stays WHITE over the dark pill (the blue-ink tracker cannot follow it); the "You" tab is the owner's
  profile photo (local only). A replay needs label-width slots in GlassTabBar (equal slots today) and a
  pill/lens tracker. DONE 17 Oct (slots): NOT label-width. Rule = equal slots of 71.5pt (inner spacing
  214.6 px = widest item 63.3pt + 8pt) centred in the 348pt bar (x 63..1107), end tabs clamped so the
  78pt pill sits 4pt inside the end; all 5 centres within 2 px (label-width rule: 13 px off). App Store
  fits the same rule with 64pt slots. `GlassTabBarStyle.slotWidth` (opt-in), `WhatsAppScene`, replay
  variant `whatsapp`, GlassTabBarGeometryTest; tap lands 129 (iOS 130), drag 915 (913). Bar over the
  grey card 54 iOS / 46 ours, pill 30 / 25 (clearer glass, as App Store; not fitted). Ports: calmSelectorBar
  ({slotWidth}) / CalmSelectorBar.Create(slotWidth:) + tests. Evidence review/.../whatsapp-slots.png. Local: .local/sxs/ref/whatsapp, waslots.py.
- Ports free button (17 Oct): `GlassFreePullPortFixtureTest` exports free-button.json (4 scenarios through
  Compose's own laws/specs; animateFloatAsState retarget rule). CanvasKit/portable `createFreeButton`
  (ports/web interaction.mjs + core springAt/fastOutSlowIn; bundle regenerated; types), .NET `FreeButton`
  + FreeButtonChecks. Parity worst 0.0093 px / 1.8e-3 (Compose: spring time = whole ms truncated, tween
  = ns; spring snaps at its 0.01 visibility threshold). web 14/14, skia 35/35, .NET 14/14.
- App Store bar is clearer glass (17 Oct): plain card 28 -> 57 (slope ~0.9) vs Phone bar same session
  slope 0.58 (JGEY2190 8.25 s avatar 77 -> 87, black 42). Clear bar fits the card, not the text (page
  under the bar unknown for 2 of 3 lines). Lens haze: wide tone in lens does not raise p10 (50-51 vs 62);
  it is the held map's top band. Scene headline matched (27 sp Bold, 15/15/9 chars).
- Calibration target (17 Oct): the C1a stills (Photos toolbar over the known target, screenshots =
  display colours) were never affected by the decode error. New opt-in
  `GlassCalibrationRenderTest.renderTheCalmBarOverTheDarkTarget` + `GlassRender(softDetail)`. Calm bar
  (pass 18) over the target: mean/std 102.2/20.8 vs iOS Tint 50 102.6/20.6; CalmRecordedSpace 112.6.
  Linear law confirmed, no compression -> App Store gap is our page, not the material. Detail: the
  high-pass matches the toolbar's stripes (3.0 rms), softDetail 7.9; smooth along-bar Gaussian 7.0-8.2
  (fixes text, not stripes). Toolbar vs tab-bar role unresolved; softDetail kept for Calm. NEXT: make the
  App Store scene's page match (headline size/weight/position under the bar), then re-score.
- App Store bar (17 Oct, in progress): sample AppStoreScene + replay variant `appstore` (also `+bar=a,lift,
  fineShare,blurDp` for fitting); `.local/sxs/trackas.py` (as-track.csv), `genas.py` (as-script.txt; touch =
  bar lit vs local baseline AND left end out >= 6 px: the 38.0 s card scroll is not a touch), `scoreas.py`.
  LOLL8185 36.5-44: path 10.1 px rms, -8 ms. Bar over the card: iOS p10/50/75 58/68/79, ours 49/54/72;
  clear bar (+bar=0,32) 60/69/101; docs2 p75 rises 48 -> 59-66 with it. iOS: ~page+32 dark, compressive
  bright. NEXT: regress iOS 37.9 (black) vs 38.5 (card) per pixel for the bar's response curve, then a
  compressive opt-in (e.g. a highlight knee) on the bar; then JGEY2190 76-102 taps (bright cards).
- Next target found (15 Oct): the "light App Store / WhatsApp" bars in these recordings are DARK five-tab
  bars: JGEY2190 50-74 s WhatsApp (Updates/Calls/Communities/Chats/You), 78-101 s App Store (Today/
  Games/Apps/Arcade/Search), LOLL8185 36-44 s App Store. LOLL8185's other segments: home screen over the
  calibration target (dock, widgets, Control Centre). Personal content in all: local only.
  App Store dark bar (LOLL8185 37.5 s, display colours): x 63-1106 (348 pt), rows 453-638 (62 pt), five
  tabs at x 200/393/585/777/969 (192 px), resting pill ~x 78-328 (~83 pt). Needs its own replay scene.
- Pass 18 (done, 15 Oct): clear pill and lens, softDetail on bar and lens. All five at p18: rest bar/pill 0,
  held bar 0, held lens +0.1 (docs2 haze -13), tap pill -8 at +170 ms (plateau ok); suite 391/0 failed;
  ports 35/35, 13/13; videos .local/comparisons/2026-10-15-pass18/. Earlier notes: iOS's held lens follows held = 41-46 + 1.33-1.48 x
  (the same pixel at rest) on docs2 (`.local/sxs/lensgain.py`); its resting pill passes the page (p75 51
  vs our 29). Both are clear glass plus an offset: pill = bar - 22/255 (the originally measured inset
  law, "0.857 of the bar minus 17/255"), lens = bar + 0.30 raw page - 3/255. Slices (`pillfit.sh`): pill
  black 10/10, content 12/29/47 vs 15/25/51; lens black 48/49 vs 48/49, content 52/75/135 vs 62/80/135
  (p10 gap = iOS's milky haze). Applied to dark Calm; p18 renders running. Next found: text under the
  bar is grainy in ours (per-pixel rotated scatter taps + jittered axis mean), smooth in iOS: test an
  unjittered/smooth fine term once the renders free the shaders.
- Pass 17 (15 Oct): the search button. `GlassPullShape.Free` (body + glyph follow the finger, landing
  floor squash), `GlassMotion.FreeRelease` (.45 / 264, near-edge fit), `GlassInteraction.pullLiftShare`
  (.7), `GlassAccessoryLink` (bar outline follows the button, read at draw time; composition read
  trailed a frame). Script: `.local/sxs/searchscript.py` (finger inverted through the modifier's
  resistance) -> `ref/IMG_6756/search-script.txt`; score `searchscore.py IMG_6756 s4`: centre 1.9/3.0,
  height 4.3, half-width 2.8 px, level 8.0 (passive 14.3/41.1/6.4/4.4/19.1). Tests GlassFreePullTest.
  Ports: not ported (their interaction is the Calm preset). Owner video
  `.local/comparisons/2026-10-15-pass16/IMG_6756-search.mp4`. NEXT: held-lens page gain; light App
  Store / WhatsApp bars (LOLL8185 36-44 s has the App Store's dark bar too).
- Pass 16 closed (15 Oct): all five replayed. Levels (ours - iOS): rest bar +0 (44), rest pill +3,
  held bar +0 (16), held lens -0.9 (black pages 0; docs2 -16, the page veil), tap pill at +170 ms -8
  (plateau matches; ours falls ~50 ms sooner). Tracking (holds/drags): IMG_6756 11.0, LOLL8185 9.2,
  JGEY2190 26.9 px rms, best shift -8 ms on all three. Suite 388/361/27 skipped/0 failed; desktopTest,
  assembleDebug, CanvasKit 35/35 (touchLift ported: `calmBarMaterial({touch})`, navigation
  `state().touch`), .NET 13/13 (`CalmNavigation.Touch`, `DrawCalmBar(..., touch)`). Owner videos:
  `.local/comparisons/2026-10-15-pass16/`. NEXT: the search-button model (opt-in, see the measured
  search catalogue above), then the held lens's page gain, then the light App Store / WhatsApp bars.
- Step 4 (first fit): `.local/sxs/levels.py` (bar/pill at rest, held bar/lens, tap pill; iOS vs ours per
  catalogue moment). Pass-14 Calm in display colours, five recordings: rest bar iOS 33 / ours 46,
  rest pill 15 / 27, held bar 43.5 / 53, held lens 48 / 60, tap pill 54 / 52. Re-fit: bar lift 45->32/255,
  pill black .42->.55, held lift 8->10/255; old values kept as `GlassTabBarStyle.CalmRecordedSpace()`
  (replay variant `calm-recorded`). Re-rendering all five as `ref/<name>/p15` to re-score.

## How work is judged now

Core visible behaviour first. Every motion/material change is checked by replaying both owner
recordings through the real renderer and comparing **continuous sequences**, not best-fit frames:

1. `tools/reference-replay/` — ink tracker, inferred gesture scripts (`v1-script.txt`,
   `v2-script.txt`, catalogue), strip and three-row comparison tools.
2. `./gradlew :desktop:phoneReplay -Patlas.replay.script=<abs> -Patlas.replay.out=<abs>` renders
   `PhoneScene` at a deterministic 60 fps on the GPU (~2 min per recording). Not a performance
   measurement. Run it through `tools/workspace.ps1` on the owner's machine.
3. Three-row videos (reference / before / after) stay in `.local/comparisons/` — they contain the
   private recordings. Public evidence uses our renders only.

## Current state

- Pass 1 (13 Oct) fixed tap optics, thrown-lens formation, wall landing shape, grasp lag, rim
  specular and dark material levels. Record: `review/atlas/reference-replay-2026-10-13/README.md`.
- Baseline renders of the handoff build: `.local/sxs/base-v1`, `base-v2`; current: `fix4-v1`,
  `fix4-v2`. Comparisons: `.local/comparisons/2026-10-13-pass1/`.
- Pass 2 (13 Oct): `GlassTabBarAccessory` bar/search fusion.
- Pass 5 (13 Oct): moving-lens rim mirror at the caps (`rimMirror`, `foldAtCorners`), selected ink
  under the whole lens (`lensInkAll`), held lens blends to the mirror with travel (`heldTravel`).
- Pass 4 (13 Oct): bar rim, lens edge line, band glow (`heldGlowReach`), lens interior clarity.
- Pass 6 (14 Oct): two-edge wall landing (`releaseTrailingEdge`, 13/s; old Calm kept as
  `CalmFreeLanding`) and `PhoneGeometry` 95 pt pill / 8 pt ends. Current renders:
  `.local/sxs/land-v2`; landing clip `.local/sxs/landing-slow.mp4`.
- Pass 7 (14 Oct): input fixes (`v2script.py`: HOLD 137 ms, wall lifts moving), rim mirror 1.0
  with fringes, Atlas Studio redesign (`LoupeScreen`, replay variant
  `atlas`). Renders: `.local/sxs/p7-v2`, `.local/atlas/demo2`.
- Pass 3 (13 Oct): fast held reversals (grasp 120/s, held formation 50/s); reversal input
  rebuilt from turning points (`tools/reference-replay/reversal-script.txt`).
- README and repository description rewritten (owner authorized the description/homepage).
- PR6 merged into `main` (02f74a4, 14 Oct, owner: "permission granted for all ... merge already").
- Pass 9 (14 Oct, branch `claude/ios-gaps`): every wall landing scored (`.local/sxs/wallscore.py`:
  trailing edge underdamped toward the wall, carry .1, wall give .35; rms 4.9 px over nine
  landings, was 15.4; old Calm kept as `CalmCriticalLanding`); rim-mirror clamp bug fixed and
  `rimMirrorGain` 0.4 (cap petals); neck yields to an arriving lens (`yieldsToLens`) and the
  stationary-hold lift follows the iOS bar's release (`neck.py`: median 33 ms late, was 67); finger
  leads the ink 33 ms (`v2script.py` LEAD); ports use the exact Compose Calm material
  (`selector-material.json`). Renders: `.local/sxs/p13-v2`.

## Next, in order

1. Owner visual acceptance of the selector (pass 9) and of the redesigned Atlas Studio.
2. Pass 11 (14 Oct): dragPressShare .7 (bar relaxes while its lens is dragged), neck median 0 ms,
   rim dispersion .05, ported selected ink, mid-throw centroid stalls interpolated. Owner review
   videos: `.local/comparisons/2026-10-14-pass11/`. Residuals (pass 10, still current): 9.9 s landing passes rest by 36.5 px vs 41; left width series' first
   frames narrow up to 11.2 px less; iOS's cap petals carry stronger rainbow fringes; neck median
   17 ms late, 34.8 s 133 ms (bar and lens disagree on that lift). 28.66 s landing is an input
   artefact (source frame gap). Left landings: `.local/sxs/wallscorel.py` (rows 495–498).
3. Pass 12 (14 Oct, branch `claude/parity-2`): the owner's six differences after pass 11 — held
   ink pivot/reach, grey outline (`heldEdgeTone`, `heldGlow` 0), moving-lens strain
   (`heldTravelStrain` .5 @ 10/s, held reference speed 8, squeeze .52; old Calm kept as
   `CalmWallLanding`), transit fold .75 along the runs, frame-loop wake clock (throws were 1.5
   frames late), placeholder scene page. Renders `.local/sxs/p12d-v1`/`p12d-v2`, baseline
   `main-v1`/`main-v2` (from a temporary 8cb49ec worktree, `.local/sxs/rrm.sh`); owner videos `.local/comparisons/2026-10-14-pass12/`.
   Residuals in the review record's "Remaining differences".
4. Pass 13 (15 Oct, same branch): `GlassPoseHandle.barShift` puts the held lens on the bar's
   pressed tab, Calm `pressReach` 0, `heldGlowFloor` 1 with glow 1.5 / 5 dp, `rawShare` .05,
   strain .6 / squeeze .47. Renders `.local/sxs/p13-v1`, `p13-v2`; `.local/sxs/inklag.py` scores
   whole-video ink timing (p13 +4 ms, main +20 ms).
5. Pass 14 (15 Oct, same branch): held lens veil (tint .55 of 0x3A, rawShare .22), refracted tint
   mask for the held profile, reversal input rightward moves 18.7-19.6 s 20 ms earlier
   (`.local/sxs/revevents.py` scores icon crossings). Renders `.local/sxs/p14b-v1`, `p14b-v2`.
4. CI: Atlas UI tests now take ~2.6 min locally (density 0.75, reduced motion, no per-move waits;
   they took 14 min on CI before). Hosted runner speed varies ~2x (36 min on 31bd210 with
   unchanged classes 1.9x slower), so the build job limit is 55 min.
5. Android: verified on an API 37 emulator (software GPU, AVD `lg_verify_37`,
   `review/atlas/android-emulator-2026-10-14`); a physical-device check remains open.

## Constraints (owner)

Work alone: no subagents, no computer-use. Atlas only, not Vitals. JDK 21 at
`C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot`, Gradle via `tools/workspace.ps1`.
Commits/pushes authorized; no packages, tags or releases (PR6 was merged with the owner's
explicit go-ahead; ask again for any later admin merge). Keep private
recordings, page content and credentials off GitHub. Do not run the GPU replay while the JVM test
suite is running (CPU contention caused a historical test timeout).
