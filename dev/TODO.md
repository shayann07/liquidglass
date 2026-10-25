# LiquidGlass / Atlas delivery checklist

Updated 2026-10-14. Start with [NEXT-SESSION.md](NEXT-SESSION.md). Checked entries describe
bounded completed work, **not completion of full iOS parity**. All open rows remain in scope.

## AFTER 1.0 (20 Oct 2026) — start here

v1.0.0 is released (tag on 4a71394; npm, Maven Central, NuGet incl. `LiquidGlass.Maui`, GitHub
Release). Main is 1.1.0-SNAPSHOT. Everything below the "PRODUCT PLAN" heading is history; the open
work is this list. Each row names its issue; close the row and the issue in the same change, with
evidence under `review/` for anything visible. Rules: AGENTS.md (opt-in optics, never re-fit a
measured constant silently, docs/CHANGELOG/dev notes in the same change, ask the owner before
scope/API/positioning decisions, one Gradle build per worktree).

### Owner only (an agent cannot do these; remind the owner, do not attempt)

- [x] pub.dev: 1.0.0 uploaded 20 Oct (docs updated); the owner enabled automated publishing (GitHub Actions, push events, `v{{version}}`) the same day. Not readable from outside: the first proof is the next tag's `pub-dev` job. 1.0.0 scored 150/160 (description over 180 characters; shortened on main for 1.1). Was:
  docs/publishing.md "pub.dev, once". Until then the `pub-dev` job warns and stands down. After it,
  remove "first upload pending" from docs/compatibility.md, ports/flutter/README.md, README.md and
  docs/for/flutter.md.
- [x] Pages HTTPS, fixed 20 Oct (certificate approved, HTTPS enforced). Was: certificate state `bad_authz` for `liquidglass.shayxo.dev` (DNS is correct).
  Settings → Pages: clear the custom domain, Save, re-enter it, Save; tick Enforce HTTPS once the
  certificate is issued. Check: `gh api repos/shayann07/liquidglass/pages --jq .https_certificate.state`
  should say `approved`.
- [x] Social preview: uploaded 20 Oct (docs/assets/social-card.png).
- [x] CodeRabbit: trial ends 2 Nov 2026; the repo is public, so reviews stay free. Confirm no paid
  plan starts (coderabbit.ai → Subscription).
- [x] (20 Oct: owner looked; ~90% satisfied, refraction too weak, see agent work. CodeRabbit: no card on file, so the trial just ends on 2 Nov; do not click "Continue to payment".) Look at the navigation selector side-by-side page (.local/review-selector/index.html, local
  only) and the launch drafts (drafts/launch-drafts.md, local only), then post if happy.

