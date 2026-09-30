# Owner motion repair: physical Pixel 10 verification

2026-09-22. This completes the physical replay that the Pixel 7 disconnection prevented.
It verifies the bounded owner-motion repair, not Apple parity or the entire V3 acceptance matrix.
No production source changed during this verification. No commits, pushes, tags or releases.

## Device and artifact

- Physical Google Pixel 10, Android 16/API 36, `frankel`, serial `56071FDCR00C4Y`;
  adb endpoint `localhost:58657`. Portrait 1080×2424, density 420. Qemu properties empty;
  physical product/fingerprint recorded in `identity.json`. The separately connected emulator was not used.
- Same-device baseline: saved final-polish `vitals-v3.apk`, SHA256
  `b1e80bacc0188d0a540000b3409de04cf0d7b03ecd03e86d5e1e62b448ccd1c8`.
- Final installed candidate: `../vitals-owner-repair.apk`, SHA256
  `73050254c86a1fbc7a3ec2aab9a77c387e153d261bc20fd08e18597dcc3c9fa9`.
  Pulled installed base APK (`installed-vitals.apk`) hashes identically. Candidate remains installed,
  on the root Device tab; see `final-screen.png` and `final-activity.txt`.
- Existing full JVM result applies unchanged: 176 tests, zero failures/errors/skips.
  Only diagnostic capture scripts/helper and evidence documentation changed in this device pass.

## Method and observations

`../device_motion.py` discovers the five root tab cells, checks Vitals foreground and the actual
root bar before each scenario and at completion, and records a continuous injected pointer.
The onboarding overlay was dismissed first. Tab centres were 150, 344.5, 539.5, 734.5, 929.5 px;
the gesture row was y=2211.5. Device-clock event logs and exact keyframes are in each manifest.
Native recordings are retained; 30 Hz crops are resampled silhouette inspection aids, **not
frame-time measurements**. Film time zero is not calibrated to event time zero; equal numbered
frames in separate recordings are not exact phase matches. Motion intervals were inspected,
including intermediate shapes, rather than relying on idle frames or end states alone.

| Scenario | Evidence | Observed result |
|---|---|---|
| Off-centre slow drag and stationary hold | `before/wide_offset.mp4`, `after/wide_offset.mp4`, `width-before-after.png`, `after-wide_offset-active.png` | Old build visibly spans Device/Battery/Storage icon positions; repair stays bounded to the local overlap. Example frame 041 contour x≈312–828 before versus x≈378–750 after (manual ±4 px, illustrative rather than peak or phase-aligned measurements). No three-centre enclosure in inspected candidate frames. |
| Hard swipe both directions | `before/hard_right.mp4`, `before/hard_left.mp4`, corresponding `after/` videos and active sheets | Candidate deforms and flattens during travel, then settles onto Sensors/Vitals respectively; no retained held state or escape after settling. |
| Stop at far edge, then release | `before/stop_end.mp4`, `after/stop_end.mp4`, `after-stop_end-active.png` | Candidate moves toward the selected end centre while shrinking through intermediate shapes. The old faster collapse is visibly different. No renewed large expansion observed during the candidate recovery. |
| Slow release at each endpoint | `additional/slow_left.mp4`, `additional/slow_right.mp4` | Correctly selects Vitals/Sensors even though the wide visible body is kept inward by containment. |
| Stationary partial-content overlap | `additional/stationary_overlap.mp4` | Bounded accommodation continues under a stationary finger; release selects Device and returns to rest. |
| Short and long ordinary taps | `additional/tap_short.mp4`, `additional/tap_long.mp4` | Arrive at Device/Sensors with the faster tap behaviour retained; do not acquire the slower held-recovery timing. This does not establish an Apple-like tap silhouette. |
| Retarget during travel | `retarget-fixed/retarget.mp4`, manifest and active sheet | Second DOWN occurs 81 ms after first UP (device uptimes 8915688→8915769); selector reverses during travel and ends on Device. |

Ten distinct candidate scenarios were inspected (four paired A/B and six additional); the
retarget scenario needed one diagnostic rerun. The first `additional/retarget` attempt had a
1,501 ms gap from first UP to second DOWN due to remote adb overhead. It is explicitly INVALID
for rapid retarget and retained as such in its manifest. The helper now executes both taps and
the wait inside one device process; `retarget-fixed/` is the valid record.

## Verdict and limits

- **PASS for the physical functional replay:** the named motions run, final selected tabs are
  correct, the excessive three-icon widening is absent in inspected candidate frames, and
  edge recovery is visibly coordinated. Formal 1.9-slot contour bounds and long-uptime invariance
  remain production-controller/JVM evidence, supported qualitatively here rather than re-estimated
  to subpixel precision from video. Physical video does not reproduce a twenty-day uptime.
- No Vitals crash/ANR observed. `process-exit-info.txt` contains only the baseline replacement;
  captured app logs contain no matched fatal exception, fatal signal, ANR or SkSL error.
  This short replay is not a new two-minute soak or a performance benchmark.
- **PARTIAL for iOS similarity; owner acceptance pending.** Width limits, flattening and common
  recovery dynamics are authored approximations. Apple stills cannot certify their exact timing.
  The ordinary-tap look, background optics and native-ink rendering were not redesigned here.
- Prior end-to-end compositor error (1.426 levels versus 1) and prior timing failures (+6 ms
  median versus +2; p95 ratio 1.30 versus 1.10) remain unresolved historical failures. No fresh
  timing comparison was made on Pixel 10; do not transfer old-device numbers as its measurements.

Review the installed build and these active videos. A passing replay must not be promoted into
an assertion that all V3 or Apple-matching requirements are finished.
