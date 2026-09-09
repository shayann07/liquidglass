# Where Android necessarily differs from Apple

> Verbatim output of the `liquid-glass-optical-spec` research run. Preserved unedited;
> see [README](README.md) for what it is and how far to trust it.

---

## Where this necessarily differs from Apple on Android

### 1. There is no system backdrop. Every glass element pays for its own.

Apple's material is composited by the OS: on macOS 26 it is a `CABackdropLayer` running a `glassBackground` filter fed by a named source sublayer, with `CASDFLayer`/`CASDFElementLayer` supplying the geometry and `CASDFGlassHighlightEffect` the edge lighting. Android has no equivalent. The host must *record* the backdrop into a `GraphicsLayer` and hand it to the shader, which means:
- an extra full-screen layer per backdrop and a `RenderEffect` per glass element;
- glass can only sample what the app itself drew — never the wallpaper, never another app, never the system UI beneath;
- the recorded layer must be pre-filled with an opaque ground (Android backdrop layers are transparent wherever nothing was painted, and every blur drags that transparency inward);
- glass genuinely cannot sample glass, so Apple's rule against stacking is not a guideline here, it is a hard constraint of the capture model.

### 2. AGSL is Android 13+ (API 33). Below that there is no material at all.

Roughly a third of the Android install base has no `RuntimeShader`. The fallback must be a plainer *material* (tinted surface + the same rim lighting), not a broken one. Apple's analogue is narrower and gentler: on tvOS only Apple TV 4K 2nd-gen and newer support the effects, and older devices simply keep their existing appearance.

### 3. No vibrancy. The shader cannot recolour what is drawn on top of it.

Apple's biggest legibility mechanism for content *on* glass is automatic: "the label automatically becomes vibrant, based on its textColor", and on the Regular variant all placed content gets it, and symbols flip light/dark in lockstep with the glass. That is a compositing mode inside the system's own renderer. Compose has no equivalent, and a fragment shader drawn *behind* the content cannot touch the content. The workaround is the reason `uFlip` in this spec is a **host-supplied scalar rather than a per-pixel `smoothstep`**: the same scalar that inverts the glass is handed to the content layer to pick its label and icon colours, which is the only way to keep them in step. It is one decision per element per frame, not per glyph, so a label straddling a light/dark boundary in the backdrop will not track it the way Apple's vibrancy does.

### 4. No "Reduce Transparency" setting exists on Android.

Apple's three accessibility modifiers map unevenly. Reduce Motion has a real Android analogue (`Settings.Global.TRANSITION_ANIMATION_SCALE == 0`). Increase Contrast has a partial one (`AccessibilityManager.isHighTextContrastEnabled`, and it is about text, not materials). **Reduce Transparency has none.** The `uFrost` uniform in this spec must therefore be driven by an in-app setting the app itself exposes, and an app that ships this material without such a toggle is offering users less control than iOS does. Likewise there is no Android analogue of iOS 26.1's system-level Clear/Tinted "preferred look for Liquid Glass", nor of 26.2's Lock Screen transparency slider.

### 5. The scroll edge effect is a sibling, not a mode.

`UIScrollEdgeEffect` is a first-class per-edge object on `UIScrollView` with `isHidden` and a `style`, visible by default, and floating containers opt into it via `UIScrollEdgeElementContainerInteraction`. On Android it is a separate composable/draw layer the app must place and keep in sync with the same backdrop-luminance signal that drives `uFlip`. Two effects reading one signal in two places will drift under fast scrolling; Apple's does not, because it is one system.

### 6. The frosted interior is grainy where Apple's is smooth.

Nineteen taps cannot blur legible text. Rotating the tap disc per pixel converts the ghosting into noise, and noise reads as blur — but at large radii it is visibly noise. A separable Gaussian would be cleaner but must run *before* the shader, which destroys the sharp rim (see the Chrome preset trade). Apple's composited pipeline has no such either/or.

### 7. Chrome over an app's own ground loses the signature edge, deliberately.

Over a plain dark ground with sparse high-contrast text there is almost nothing to refract, and the little there is arrives half-legible, smeared along the rim and sitting under the labels. The `Chrome` preset therefore uses a real pre-shader blur and a shallow 9 dp bend, trading Apple's most recognisable feature for legibility. This is a real cost of the design, accepted knowingly, and it is a direct consequence of (6).

### 8. No morph identity, no `glassEffectID`, no `glassEffectUnion`.

Apple's morphing is identity-driven; Compose has `LookaheadScope` and shared-element transitions, which can approximate `matchedGeometry` but do not participate in the material's own union. `glassEffectUnion` — forcing shapes into one silhouette at rest regardless of distance — has to be emulated by giving those members an artificially large `uMerge`, which also inflates their bevels, so it is an approximation rather than an equivalent.

### 9. No LOD control, and the rim contains a fold.

Where displacement is steep the sampling Jacobian passes through zero — a caustic fold, which is exactly the compressed inverted rim image that defines the material. AGSL exposes no per-pixel mip bias over a recorded layer, and an isotropic bias would blur the tangential direction and destroy the sharp rim anyway. The mitigation here is bounded displacement (the exact-Snell form self-bounds at √(n²−1)) plus the 1.5 px rim guard, not filtering.

### 10. Coordinate-space hazards Apple never has.

`positionInRoot` differences are wrong the moment anything between the backdrop and the panel applies a transform (a 2.5 % shrink behind a modal slides every panel's sample by 2.5 % of its distance from the backdrop origin — tens of pixels for bottom chrome). The panel must ask the *source* for its position in the source's space. And the padded slice hangs off the recording near a screen edge, so samples must be bounded by the backdrop rather than by the layer, or the rim grows a band of flat fill.

### 11. Things I did not invent to fill Apple's gaps

- No luminance threshold is published for the light/dark flip; ours (0.60/0.44 with 180 ms crossfade) is stated as ours.
- No size boundary between "small, flips" and "large, does not"; ours is 188 dp minimum edge.
- No spring constants for the interactive scale/bounce, no shimmer rate, no materialize duration; all ours.
- No layer count or per-layer definition for "a number of layers"; this shader is one pass and does not claim to reproduce a layer stack it cannot see.
- No gyroscope coupling is claimed as universal, because Apple's own wording is "in some cases" and no Apple API exposes a motion input.
