# Pixel refinement performance

Same physical Pixel 7 28121FDH2006ZF. Debug builds, live telemetry, mode-1 events. All raw snapshots retained. These results must not be combined with Samsung captures.

| Trial | Build | Frames | Median ms | p95 ms | >20ms | Deadline misses | Submission median ms | Battery before/after |
|---|---|---:|---:|---:|---:|---:|---:|---|
| pixel-performance-live-1 | r11 | 1395 | 13.87 | 23.37 | 31.3% | 0.6% | 11.07 | 33.8/34.2 |
| pixel-performance-live-1 | r13 | 1390 | 14.58 | 18.77 | 4.3% | 0.8% | 11.07 | 34.2/34.5 |
| pixel-performance-nav-2 | r13 | 1432 | 16.33 | 21.95 | 9.2% | 0.1% | 11.07 | 34.9/34.9 |
| pixel-performance-nav-2 | r11 | 1433 | 15.82 | 21.51 | 8.3% | 0.1% | 11.07 | 35.1/35.4 |

FrameCompleted minus IntendedVsync is HWUI latency; submission spacing is separately reported. No claim of statistically established superiority or long-duration thermal stability.
