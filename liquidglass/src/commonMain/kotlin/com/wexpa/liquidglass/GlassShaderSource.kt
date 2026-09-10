package com.wexpa.liquidglass

/**
 * The glass shader, in the SkSL dialect shared by AGSL (Android 13+) and Skia (Desktop).
 *
 * Apple publishes no numbers for Liquid Glass — no blur radius, no index of refraction, no
 * falloff exponent, no specular formula. What it publishes is architecture and
 * direction-of-effect. This implements that architecture; the numbers are ours.
 *
 * The material is **displacement-first, blur-second**. Apple's own framing is that inversion:
 * earlier materials scattered light, this one bends and concentrates it. That single property
 * is what separates it from glassmorphism, and the rest follows from it:
 *
 *  1. **The sample region is larger than the element.** Apple describes the material as
 *     sampling content from an area larger than itself, and only *outward* displacement needs
 *     that. The host records a padded backdrop and passes [uPad]; the panel occupies the inset
 *     rect, so the rim reaches real content instead of clamping against its own edge.
 *  2. **The rim is sharper than the middle.** The edge band shows a compressed, warped,
 *     *legible* image of what lies just outside; the interior is soft. A backdrop blurred
 *     before the shader sees it cannot do this — the detail the rim exists to bend is already
 *     gone — so the scatter lives here, keyed to distance from the edge.
 *  3. **Parameters are keyed to element size.** Larger glass reads more opaque with deeper
 *     shadow and stronger lensing; smaller glass reads clearer. [uScale] carries that.
 *  4. **Tint is a tone mapping, not an overlay**, and it is per-pixel, because coloured glass
 *     really does vary with what is behind each point. Light/dark inversion is the opposite:
 *     one decision for the whole element, because symbols drawn on top have to flip in lockstep
 *     with it and only a scalar the host also gives the content layer can do that. So [uFlip]
 *     arrives already decided rather than being derived per pixel here.
 *  5. **The edge is lit, not outlined.** A dark border is the fastest way to make a material
 *     like this read as a drawn rectangle rather than a solid.
 */