- [ ] Launch posts, parked by the owner 20 Oct. Drafts: drafts/launch-drafts.md (local only). Done: awesome-list PRs
  jetpack-compose/jetpack-compose-awesome#104 and web-padawan/awesome-web-components#155 (kmp-awesome needs iOS and
  ~50 stars; awesome-flutter and awesome-react-native need real adoption, so retry later). Not done: Show HN (HN refused
  sign-up on the owner's network), Reddit (blocked in the agent browser), X, Kotlin Slack, dev.to. Only on the owner's word.

### Owner decisions, 24 Oct (act on these first; see NEXT-SESSION "PAUSED 24 October")

- [ ] **#73:** make the rim fringes match iOS by default ("no need for opt ins"); scored on all five recordings.
- [ ] **#77:** render the lens band at 0.3 / 0.6 / 1.0 / 1.1 beside iOS, adopt the one that looks closest, and record why.

### Agent work, in this order (GitHub milestone "1.1.0": #70-#73, #77-#82)

No 1.0.1: the owner decided on 20 Oct to ship these together as 1.1.0 rather than release the Flutter
description fix alone.

- [x] **#80 / #82 and the first part of #81 (itsaqeeldev, 20 Oct), for 1.1.0.** Active-consumer
  view lifetime, matching cache/build geometry (whole-pixel size, exact recording-grid padding),
  shader-capability gating, bounded EDT
  scratch reuse and corrected documentation are implemented with regression tests. The retained
  workspace is capped at 256k pixels (~4.25 MiB); larger builds use temporary arrays.
  CodeRabbit fractional-padding correction passes 21 focused checks and the full suite
  (428 total, 401 passed, 27 opt-in skips, zero failures/errors); Android/Wasm/ABI/docs checks pass.
  Verification and historical results: `review/atlas/compose-cache-field-2026-10-20/README.md`.
- [ ] **#81 follow-up:** bounded-resolution field preparation and device timing remain. The first
  build and outline/origin changes are still a synchronous full-resolution EDT; odd-pad and
  field-consumer tests stay gates.
  24 Oct: EDT ~15% faster with bit-identical output (no per-neighbour closure/bounds check, packed
  mask read); `FieldAllocationBenchmarkTest` pixels identical.
  24 Oct (PR, cloud session): size-only changes build on `Dispatchers.Default`, previous field kept
  until published, conflated, dropped on leave; first field / empty-to-real / shape, pad, band,
  density, direction changes stay synchronous. `GlassPathFieldOffCompositionTest` (7) holds the build
  dispatcher and checks every composed frame. Remaining: bounded-resolution build; device timing.
  review/atlas/pathfield-off-composition-2026-10-24.
- [x] **#89 Material 3 slider RTL (24 Oct).** Tracks draw from the start edge, mirrored right to left;
  two RTL tests red before, green after; material3 22/22; review/atlas/material3-slider-rtl-2026-10-24.
- [ ] **#78 Web demo first load: loader and on-page failures landed (itsaqeeldev, PR #85, 20 Oct); load time still open.** Measured in headless Chrome
  (tools/measure_web_demo.mjs, cold caches): 4.46 MB gzip (skiko.wasm 3.28 MB), then ~2 s per opening frame
  compiling shaders on a first visit; black page 7.6 s unthrottled, 30 s on slow 4G. Loader from first paint
  (0.05-0.2 s), hidden after settled frames; Compose mounts in #app (it replaced the body's children);
  no-Wasm-GC, failed-download/uncaught-error and lost-WebGL-context states on the page; docs iframes lazy.
  Time to a usable app unchanged (download + shaders). review/atlas/web-demo-load-2026-10-20.
  24 Oct (PR, cloud session): `.wasm` preloaded from the head where Wasm GC validates (names written by
  apps/web/build.gradle.kts); Skia ready 4.60 → 4.26 s fast 4G, 23.6 → 23.3 s slow 4G, same unthrottled
  (software-GL Chromium, 5 cold runs); states unchanged (tools/web_demo_states.mjs). Remaining: shader
  warm-up / scene deferral need a GPU machine to measure; brotli/CDN is the owner's decision (3.53 vs
  4.53 MB). review/atlas/web-demo-load-2026-10-24.
  Still open: cut the transfer (4.46 MB; brotli or a smaller skiko build), first-visit shader compile
  (~2 s per opening frame), the never-renders root cause (not reproduced), and Firefox/Safari/phone checks.
- [x] **#79 Material 3 polish (itsaqeeldev, 20 Oct).** Slider/RangeSlider tracks now run under the
  thumb's whole travel (they stopped half a thumb short: Material insets the track box by half a
  thumb and the glass track inset again); `GlassSliderTrackTest` failed before, passes after. Showcase:
  mini player inset 20 x 14 dp with a gap before the switch and a pause glyph; hero capped at 280 dp
  on wide windows. Desktop renders before/after, Pixel 7 capture, material3 suite 20/20:
  review/atlas/material3-polish-2026-10-20, with browser before/after (Chrome).
- [ ] **#77 Refraction reads weaker than iOS (owner review, 20 Oct).** The owner watched the selector
  side-by-side page (.local/review-selector, iOS beside p20) and is about 90% satisfied, but says
  our refraction feels much weaker than in the recordings. Next: measure displacement (how far the
  backdrop under the pill/lens is bent at the rim and across the body) in the recordings vs our
  replay, with a scorer beside levels.py; report the gap per recording; then ask the owner. Any
  change is opt-in or scored against all five recordings, never a silent re-fit.
  **Measured 24 Oct** (review/atlas/refraction-gap-2026-10-24, scorer tools/reference-replay/refraction.py):
  at the held lens's rim iOS pulls the bar's edge +10.9 px toward the lens centre, ours -2.2 px (gap ~13 px
  on docs/2, IMG_6756, LOLL8185; +9.7 on JGEY2190; docs/1 has no held lens); body magnification matches
  within 0.04; transit pill 1.10-1.14 vs 1.16-1.21. Cause: Calm lens `bodyBandScale` 0.3 (authored).
  Replay-only candidates on IMG_6756: 0.6 -> +3.8, 1.0 -> +9.8. Next: owner decides on an opt-in band
  ~1.0-1.1, then score all five plus levels.py and judge the bar's end cap ("two glasses" risk).
