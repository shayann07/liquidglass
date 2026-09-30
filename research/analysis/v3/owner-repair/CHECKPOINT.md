# Owner motion repair — 2026-09-22

The owner rejected the prior V3 build. Passing its old tests did not establish the requested
motion. This pass changes production code; it is not another implementation prompt.

## Current status

Final Vitals review APK built, installed on Pixel 7 and pulled back byte-identical. The owner
subsequently supplied a physical Pixel 10: **the same-device before/after motion replay is now
complete**, and the installed candidate was pulled back hash-identical there too. See
`pixel10/VERIFICATION.md`, `pixel10/identity.json` and `vitals-owner-repair.apk`.
The full fresh JVM suite passes: **176 tests, zero failures,
errors or skips** (`final-full-suite.log`, `test-summary.json`). The focused suite also passes.
No commits, pushes, tags, releases or remote publication.

## Changes and their reasons

- Pointer timestamps in `GlassTabBar.kt` now use Double seconds. Previously absolute Android
  uptime was divided by `1000f`, then stored as Float. At this Pixel's roughly 20-day uptime,
  representable timestamps are 125ms apart. Smooth 8ms pointer events therefore drove stale,
  stepped deformation velocity. Only the Double time *difference* is now narrowed to Float.
  A production-controller regression compares identical drags at time zero and after 20 days,
  requiring equal position, shape and velocity. Prior tests all used clocks near zero.
- `GlassSelectorDynamics.kt`: enforce the width ceiling on the final body in every constraint
  set. Previously the admission ceiling allowed an extra slot on each side and speed stretch
  was added afterwards; the solver's only width bound was the entire bar. The default final
  ceiling is now 1.9 item slots. Explicitly wider resting pills retain their minimum extent.
- Accommodation and speed strain share that budget: `Wtarget = min(Wlimit,
  max(2*accommodationHalfWidth, 2*heldHalfWidth + speedExtra))`. Full padded raw-label containment
  remains a diagnostic preference, not a reason to swallow another item or shrink text.
- `grabCentreFor` reports navigation intent bounded by the first and last **item** centres.
  The visible body's separate containment may move it inward; its width must not make an
  outermost tab unreachable on a slow release. The before regression returned x189.32 instead
  of the intended first centre x113.25.
- A genuine held release recovers `(cx, cy, L, r, k)` through the same critically damped
  operator toward the resting body. Centre speed does not generate fresh stretch during this
  shrink. Because `W=L+2r` is linear, it follows the same operator; incoming velocities remain
  state. Ordinary taps retain their faster tap dynamics. Re-grabbing replaces the old shrinking
  release walls, and a long background gap without a finger adopts rest without replaying it.
- Defaults `releaseOmega=10/s` and `maxRadiusDrop=0.30` replace 18/s and 0.12. These are **authored
  approximations**, not recovered Apple constants. The main zero-speed width recovery measures
  333ms in the controller. E7's corrected 313.8ms observation is a raised-area duration, a
  different metric; 367ms must not be reintroduced as an independent silhouette measurement.
- The previous conservative recording allocation is retained separately from the hard contour
  limit. Reducing the offscreen extent also changed resampling phase and failed the existing
  touch-warp image gate. Restoring the previous allocation passes that gate. No performance
  improvement is claimed from a smaller texture.

No optical shader, material coefficient, measured resting map, or native-ink compositor was
changed. `GlassTabBarStyle.V3` remains opt-in; the capsule presets are unchanged.

## Evidence and revised assumptions

Original Apple reference crops are `apple_*_display.png`: ICC-converted display-base geometry,
**not calibrated HDR photometry**. Approximate readings, ±3–5px:

| Images | Visible width / (bar width divided by 5) | Behaviour |
|---|---:|---|
| 6717, 6719 | 1.38 | held, then partial overlap |
| 6721 | 1.73 | stationary growth, almost unchanged centre |
| 6734, 6735 | 1.55, 1.51 | hard-swipe flattening |
| 6698 | 1.58 | long-tap midpoint |

6721 visibly refracts the Communities label at its rim. The previous requirement to enclose the
whole unwarped padded label was stronger than the reference supports. The ordered end-release
stills show shrinking and translation together; they cannot determine damping or timestamps.

`GlassSelectorOwnerRegressionTest` adds long-uptime invariance, final-contour width/three-centre checks across off-centre
grasps, both endpoint intent checks, both stopped endpoint releases, coherent recovery phase and
duration, ordinary-tap isolation, and re-grab ownership. Before results are in `before-tests.xml`.
The oversized-label growth test now uses a feasible 300px label instead of demanding a 420px
label plus padding fit inside a sub-two-slot body. Existing constrained-demand tests are retained.

## Device protocol

`OwnerGesture.java` injects one continuous DOWN→hold→move→stop→UP, using the physical device's
event clock. `device_motion.py` reads the root tab geometry from accessibility and captures
off-centre slow growth, hard swipes to both ends, and stop-at-end release. Before videos are in
`before/`. Full 30Hz active-region crops are retained, with 100ms labelled contact sheets. The
old final-polish contact sheets sampled percentages of recordings, frequently only idle frames;
they cannot validate the missing motion.

For a repeat, rediscover the authorized physical phone, leave Vitals open in portrait and use a
new output directory from the library root (the script refuses to overwrite an existing run):

```powershell
$env:OWNER_SERIAL='localhost:58657' # re-check adb identity first
& 'C:\Users\shaya\AppData\Local\Programs\Python\Python312\python.exe' research/analysis/v3/owner-repair/device_motion.py pixel10/repeat
```

Rediscover adb identity/foreground first. Compare **active intervals**, not equal percentages
of videos. The script refuses to run unless Vitals and its five root tab cells are present.

The Pixel 10 replay, endpoint checks, taps and corrected device-timed retarget are inspected and
reported in `pixel10/VERIFICATION.md`. The earlier endpoint quantisation and frame-time failures remain
unresolved; this pass must not relabel them PASS. No iOS parity or owner acceptance is claimed.

## Completion audit

| Requirement | Current evidence | Status |
|---|---|---|
| Actual body cannot grow across three normal five-bar item centres | Final contour inequality and off-centre production-controller sweeps, max 1.9 slots; Pixel 10 replay supports bounded width | Code/JVM PASS; physical qualitative PASS for inspected frames |
| Both end tabs remain reachable independent of bubble width | Endpoint-intent regression and Pixel 10 slow release onto each endpoint | Code/JVM and physical PASS |
| Coherent end release, no second stretch, ordinary tap preserved | Both endpoint traces, common recovery-phase test, 333ms width duration, tap isolation and re-grab tests | Code/JVM PASS; reference similarity remains approximate |
| Smooth input velocity at real device uptime | Double-time production adapter plus zero-origin vs twenty-day controller invariant | PASS |
| Preserve optics and existing work | No shader changes; original recording allocation retained; full suite green; HEADs unchanged | PASS within existing tolerances; old known failures still open |
| Deliver the actual build to a physical phone | Pixel installed APK pulled back with matching SHA256 in identity.json | PASS for installation only |
| Judge final motion on the physical device | Pixel 10 same-device A/B, 10 candidate scenarios including taps and device-timed retarget | Functional replay PASS; iOS similarity PARTIAL and owner acceptance pending |

The Pixel 7 interruption was resolved by the owner's authorization to use physical Pixel 10.
Final candidate remains installed on it. No emulator evidence substitutes for these captures;
the wider project must not be reported visually finished solely from this bounded repair.
