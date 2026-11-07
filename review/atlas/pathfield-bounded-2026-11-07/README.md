# Custom-path field build: bounded transform (#81)

Source: branch `perf/81-field-build` from `main` 13ec13b, 7 November 2026. Desktop JVM, direct calls to
`buildPathField` (not shader rendering, not presented frames). **No Android or device claim.** Optical output
is unchanged by construction and by test; there is nothing visual to look at, so this record has no frames.

## What changed

`GlassPathField.kt`'s distance transform visits less of the padded layer:

1. **Seeds are not revisited.** A pixel at distance 0 is never improved by a neighbour (every candidate offset
   is at least 1 away), so the sweeps now step over it. Pure no-op elimination.
2. **Far blocks are not visited.** The layer is cut into 16 px blocks. A block is *far* when it lies more than
   `1.5 band + 8 px` (the encoded range plus a margin) from every block the outline passes through. The
   encoder saturates at 1.5 band, so every pixel in a far block stores the same byte whatever distance it
   was given. A far block is one colour of mask, so its pixels are seeds (never changed) or pixels whose
   transform value is past the range. They are left unvisited and read as "beyond range".
3. **Safety net.** If a visited pixel is ever left unreached, or the layer has no outline, or nothing is far
   (small layers), the whole-layer transform runs as before.

Why the near pixels do not change: the 8-point transform only ever accepts a neighbour's offset plus one
step, so along the chain back to the seed that supplied it, offsets never shrink. A pixel nearer than the
range can therefore only be supplied by chains that stay nearer than the range, and those run entirely
through pixels that are visited. Skipped pixels could only have offered values that a nearer pixel would
have rejected. A field texel is 2 × 2 px and blocks are an even 16 px, so no texel straddles a skipped and a
visited block.

No published API, shader, material parameter, field resolution, encoding or motion constant changed.
`buildPathField` gained an internal `bounded` parameter (default true) so tests can ask for the whole-layer
transform.

## Gates (all green)

| Gate | Result |
| :-- | :-- |
| `FieldAllocationBenchmarkTest`: pixels equal to the unmodified upstream algorithm (`Baseline.kt`), three sizes | identical |
| `FieldBoundedBaselineTest` (opt-in, `tools/benchmarks/pathfield`): 59 shape / size / pad / band cases against upstream | identical |
| `GlassPathFieldBoundedTest` (new, in the normal suite): bounded vs whole-layer transform over rounded rectangles, squares, stars, blobs, even-odd rings, separate islands, notches, a 6 px bar, a 1 px hairline and an empty path, at 8 sizes with odd pads and bands 8-96; plus a check that a 1080 × 720 panel really leaves blocks unvisited | 3 / 3 |
| `GlassFieldPadConsumerTest` 5, `GlassPadContractTest` 5, `GlassPathField*Test` 21 (buffer 5, cache regression 4, off-composition 7, path field 2, bounded 3) | 31 / 31 |

## Timings

Windows 11, Intel i7-10700 shared with other work (noisy: other Gradle builds were running; a first run showed
the same baseline 1.5-2x slower). Paired in one JVM, 61 samples per mode after 40 warm-ups, alternating order,
notched closed path, 24 px pad, band 48. `main` is the transform on `main` 13ec13b
(`tools/benchmarks/pathfield/PreviousMain.kt`); "production" is the pooled build the library calls.
p50 in ms:

| Panel (px) | upstream 1.0.0 | `main` (pooled) | this change (pooled) | change vs `main` | p95 `main` → new |
| :-- | --: | --: | --: | --: | --: |
| 512 × 256 | 11.59 | 9.98 | 8.42 | -16% | 14.25 → 11.92 |
| 1080 × 300 | 29.27 | 26.41 | 21.03 | -20% | 45.69 → 32.08 |
| 1080 × 720 | 57.93 | 53.06 | 31.23 | -41% | 69.66 → 50.64 |

The saving grows with the panel because the far interior is what is skipped: a 300 px panel has roughly
40% of its rows beyond the range, a 720 px one about half. Small panels, where the band covers everything,
are only helped by skipping the seeds. Allocation rises a little (the block map): median bytes per build, `main`
against this change, 1,874,280 to 1,878,288 (+0.21%) at 512 × 256, 10,993,064 to 11,002,128 (+0.08%) at 1080 × 300
and 24,258,344 to 24,276,632 (+0.08%) at 1080 × 720. (The first version of this record said +0.07%, which only the
largest panel gave; the benchmark printed `bytes_p50` from the 25-sample index 12 after the sample count went to 61. The
medians do not change with the index because the allocation of one build is the same every time, so only the
statement was wrong, and the benchmark now reads index 30.) Run-to-run spread on this machine is large; the paired
order is the evidence, not the absolute numbers.

**Second run, after the review fixes (7 November, same machine and method, shared with other work):** p50 `main` against
this change 11.55 to 9.66 ms (-16%) at 512 × 256, 24.61 to 18.72 ms (-24%) at 1080 × 300, 61.11 to 41.29 ms (-32%) at
1080 × 720; upstream 12.75, 26.89 and 68.93 ms. The saving is -16% to -41% across the two runs, the same ordering both
times and the same growth with the panel; the fields were byte-identical again (59 cases).

Reproduce:

```
./gradlew --init-script tools/benchmarks/pathfield.init.gradle :liquidglass:jvmTest \
  --tests '*FieldAllocationBenchmarkTest' --tests '*FieldBoundedBaselineTest' --rerun
```

then read `FIX_BENCH` / `BOUNDED_BASELINE` from `liquidglass/build/test-results/jvmTest/*.xml`
(mode 0 upstream, 1 fresh, 2 production, 3 previous `main`).

## Limits

- Desktop JVM wall time. Android (ART), Wasm and the phone's CPU were not timed; the saving on a device is
  unmeasured and may differ. No frame-rate claim.
- Size-only rebuilds already run off composition (#81, 24 October); this makes each build cheaper, on or off
  the main thread. The first build and shape changes are still synchronous.
- The estimate of about 30% saved noted on 5 November held at the large size and fell short at the small one.
- Device check pending: nothing to see; if timed on the Pixel, a panel-size change should build faster.