internal const val GLASS_SHADER_SOURCE = """
uniform shader content;        // padded backdrop, opaque by construction, unblurred
uniform shader field;          // sampled distance field, used only when uShapeKind is 1

uniform float2  uSize;         // panel size, px (not counting padding)
uniform float   uPad;          // px of backdrop recorded beyond each edge
uniform float4  uBackdrop;     // l, t, r, b of the region that actually holds recorded pixels
uniform float3  uBase;         // opaque ground the backdrop is painted on
uniform float4  uRadii;        // corner radii: top-left, top-right, bottom-right, bottom-left

uniform float   uRefractBand;  // px from the rim over which refraction acts
uniform float   uRefractDepth; // px of displacement at the rim
uniform float   uIor;          // index of refraction; shapes the falloff, not its magnitude
uniform float   uBevelPower;   // bevel profile exponent: 2 a circular arc, 4 squircle-matched
uniform float   uCornerPower;  // OUTLINE corner exponent: 2 a circular arc, 4 an Apple squircle
uniform float   uShapeKind;    // 0 analytic rounded rect, 1 sampled field
uniform float   uFieldRange;   // px the sampled field's 0..1 range spans, centred on zero
uniform float   uFieldScale;   // layer px -> field texel; the field is stored downscaled
uniform float   uAberration;   // per-channel split as a fraction of the displacement
uniform float   uMirror;       // amplitude of the mirrored edge band
uniform float   uBlur;         // px radius of the interior scatter, tapering to 0 at the rim

uniform float   uBevel;        // px width of the lit bevel (uRefractBand is 4-7x this)
uniform float2  uLight;        // unit vector toward the key light
uniform float   uSpecular;     // rim highlight intensity, 0..1
uniform float   uSpecularPow;  // rim highlight tightness
uniform float   uCounterLight; // how much of the key light reaches the side facing away, 0..1
uniform float   uEdgeLight;    // brightness of the outermost line relative to the bevel lobe
uniform float   uBevelPeak;    // where in the bevel the highlight peaks: 0 at the edge (chamfer), 0.4 inside (bead)
uniform float   uEdgeShadow;   // dark separating contour at the outermost pixel, 0..1
uniform float   uRimSoft;      // px the compressed rim image is smeared along the normal
uniform float   uTintAbsorb;   // 0 tint as a blend, 1 tint as an absorbing medium
uniform float   uFresnel;      // Schlick rim reflectance gain
uniform float   uHiChroma;     // how much of the highlight is lightness rather than white
uniform float   uInnerShadow;  // strength of the inner thickness line

uniform float4  uTint;         // rgb + strength
uniform float   uAdaptive;     // how far the tint tone-maps against backdrop brightness
uniform float   uLegibility;   // how far local backdrop contrast raises tint strength
uniform float   uScale;        // element-size factor: 0 small and clear, 1 large and opaque
uniform float   uFlip;         // whole-element light/dark inversion, 0..1, decided by the host

uniform float2  uTouch;        // touch point in panel-local px
uniform float   uTouchAmt;     // press amount, 0..1
uniform float   uMaterialize;  // 0 the shader is a pass-through of the backdrop, 1 full material
uniform float   uFrost;        // Reduce Transparency, 0..1
uniform float   uContrast;     // Increase Contrast, 0..1

// The Ln norm. At n = 2 this is `length`, so the rounded rect below is bit-identical to a
// circular-cornered one; at n = 4 the corner becomes the superellipse Apple actually uses.
float lnNorm(float2 v, float n) {
    if (n <= 2.001 && n >= 1.999) {
        return length(v);
    }
    return pow(pow(v.x, n) + pow(v.y, n), 1.0 / n);
}

// A rounded rectangle whose corners are Lame curves rather than circular arcs.
//
// Apple's shapes are squircles, and Compose's RoundedCornerShape is a circular arc, so a
// panel drawn with the platform shape and lit by a circular-corner field is subtly wrong at
// exactly the place the eye checks. Swapping the corner's L2 norm for an Ln one fixes the
// outline without changing anything else; the field is no longer a true Euclidean distance in
// the corner, but its zero set is exact and its gradient points the right way, which is all
// the refraction and the lighting read from it.
float sdRoundRect(float2 p, float2 halfSize, float4 r) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + lnNorm(max(q, float2(0.0)), uCornerPower) - radius;
}

// The outward unit normal of sdRoundRect, in closed form.
//
// Only valid away from the medial axis, where the true gradient is discontinuous; the caller
// checks that before using it. `gradRadius` is deliberately larger than the outline's radius,
// which rotates the normal through the corner over a wider arc than the outline turns and
// removes the direction kink where the corner meets the flat run. It does not move the outline.
//
// The closed-form gradient and the wider-radius trick are Kyant0/AndroidLiquidGlass's; the Ln
// corner, the degenerate guards and the caller's medial-axis handling are ours. See NOTICE.
$GLASS_OKLAB_SOURCE

float2 gradRoundRect(float2 p, float2 halfSize, float4 r, float power) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + gradRadius;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        float L = lnNorm(q, power);
        if (L <= 1e-5) {
            return s * float2(0.70710678, 0.70710678);
        }
        float2 w = float2(
            pow(q.x / L, power - 1.0),
            pow(q.y / L, power - 1.0));
        float wl = length(w);
        return (wl <= 1e-5) ? s * float2(0.70710678, 0.70710678) : s * (w / wl);
    }
    // Flat run: the nearest edge is whichever of the two is closer.
    float ax = step(q.y, q.x);
    return s * float2(ax, 1.0 - ax);
}

// One backdrop sample, bounded by the region that actually holds recorded pixels.
//
// The layer is filled with the ground before the backdrop is drawn into it, so it is opaque by
// construction and any alpha shortfall here is filtering error at its own boundary rather than
// real transparency. Dividing it out reconstructs the colour; compositing toward the ground
// instead paints a halo of the ground colour around every panel.
// The shape, as a signed distance in px: negative inside, zero on the outline.
//
// Rectangles, rounded rectangles, capsules, circles and squircles all have a closed form and
// are evaluated directly. Anything else - a star, a blob, a hand-drawn GenericShape - has no
// closed form, so the host rasterises it once and measures it, and this reads the measurement.
// The two paths are interchangeable from here on: everything downstream asks the same two
// questions of whichever one is in play.
float sdShape(float2 p, float2 halfSize) {
    if (uShapeKind < 0.5) {
        return sdRoundRect(p, halfSize, uRadii);
    }
    // Field coordinates are the padded layer's, so undo the centring the caller applied — and
    // then scale into the field's own resolution, which is lower than the layer's because a
    // distance field is smooth and bilinear sampling puts back more than the halving takes out.
    float2 layerCoord = (p + halfSize + float2(uPad)) * uFieldScale;
    float stored = float(field.eval(layerCoord).r);
    return (stored - 0.5) * 2.0 * uFieldRange;
}

half3 backdropAt(float2 p) {
    float2 q = clamp(p, uBackdrop.xy, uBackdrop.zw);
    half4 c = content.eval(q);
    return (c.a > half(0.004)) ? c.rgb / c.a : half3(uBase);
}

float2 rotate(float2 v, float2 rot) {
    return float2(v.x * rot.x - v.y * rot.y, v.x * rot.y + v.y * rot.x);
}

// A cheap per-pixel angle; any hash with no visible structure will do.
float2 tapRotation(float2 coord) {
    float3 q = fract(float3(coord.x, coord.y, coord.x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    float a = fract((q.x + q.y) * q.z) * 6.2831853;
    return float2(cos(a), sin(a));
}

// Nineteen taps: three rings of six at staggered angles plus the centre. Too few to blur
// legible text on their own, so the whole pattern is rotated per pixel and the ghosts break up
// into noise, which the eye integrates as blur. Only ever called with a radius that falls to
// zero at the rim, so the expensive case never coincides with the case that needs detail.
half3 blurredAt(float2 p, float radius, float2 rot) {
    if (radius < 0.5) {
        return backdropAt(p);
    }
    float inner = radius * 0.55;
    float mid = radius * 0.8;
    half3 sum = backdropAt(p);
    sum += backdropAt(p + inner * rotate(float2( 1.000,  0.000), rot));
    sum += backdropAt(p + inner * rotate(float2( 0.500,  0.866), rot));
    sum += backdropAt(p + inner * rotate(float2(-0.500,  0.866), rot));
    sum += backdropAt(p + inner * rotate(float2(-1.000,  0.000), rot));
    sum += backdropAt(p + inner * rotate(float2(-0.500, -0.866), rot));
    sum += backdropAt(p + inner * rotate(float2( 0.500, -0.866), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.866,  0.500), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.000,  1.000), rot));
    sum += backdropAt(p + radius * rotate(float2(-0.866,  0.500), rot));
    sum += backdropAt(p + radius * rotate(float2(-0.866, -0.500), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.000, -1.000), rot));
    sum += backdropAt(p + radius * rotate(float2( 0.866, -0.500), rot));
    sum += backdropAt(p + mid * rotate(float2( 0.966,  0.259), rot));
    sum += backdropAt(p + mid * rotate(float2( 0.259,  0.966), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.707,  0.707), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.966, -0.259), rot));
    sum += backdropAt(p + mid * rotate(float2(-0.259, -0.966), rot));
    sum += backdropAt(p + mid * rotate(float2( 0.707, -0.707), rot));
    return sum * half(1.0 / 19.0);
}

float luma(half3 c) {
    return dot(float3(c), float3(0.2126, 0.7152, 0.0722));
}

// Superellipse bevel height h(e) = T * (1 - (1-e)^p)^(1/p), for e = depth/band in [0,1].
// This returns dh/ds, the surface slope: p = 2 is a true circular arc, p = 4 the
// squircle-matched profile. The slope is 0 at the inner edge of the band and diverges at the
// rim, so e is clamped a hair inside both ends.
float bevelSlope(float e, float p) {
    float u = 1.0 - clamp(e, 0.002, 0.998);
    float up = pow(u, p);
    return pow(u, p - 1.0) / pow(max(1.0 - up, 1e-4), 1.0 - 1.0 / p);
}

// Exact Snell deviation for a surface of that slope, returned as tan(theta - theta_t) via the
// tangent difference identity, so it contains no transcendental at all.
//
// The usual shortcut is slope * (1 - 1/n), which overestimates by about a fifth at this
// profile's own peak and diverges at the rim, which is why every shader that uses it also
// carries an ad-hoc clamp. The exact form is self-bounding: as the slope goes vertical it
// tends to sqrt(n^2 - 1), so no clamp is needed and the index of refraction becomes a knob on
// the shape of the falloff rather than on its magnitude.
float snellShift(float slope, float ior) {
    float inv = inversesqrt(1.0 + slope * slope);
    float si = slope * inv;
    float ci = inv;
    float st = si / max(ior, 1.0001);
    float ct = sqrt(max(1.0 - st * st, 0.0));
    return (si * ct - ci * st) / max(ci * ct + si * st, 1e-4);
}

// Tone-map one tint colour across the backdrop's brightness, the way a pane of coloured glass
// does: it darkens and saturates over bright ground, lifts and desaturates over dark ground,
// and stays recognisably the same colour throughout. At uAdaptive 0 it does nothing at all,
// which is what Apple's clear variant requires — it "does not have adaptive behaviours".
half3 toneMappedTint(half3 tint, float bgLuma, float adapt) {
    float g = clamp(bgLuma, 0.0, 1.0);
    float value = mix(1.0, mix(1.10, 0.88, g), adapt);
    float sat   = mix(1.0, mix(0.88, 1.06, g), adapt);
    half mean = half(dot(float3(tint), float3(1.0 / 3.0)));
    half3 shifted = half3(mean) + (tint - half3(mean)) * half(sat);
    return clamp(shifted * half(value), half3(0.0), half3(1.0));
}

half4 main(float2 coord) {
    // The panel is inset inside the padded backdrop.
    float2 local = coord - float2(uPad);
    float2 halfSize = uSize * 0.5;
    float2 p = local - halfSize;

    float d = sdShape(p, halfSize);
    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    float scale = clamp(uScale, 0.0, 1.0);

    // Geometry. A one-pixel epsilon collapses the gradient magnitude from 1 to 0 to 1 across
    // the medial axis of a pill and leaves a one-pixel seam of zero refraction down its middle.
    // Widening the epsilon to the scale of the band lets the two sides cancel gradually, and
    // the gradient magnitude that falls out doubles as a confidence term that fades the lens
    // toward the axis instead of letting its direction flip.
    //
    // When the shape is analytic and its band stops short of the axis, none of that applies and
    // the normal has a closed form: four fewer field evaluations per pixel, and no fade.
    float inradius = min(halfSize.x, halfSize.y);
    float2 n;
    float axisFade;
    if (uShapeKind < 0.5 && uRefractBand < inradius * 0.75) {
        n = gradRoundRect(p, halfSize, uRadii, uCornerPower);
        axisFade = 1.0;
    } else {
        float eps = clamp(uRefractBand * 0.3, 1.0, 16.0);
        float2 ex = float2(eps, 0.0);
        float2 ey = float2(0.0, eps);
        float2 g = float2(
            sdShape(p + ex, halfSize) - sdShape(p - ex, halfSize),
            sdShape(p + ey, halfSize) - sdShape(p - ey, halfSize));
        float gLen = length(g);
        n = g / max(gLen, 1e-5);
        axisFade = smoothstep(0.15, 0.80, gLen / (2.0 * eps));
    }

    float depth = max(-d, 0.0);

    // A dome field for wide shapes whose band reaches their own centre line.
    //
    // Distance-to-edge collapses along the medial axis of a long pill: the two sides cancel, the
    // gradient has no direction, and the fade above gives up refraction exactly down the spine.
    // Past an aspect ratio of 1.5, and once the band approaches the inradius, blend the distance
    // field into a dome instead — a smooth rectangular radius that is zero at the centre and one
    // on every edge, with a closed-form gradient everywhere and no axis to collapse on. The idea
    // is chrisbanes/haze's; the blend into our own field and profile is ours.
    //
    // Only the refraction reads this. The lighting keeps the true distance, because the lit edge
    // belongs to the outline and not to the dome.
    float aspect = max(halfSize.x, halfSize.y) / max(min(halfSize.x, halfSize.y), 1e-4);
    float domeWeight =
        smoothstep(1.5, 3.0, aspect) *
        smoothstep(0.75, 1.0, uRefractBand / max(inradius, 1e-4));
    float opticalDepth = depth;
    if (domeWeight > 0.001) {
        float2 q = p / max(halfSize, float2(1e-4));
        float2 q2 = q * q;
        float domeRadius = sqrt(clamp(q2.x + q2.y - q2.x * q2.y, 0.0, 1.0));
        opticalDepth = mix(depth, (1.0 - domeRadius) * uRefractBand, domeWeight);
        float2 domeGrad = float2(q.x * (1.0 - q2.y), q.y * (1.0 - q2.x));
        float domeLen = length(domeGrad);
        if (domeLen > 1e-4) {
            n = normalize(mix(n, domeGrad / domeLen, domeWeight));
        }
        axisFade = mix(axisFade, 1.0, domeWeight);
    }

    float e = clamp(opticalDepth / max(uRefractBand, 0.001), 0.0, 1.0);
    float pw = max(uBevelPower, 1.5);
    float slope = bevelSlope(e, pw);

    // Refraction, normalised by the shift at the rim so uRefractDepth is literally the peak
    // displacement in px. Faded over the inner quarter of the band so the warp field is C1
    // where it meets the flat interior; a kink there resamples as a visible ring. The 1.5px
    // guard stops the outermost antialiased pixel fetching from beyond coverage.
    float bend = snellShift(slope, uIor) / max(snellShift(bevelSlope(0.0, pw), uIor), 1e-4);
    bend *= smoothstep(1.0, 0.75, e) * axisFade * smoothstep(0.0, 1.5, depth);
    // The dome has no rim of its own, so its bend must still die at the real outline.
    bend *= mix(1.0, smoothstep(0.0, 2.0, depth), domeWeight);
    bend = clamp(bend, 0.0, 1.0);

    float mat = clamp(uMaterialize, 0.0, 1.0);
    float lens = uRefractDepth * mix(0.85, 1.25, scale) * mat;

    // The touch magnifier. Apple describes the material moving in tandem with the interaction
    // and illuminating from within, starting under the fingertip; this is the optical half of
    // that — the backdrop swells slightly toward the finger. A Gaussian of sigma = the panel's
    // short edge, so a press near one end still lifts the far end a little, which is what lets
    // a press on one member of a container light its neighbours with no extra term.
    float2 touchDelta = local - uTouch;
    float touchSigma = max(min(uSize.x, uSize.y), 1.0);
    float touchFall = exp(-dot(touchDelta, touchDelta) / (touchSigma * touchSigma))
        * clamp(uTouchAmt, 0.0, 1.0);
    float2 base = coord - touchDelta * (touchFall * 0.17);

    // Sampling outward pulls the surroundings inward and compresses them into the rim band.
    // That is the only direction consistent with sampling "an area larger than itself", and it
    // is why the host has to record uPad beyond the edge.
    float2 push = n * bend * lens;

    // Dispersion. Blue carries the higher index, so it lands furthest out. Kept faint: this is
    // a fringe, and past a few percent it stops reading as glass and starts reading as a broken
    // colour channel.
    float split = uAberration * bend;
    half3 sharp;
    float soften = uRimSoft * bend;
    if (soften > 0.05) {
        // Smear each channel along the normal, by an amount that grows with the bend.
        //
        // Where the rim compresses a hard boundary it lands as a single bright line, which reads
        // as a drawn stroke rather than as compressed image. Averaging three taps across the
        // direction the compression runs turns that line into the short gradient the reference
        // actually shows. After QWEA0/Liquid-Glass-Android, which calls it rimSoft.
        float2 sm = n * soften;
        float2 cr = base + push * (1.0 - split);
        float2 cg = base + push;
        float2 cb = base + push * (1.0 + split);
        sharp = half3(
            (backdropAt(cr).r + backdropAt(cr - sm).r + backdropAt(cr + sm).r) / 3.0,
            (backdropAt(cg).g + backdropAt(cg - sm).g + backdropAt(cg + sm).g) / 3.0,
            (backdropAt(cb).b + backdropAt(cb - sm).b + backdropAt(cb + sm).b) / 3.0);
    } else {
        sharp = half3(
            backdropAt(base + push * (1.0 - split)).r,
            backdropAt(base + push).g,
            backdropAt(base + push * (1.0 + split)).b);
    }

    // Scatter, keyed to distance from the edge. Squared, so the crisp compressed image stays
    // tight against the rim while the displacement itself still spans the whole band.
    half3 soft = blurredAt(base + push, uBlur * mat * e, tapRotation(coord));
    float rimSharp = (1.0 - e) * (1.0 - e);
    half3 bg = mix(soft, sharp, half(rimSharp));

    // The mirrored edge band: a broad, soft, upside-down echo of nearby content over roughly
    // the outer third of the surface. A thin band reads as a hard streak rather than as liquid.
    float mirrorWidth = max(14.0, min(halfSize.x, halfSize.y) * 0.7);
    float mirrorBand = 1.0 - clamp(depth / mirrorWidth, 0.0, 1.0);
    float mirrorMask = mirrorBand * mirrorBand * axisFade * smoothstep(0.0, 1.5, depth);
    half3 echo = backdropAt(base - n * (mirrorMask * lens * 3.0));
    bg = mix(bg, echo, half(clamp(mirrorMask * uMirror * mat, 0.0, 1.0)));

    float bgLuma = luma(bg);

    // Local contrast, free: the rim sample and the interior sample already bracket the
    // backdrop's high frequencies, so their luma difference stands in for text scrolling
    // underneath. Apple raises tint and dynamic range exactly then, to keep controls legible.
    float contrast = clamp(abs(luma(sharp) - luma(soft)) * 4.0, 0.0, 1.0);

    half3 tinted = toneMappedTint(half3(uTint.rgb), bgLuma, uAdaptive);
    tinted = mix(tinted, half3(1.0) - tinted, half(clamp(uFlip, 0.0, 1.0)));

    // Larger elements sit a little more opaque. Kept gentle: the material has to stay
    // transparent enough that the refracted detail survives, which is the whole point.
    // Reduce Transparency has no Android setting behind it, so uFrost is whatever in-app
    // control the app exposes. It raises opacity rather than disabling the material, which
    // keeps the shape and the lighting intact for anyone who still wants to see the edges.
    float strength = uTint.a * mix(0.9, 1.25, scale)
        + contrast * uLegibility * 0.25
        + clamp(uFrost, 0.0, 1.0) * 0.35;
    strength = clamp(strength * mat, 0.0, 0.95);
    // Two ways to apply a tint, and they differ over textured ground.
    //
    // A blend pulls every pixel the same distance toward one colour, which flattens the
    // backdrop's own light and shade. Multiplying by the tint instead scales what is there, so
    // the structure survives; a small additive term, strongest where the backdrop is darkest,
    // keeps the hue readable over black. That is how a coloured transparent medium behaves.
    // After QWEA0/Liquid-Glass-Android.
    //
    // The blend is the default because its strength is fitted to measurement: the value that
    // lifts pure black by 20 and passes 64% of white text is what pins this material.
    half3 blended = mix(bg, tinted, half(strength));
    half3 col;
    float absorb = clamp(uTintAbsorb, 0.0, 1.0);
    if (absorb > 0.001) {
        half3 absorbed = bg * mix(half3(1.0), tinted, half(0.85));
        half3 scattered = tinted * half(0.38 * (1.0 - clamp(bgLuma, 0.0, 1.0)));
        half3 medium = mix(bg, clamp(absorbed + scattered, half3(0.0), half3(1.0)), half(strength));
        col = mix(blended, medium, half(absorb));
    } else {
        col = blended;
    }

    float facing = dot(n, uLight);
    // Two rim geometries. At uBevelPeak 0 the bevel is a chamfer: brightest at the very edge,
    // fading inward, which is what a hairline-edged bar wants. Above 0 it is a bead: dark at
    // the edge, brightest a fraction of the bevel's width inside it, fading again toward the
    // interior — the profile the reference lens shows, rising over about three points from a
    // dark outermost pixel to its peak and falling over three more.
    float peakAt = clamp(uBevelPeak, 0.0, 0.9) * uBevel;
    float bevelBand = (peakAt < 0.5)
        ? smoothstep(uBevel, 0.0, depth)
        : smoothstep(0.0, peakAt, depth) * smoothstep(uBevel, peakAt, depth);

    // The faint dark line that reads as thickness, sitting just inside the bevel rather than on
    // the rim. Deliberately weak — see note 5 about outlines.
    float innerLine = max(smoothstep(uBevel * 2.2, 0.0, depth) - bevelBand, 0.0);
    col -= half3(half(innerLine * uInnerShadow * mix(0.9, 1.15, scale) * mat));

    // Schlick, using the same bevel slope the refraction used, so the rim brightens where it
    // turns away from the viewer. It stands in for an environment we do not have.
    float cosI = inversesqrt(1.0 + slope * slope);
    float f0 = (uIor - 1.0) / (uIor + 1.0);
    f0 = f0 * f0;
    float fresnel = f0 + (1.0 - f0) * pow(max(1.0 - cosI, 0.0), 5.0);

    // The bevel is lit where its normal faces the light, with a counter-lobe opposite: a bead
    // lit from one side only reads as a gradient rather than as a solid. How much reaches the
    // far side is the style's call — a free-standing lens is lit from above and wants a little,
    // while a bar measured against the reference is lit identically top and bottom and wants
    // all of it. The counter-lobe tightens as it weakens, so the historical 0.35 keeps the
    // narrower lobe it was tuned with.
    //
    // The edge line is the outermost two pixels, and it is *entirely* directional: where the
    // normal is perpendicular to the light there is no line at all. A floor here reads as a
    // stroke drawn round the shape, and the reference has none — its lens is a bright line top
    // and bottom and a one-pixel dark step at the sides. What defines the shape where nothing
    // is lit is uEdgeShadow, below. Its brightness is set apart from the
    // bevel lobe's: the reference bar is a hairline with almost no lobe behind it, and the
    // reference lens is a broad lobe whose peak sits inside an edge that stays dark.
    float counterLight = clamp(uCounterLight, 0.0, 1.0);
    float counterPow = uSpecularPow * mix(1.6, 1.0, smoothstep(0.35, 1.0, counterLight));
    float key = pow(max(facing, 0.0), uSpecularPow) * bevelBand;
    float counter = pow(max(-facing, 0.0), counterPow) * bevelBand * counterLight;
    float edge = smoothstep(2.0, 0.0, depth);
    float edgeLine = edge * 0.5 * (max(facing, 0.0) + counterLight * max(-facing, 0.0));

    float highlight = ((key + counter) * uSpecular
        + edgeLine * uSpecular * uEdgeLight
        + fresnel * uFresnel * bevelBand) * mat;
    col = applyHighlight(col, highlight, uHiChroma);

    // The dark contour that separates glass from its backdrop. Apple's 2026 revision pairs it
    // with a brighter specular; the 2025 material has none, so this is 0 on every preset here.
    // It occupies the outermost pixel only, inside of which the lit edge line begins, so
    // raising it darkens the boundary without eating the highlight.
    // Centred 1.5px inside the edge, not on it: coverage only reaches 1 at 0.75px, so a contour
    // drawn on the outermost pixel is multiplied by a partial alpha and composited against
    // whatever is outside — invisible when that is dark. The reference's step is one solid
    // pixel just inside the boundary.
    float rimDark = clamp(1.0 - abs(depth - 1.5) / 1.5, 0.0, 1.0);
    col -= half3(half(rimDark * uEdgeShadow * mat));

    // Illumination from within, under the fingertip. A sixth of a stop at the peak: measured
    // against a device, where anything near 0.2 reads as a camera flash rather than as glass
    // responding to a touch.
    col += half3(half(touchFall * 0.06 * mat));

    // Increase Contrast: Apple's guidance is that the material becomes predominantly black or
    // white with a contrasting border, rather than losing the effect entirely.
    float contrastBoost = clamp(uContrast, 0.0, 1.0);
    half3 solid = (bgLuma > 0.5) ? half3(0.02) : half3(0.97);
    col = mix(col, solid, half(contrastBoost * 0.88));
    col += half3(half(contrastBoost * edge * ((bgLuma > 0.5) ? 0.85 : -0.85)));

    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0) * half(coverage);
}
"""
