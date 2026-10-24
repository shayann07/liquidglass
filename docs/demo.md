---
description: Try LiquidGlass in your browser: the Compose Multiplatform demo running on WebAssembly, with glass refracting a moving scene.
---

# Live demo

Atlas Studio, the library's demo app, running in your browser: Compose for Web (WebAssembly) drawing
the same production shaders as Android and desktop. Tap, hold and drag the tab bar; drag the
magnifier; hold and pull the card.

<iframe loading="lazy" src="https://liquidglass.shayxo.dev/demo/" title="Atlas Studio live demo" style="width:100%;height:720px;border:0;border-radius:16px;"></iframe>

[Open the demo full screen](https://liquidglass.shayxo.dev/demo/).

## Material 3

A Material 3 app built only from `liquidglass-material3` (the screen in the [Material 3 guide](material3.md)):
the tab bar and its search button, the segmented control, buttons, chips, a switch and a slider on a
card, and from the menu a dialog and a sheet. The star button switches between the dark and light schemes.

<iframe loading="lazy" src="https://liquidglass.shayxo.dev/demo/#material3" title="Material 3 live demo" style="width:100%;height:720px;border:0;border-radius:16px;"></iframe>

[Open the Material 3 demo full screen](https://liquidglass.shayxo.dev/demo/#material3). It needs a browser with
WebAssembly garbage collection (current Chrome, Edge, Firefox and Safari 18.2 or later).

## First load

A first visit downloads about 4.5 MB (gzip), three quarters of it Skia's WebAssembly runtime, and then
compiles the glass shaders for your GPU: a few seconds on a laptop's integrated graphics, cached by the
browser afterwards. A loader shows while both happen. A browser without WebAssembly garbage collection,
a failed download or a lost graphics context is reported on the page instead of leaving it black. The
two demos above load lazily, as each approaches the viewport (`loading="lazy"`, a hint the browser
may start early). Both WebAssembly files start downloading with the page rather than after its script
has loaded: on an emulated 4G connection Skia's runtime was loaded and drawing (its first WebGL context)
about 0.3 s sooner. The time until the app is usable was not measured. Browsers that cannot run the
demo download nothing.
Measurements: [loader and failures](https://github.com/shayann07/liquidglass/blob/main/review/atlas/web-demo-load-2026-10-20/README.md),
[preloading](https://github.com/shayann07/liquidglass/blob/main/review/atlas/web-demo-load-2026-10-24/README.md).