- [x] **#71 first-tap hitch (itsaqeeldev, 20 Oct).** Traced: the selector's pad followed its deforming
  body, its layers were reallocated mid-tap (756x505 -> 748x497 -> 740x489) and the first new size compiled
  a 265-286 ms Vulkan pipeline for the glass shader. GlassPadFloor holds the largest pad: first-tap slowest
  frame 326-352 -> 67-91 ms (measure.sh CLEAR=1), cold start medians 135.5 / 136 ms (alternated).
  Baseline profile removed (no measurable gain with or without the fix). review/atlas/first-tap-fix-2026-10-20.
- [ ] **#70 tap pill plateau ends ~50 ms early.** Scored with `.local/sxs` (main checkout):
  rrw.sh renders a replay (WT=worktree), taparrive.py / tapprofile.py score it, run from the repo
  root. A fall-omega change was tried and rejected 20 Oct (review/atlas/reference-replay-2026-10-13
  README). Any change opt-in or scored against all five recordings, reported in that README.
- [ ] **#72 held lens over page content reads 5 levels below iOS (docs/2).** On black pages it is
  -0.1 / +1, so it is content-dependent. Score with levels.py; same rules.
- [ ] **#73 score the rim's chromatic fringes and timing** instead of judging by eye: add a scorer
  next to levels.py, then decide with the owner whether any change is needed. **Scored 24 Oct**
  (`tools/measure_rim_fringe.py` = `.local/sxs/fringe.py`; review/atlas/rim-fringe-score-2026-10-24):
  iOS's fringes are 30 to more than 100x ours (peak E per touch ours/iOS 0.00-0.03 on all five); iOS's grow 5-30x with
  speed, peak at a held gesture's lift and fade +110-120 ms after it; ours are flat and too faint to time
  except on docs/2 (same shape). Split is authored 0.05 of the band (GlassTabBar.kt:2235, :2089).
  Next: owner decides; an opt-in candidate (style field for the constant, travel gain, split in px) is
  described in the record, nothing applied.

### Help wanted (needs hardware this machine does not have; keep the issues current)

