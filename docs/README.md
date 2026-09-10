# liquidglass — documentation

A refracting glass material for Compose Multiplatform, drawn entirely in a shader.

If you are here to use it, start with [Getting started](getting-started.md). If you are here to
change the optics, read [How it works](how-it-works.md) and then
[the research](research/README.md). If you are here to decide whether it fits your app, read
[Limitations](limitations.md) first — it is deliberately the most detailed document here.

## Guides

| | |
| :--- | :--- |
| [Getting started](getting-started.md) | The three-line version, and the one rule about where the backdrop goes. |
| [Shapes](shapes.md) | Rounded rects, capsules, squircles, and arbitrary paths. |
| [Interaction](interaction.md) | Press, materialize, and morphing between styles. |
| [Tab bar](tab-bar.md) | `GlassTabBar`: the measured selection lens, and what each number in it comes from. |
| [Roadmap](roadmap.md) | What is planned, what the competitive audit changed, and what is deliberately not being done. |
| [Adaptation](adaptation.md) | Light/dark inversion, legibility, shadows, the scroll edge effect. |
| [Accessibility](accessibility.md) | Reduce Transparency, Increase Contrast, Reduce Motion. |
| [How it works](how-it-works.md) | The optical model, layer by layer, and why each part is there. |
| [Performance](performance.md) | What it costs, measured, and what to do if it costs too much. |
| [Limitations](limitations.md) | What it cannot do. Read before promising anyone parity with iOS. |
| [API reference](api-reference.md) | Every public symbol, with signatures. |

## Reference

- [Research](research/README.md) — 27.6M tokens of adversarially-verified derivation, preserved
  verbatim: the optical model, every parameter with its provenance, the motion model, and an
  honest account of where Android cannot match Apple.
- [Reference measurements](research/reference-measurements.md) — the one first-hand source:
  numbers read off a recording of a real iOS 26 tab bar, and the three places they overturned a
  derivation.
- [Publishing](publishing.md) — maintainer notes: coordinates, Maven Central, signing, and the
  release workflow.

## The one-paragraph version

Liquid Glass is **displacement-first, blur-second**. Apple's own framing is that inversion:
earlier materials scattered light, this one bends and concentrates it. A thin bevel around the
rim bends a *sharp* backdrop inward, compressing the surroundings into a legible band at the
edge, while the flat interior scatters and tints. Almost everything that makes an
implementation look wrong comes from getting that ordering backwards — blur the backdrop before
the shader sees it and you have destroyed the detail the rim exists to bend, and no amount of
tuning gets it back.