- [ ] #62 LiquidGlass.Maui on Windows · #63 desktop and Avalonia on macOS · #64 the web component
  in Safari · #65 more Android devices · #66 Flutter and React Native frame timing on a phone ·
  #67 Linux desktop on real GPUs · #74 the Unity package in Unity · #75 the Qt port on a GPU.
  When a contributor reports, add their evidence under review/, update docs/compatibility.md
  (Qt/Unity leave "preview" only after #74/#75) and close the issue.

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
- 18 Oct, claude/residuals: selector contact shadow cast by the lens body (was the whole bar's node rect);
  light Calm lens contactShadow .5 (below-lens rms 2.6 vs IMG_7048); search width ratio 1.0 confirmed
  (0.8/0.6 worse); "sideways squash" is a release overshoot (iOS 6 px past rest vs 2); App Store light pill
  is 255 over white (217-228 was cards). Open: dark lens top band, App Store text p75, light lens band
  width, sideways release overshoot, landing input residuals, owner acceptance, real-finger device check.
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

## Now — continuous comparison against the recordings (13 October)

- [x] Deterministic 60 fps GPU replay of both recordings' gestures (`:desktop:phoneReplay`,
  `tools/reference-replay/`); three-row reference/before/after comparisons kept local.
- [x] Pass 1: tap transit optics, thrown-lens formation, wall landing shape, grasp lag, rim
  specular, dark material levels. `review/atlas/reference-replay-2026-10-13/`.
- [x] Held bar/search-button fusion (liquid neck): `GlassTabBarAccessory`; silhouette within 1 px at
  1.80 s. Neck timing follows our later press; finger-side bar growth not modelled.
- [x] Fast held reversals: lens follows the sweeps (grasp, hold formation, rebuilt input; pass 3).
  End-of-lens icon warping is still weaker than iOS.
- [x] Lens rim, band glow and interior clarity measured and matched (pass 4).
- [x] Moving-lens end optics: reflective rim at the caps and selected ink under the lens (pass 5).
  iOS's swirl is still larger and faintly rainbow-fringed.
- [x] Wall landings press into the end and narrow from behind (pass 6); comparison scene's resting
  pill corrected to the measured 95 pt. Landings with an inferred stop-before-lift do not narrow.
- [x] Inferred input corrected: hold onset aligned by construction (finger-down is invisible);
  wall throws lift moving. 2.3/12.4/25.6 s now narrow like iOS; 9.9 s narrows less (our held
  lens is slowed by the end-tab clamp before lift; iOS's hits the wall at speed). Open.
- [x] Larger cap swirl with faint fringes (rim mirror 1.0) (pass 7).
- [x] Finger-side bar growth: `pressReach` (far side anchored); edges within 2–5 px (pass 7).
- [x] Atlas Studio redesign: moving full-window scene, one switcher, fused magnifier button.
- [x] Bar release after lift matched (0.12 s, ~10% overshoot); neck parts 60–70 ms after iOS
  (inferred lift) instead of 70–180 ms (pass 8).
- [x] Atlas UI tests made cheap enough for CI's 40 min limit (~2.6 min locally).
- [x] Android verified on an API 37 emulator (AGSL path, software GPU): tap, hold, throw and Atlas
  Studio render correctly (`review/atlas/android-emulator-2026-10-14`). Physical device still open.
- [x] Calm navigation selector ported to CanvasKit and .NET, checked against Kotlin-exported
  trajectories (5 scenarios, worst 0.035 px).
- [x] PR6 merged into main (02f74a4) with the owner's explicit go-ahead.
- [x] Pass 9: every wall landing scored; trailing edge travels on underdamped, formed lens presses
  past the end (rms 4.9 px over nine landings, was 15.4; 9.9 s passes rest by 33 px vs iOS 41).
- [x] Pass 9: rim-mirror clamp bug fixed; cap content drawn out radially (`rimMirrorGain` 0.4).
- [x] Pass 9: neck yields to an arriving lens; stationary-hold lift from the iOS bar's release;
  neck parts a median 33 ms late (was 67). Two releases still 150–200 ms late.
- [x] Pass 9: ports use the Compose Calm bar/selector material (96 Kotlin samples, ≤2e-7) and look
  through the bar. Selected ink in the ports remains host-owned.
- [x] Pass 11: bar relaxes while its lens is dragged (dragPressShare .7; edge rms 4.9 px); neck median
  0 ms (34.8 s +100); fringes .05; ported selected ink; owner review videos built locally.
- [x] Pass 10: left-wall landings scored (rows 495–498); stretch-stiffened trailing edge (rms 4.1 px
  left, 5.5 right); reversal end lifts moving; release spring one frame in; neck median 17 ms;
  still release keeps its glass .18 s (stationary hold darkens on time again).
- [x] Pass 12: held selected ink on iOS's position (pivot and reach fixed; 203 vs 204 px); grey
  held outline (`heldEdgeTone`, glow off); moving lens widens and flattens (`heldTravelStrain`,
  427 x 144 vs iOS 458 x 145); tap fold along the runs (.75); frame-loop wake lag fixed (throws on
  time); neutral scene page. Landings 5.7 / 5.1 px rms. Evidence: `review/atlas/reference-replay-2026-10-13`.
- [x] Pass 13: held lens on the pressed tab, bar reach removed (cap band as iOS); glow as a floor
  (band 26..10 vs 36..9); rawShare .05 (text p95 82 vs 84); strain .6 (444 x 145 vs ~444 x 141).
- [x] Pass 14: grey veil on the held lens (avatar 82.5 vs 80, text 80/105 vs 84/109); refracted
  tint mask (band 15.4 vs 15.3); reversal input refit on icon crossings (right +2 ms, left +3 ms).
- [x] Tap transit pill strip reads 58 against iOS's 67 at 1.0 s (pre-existing, seen in pass 14). **Closed 20 Oct:** Superseded 20 Oct: the tap pill at its destination scores +0.8 (docs/1) and -2 (JGEY2190) against iOS (replay p20); the remaining plateau shape is #70.
- [x] Owner visual acceptance of the navigation selector. **Closed 20 Oct:** Owner accepted 20 Oct (in advance of the review page, which was built: .local/review-selector, local only).

## P0 — current visible failures

- [x] Preserve the owner's rejected dark-disc screenshots and withdraw their visual sign-off.
- [x] Keep resting tint on the actual navigation substrate; preserve the resting inset and
  reveal the background through protruding caps. Published checkpoint5a81393.
- [x] Isolate the vertical moving-selector seam: no-wide and no-through remove it;
  no-mask and no-packed do not. Restore all temporary isolation variants.
- [x] Validate clipped-filter test coordinates. Canvas translation fixes the flawed image-placement
  test; a deferred-layer regression now reproduces the old defect and passes the candidate.
- [x] Verify the explicit JVM wide-input graph, retaining kernel/through-bar/substrate.
 13focused checks,43native phases/static and the complete369-case library inventory pass
 (342pass,27optional skips), with3Atlas passes and Android assembly. Original interruption
 and exact recovered/fresh coverage are preserved. Hosted confirmation remains pending.
- [x] Inspect native early taps in both directions, gradients and clipped/nested/container
 paths, plus resting/recovered insets and caps. The hard seam is removed; full optics remain open.
- [x] Fix the separate long-tap appearance decay; current candidate becomes dark too early. **Closed 20 Oct:** Carried by #70 (plateau ends ~50 ms early; the fall rate was tried and rejected).
  Area+axial candidate corrects the25calibrated interior patches;371-case library inventory,
  3Atlas/Android and43native phases are verified. Complete optical/ink timing remains inM1.
  The original V3gesturetimeout and unchanged-source retry remain a reliability issue.

## M1 — complete motion, contour and optics across supplied gestures

- [x] Preserve source hashes/native PTS, gesture catalogue, decoded frames and failed baselines.
- [x] Coupled area/spine tap model, short-trip timing calibration and bilateral leading-edge
  landing recovery implemented with labelled authored parameters and regression evidence.
- [x] Video1 short taps: close remaining width/contour error in the repeated episode; compare **Closed 20 Oct:** Scored 20 Oct (p20): docs/1 rest bar/pill 0/0, tap pill +0.8; profile residual #70.
  continuous appearance and neighboring content, not only centre timing or a best-fit frame.
- [x] Video1 long taps: verify full shape and pigment/ink sequence, including the early repeated **Closed 20 Oct:** Scored 20 Oct (p20) with the short taps (docs/1, JGEY2190); residual #70.
  frame and late recovery; retain uncertainty about original input duration.
- [x] Video2 stationary hold: verify growth, rim, refracted ink and bar response together. **Closed 20 Oct:** Scored 20 Oct (p20): held bar +1.3, held lens -5 on docs/2 (#72), -0.1 / +1 on IMG_6756 / LOLL8185.
- [x] Video2 early throws before formation: both directions, material continuity and recovery. **Closed 20 Oct:** Scored with docs/2 tracking (p20 31.6 px rms, best shift 0 ms; unchanged since pass 18).
- [x] Video2 fully formed throws: both directions, intermediate contours and optical transitions. **Closed 20 Oct:** Scored with docs/2 tracking (p20); fringe timing is #73.
- [x] Video2 held reversals between extremes: continuous response and surrounding bar/ink behavior. **Closed 20 Oct:** Reversal input refit pass 14 (+2/+3 ms); tracking unchanged at p20.
- [x] Video2 soft intermediate-tab landing: continuous contour/material recovery and selection. **Closed 20 Oct:** Scored in the docs/2 tracking (p20); no regression.
- [x] Video2 hard endpoint collisions: both walls, close remaining cap-curvature/recovery error. **Closed 20 Oct:** Wall landings scored pass 13 (6.0 / 5.4 px rms right / left); trailing damping .33 tried and rejected 18 Oct.
- [x] Verify interrupted motion, re-grabs, cancellation, resize, reduced motion and frame-rate **Closed 20 Oct:** Covered by the suite (GlassPoseRegrabTest, GlassTabBar*GestureTest, GlassTabBarTouchFrameTest, GlassExtremeInteractionTest), run in full 20 Oct.
  consistency after final model changes. Reuse already valid checks where inputs are unchanged.

## M2 / M3 — restrained interaction and measured extremes

- [x] Separate press expansion, travel deformation and whole-bar perpendicular strain.
- [x] Size-dependent subtle response for large cards; preserve small-control behavior.
- [x] Layout/hit targets anchored; navigation foreground drawing follows measured shared strain.
- [x] Native replay includes all eight app-window edges/corners, reversals and intermediate landing.
- [x] Verify each direction against actual source extreme envelopes, with gradual onset; existing **Closed 20 Oct:** Search-button pulls scored in 8 directions (pass 17-18); bar extremes in GlassExtremeInteractionTest.
  snapshots and selected width/height bounds alone do not prove full iOS equivalence.
- [x] Preserve accessibility, gesture ownership, reduced transparency/motion and generic defaults. **Closed 20 Oct:** Covered by the suite (semantics, reduced motion/transparency, gesture ownership tests) and docs/accessibility.md.

## O1 — optical integrity

- [x] Separate continuous magnifier profile from selector-specific held optics.
- [x] Standalone magnifier continuity and endpoint composition regressions implemented.
- [x] Fix the JVM selector seam and audit native frames at original resolution; panel/container
  deferred-layer regressions and all 43 native phases pass. Android equivalence remains below.
- [x] Close remaining rim/chromatic/side-boundary and material timing mismatches using source **Closed 20 Oct:** Rim scored (passes 9-14); the chromatic fringe timing is judged by eye: #73.
  evidence; avoid opaque overhanging discs or a painted outline used to hide a sampling defect.
- [x] Verify Android's endpoint composition equivalent when feasible; desktop's improved packed **Closed 20 Oct:** Pixel 7 device check accepted by the owner 18 Oct (review/atlas/android-device-2026-10-17).
  path is not evidence for Android's single-input fallback. Physical-device testing remains pending.

## A1 — Atlas Studio and performance

- [x] Native studio redesign, Sky/Type/Lines scenes, continuous draggable lens and responsive controls.
- [x] App-owned AWT replay, stable-layout checks, source screenshots and actual GPU metadata.
- [x] Prefer NVIDIA discrete GPU; paired static redraw submission improved on the owner's machine.
- [ ] Fix first-use/live interaction hitches and measure presented-frame/input behavior separately **First tap fixed (#71, review/atlas/first-tap-fix-2026-10-20); presented-frame timing still open.** The first-use measurement is done (Pixel 7, 20 Oct, review/atlas/first-use-2026-10-20): one 317-370 ms frame on the first tap after install. Presented-frame and input timing remain open.
  from forced redraw submission and screenshot readback. No false FPS claims from phase snapshots.
  The wide-input fix adds about 3 ms median submission time in the controlled NVIDIA comparison;
  cropping its transformed input preserved pixels but did not improve speed and was rejected.
- [x] Review final studio at wide/narrow sizes, scene switches, zoom and visibility/accessibility **Closed 20 Oct:** Captured 18 Oct (review/atlas/desktop-2026-10-18); owner accepted 20 Oct.
  controls. Obtain an acceptable visual result; current owner acceptance remains unresolved.
  18 Oct: wide 1400x900 and narrow 420x880 captured and inspected on NVIDIA and Intel, all three scenes
  intact, compact layout as designed (review/atlas/desktop-2026-10-18). Owner acceptance still open.

## P1 — practical integration across stacks

- [x] Compose scene/control APIs and integration documentation.
- [x] Framework-independent web continuous-lens preview and hosted browser checks.
- [x] Production CanvasKit painter/scene API, types, source ownership, cleanup, context recovery,
  source replacement and reusable calm input controller.
- [x] Native SkiaSharp painter and runnable WinForms host; Windows/Linux CI, native screenshots,
  fixture parity, source ownership and Linux visible-label guard.
- [x] Implement and verify remaining native integration paths; document exact prerequisites and **Closed 20 Oct:** Done 20 Oct: MAUI, Qt Quick, Unity added; device checks open as needs-device issues #62-#67, #74, #75.
  gaps. Compose + web + .NET is not equivalent to arbitrary-stack support.
- [x] Native .NET navigation motion/controller integration: `CalmSelectorController`,
  `CalmNavigation`, `DrawCalmBar` / `DrawSelector` with the Compose material (14 October).
- [x] Practical host-owned backdrop/widget capture beyond WinForms standard controls; **Closed 20 Oct:** Done: WinForms, WPF, Avalonia, MAUI (View.Draw), React Native (makeImageFromView), Flutter and Qt (live backdrop).
  custom/other-framework capture is still missing. 19 Oct: WPF added (`LiquidGlass.Wpf`,
  `VisualBackdrop.Capture`, `GlassBitmap`; WpfDemo `--capture` checked in CI, PR #37). Avalonia 12 added too
  (`LiquidGlass.Avalonia`, headless demo in CI on Linux and Windows); React Native added (`ports/react-native`,
  React Native Skia, headless-checked; Pixel 7 checked 20 Oct); Flutter added (`ports/flutter`, live backdrop on
  Impeller, emulator-checked; Pixel 7 Vulkan checked 20 Oct; publishable on pub.dev as `liquiglass` 20 Oct,
  first upload is the owner's); MAUI added 20 Oct (`LiquidGlass.Maui`, Pixel 7 checked, Windows
  built not run); Qt Quick (`ports/qt`, CI offscreen render) and Unity (`ports/unity`, FXC + WARP
  in CI, never run in Unity) added 20 Oct.
- [x] Release pipeline for every registry (19 Oct): one tag publishes Maven Central (with the Gradle
  plugin), npm and NuGet; dry run on PRs; `set_version.py`. Waits on the npm/NuGet tokens and the owner.
- [x] Keep production shader contracts/packaging/licenses synchronized and validate each supported **Closed 20 Oct:** Every port's generator has a --check in CI; packages checked by check-packages.mjs and the dry-run publish.
  path with executable examples; documentation alone does not prove support.

## D1 / C1 — delivery, evidence, automation and protection

- [x] README/API/research/usage guides and per-checkpoint visual/verification packets exist.
- [x] Apache-2.0/NOTICE, free Dependabot/CodeQL/dependency checks and ten strict required checks.
- [x] Preserve Fable/Antigravity work and retire duplicate checkouts/caches; do not repeat cleanup.
- [x] Diagnose 44f01de hosted failure as combined Atlas timeout; split locally with all original
  coverage/resolution and unchanged per-case deadline, three cases passing.
- [x] After the actual fixes, run required affected/full validation once, inspect native images, **Closed 20 Oct:** Full suite run 20 Oct before 1.0 (dev/NEXT-SESSION.md has the count); every CI job green on main.
  update source hashes, limitations, public evidence and verification ledger.
- [x] Audit this 12 October checkpoint: 132 intended files, curated native/cropped proof only;
  raw recordings and private configuration stay local. Repeat the audit for later changes.
- [x] PR6 title/body rewritten; hosted results inspected; all ten required checks green on 32bf46c.
- [x] PR6 merged (02f74a4) on the owner's explicit instruction; no package, tag or release.

## Next action only

Superseded 20 Oct 2026: see "AFTER 1.0" at the top.

Current long-tap source is locally verified:344library passes/27optional skips,3Atlas passes,
Androidassembly and43inspected native phases. Originaltimeout/retry provenance is preserved.
All local queues are terminal; alltenaf333fa hosted checks pass. Sample-only A1 diagnostics
retain one unexplained bottom-right selection failure and two observed33-phase passing repeats.
The87ms first-input JFR is inconclusive; Windows denied the PresentMon ETW session, so display
timing remains open. See NEXT-SESSION.md and the frame-cost packet. Native.NET whole-surface
controller/host integration are hosted verified. New WinForms control capture is locally verified;
publish/check its ownCI next. Return to originalVideo1 repeated-short contour/width mismatch,
thenVideo2optical/contour parity. Native navigation and other host paths remain open.
Do not repeat unchanged library checks or these completed diagnostic probes.
