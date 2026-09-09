package com.wexpa.liquidglass

/**
 * The glass shader, in the SkSL dialect shared by AGSL (Android 13+) and Skia (Desktop).
 *
 * Apple publishes no numbers for Liquid Glass — no blur radius, no index of refraction, no
 * falloff exponent, no specular formula. What it does publish is architecture and
 * direction-of-effect, and this shader implements that architecture; the constants are ours.
 *
 * The five architectural facts that shape it:
 *
 *  1. **The sample region is larger than the element.** Apple describes the material as
 *     sampling content from an area larger than itself, which is what makes it lens rather
 *     than merely blur. The host records a padded backdrop and passes [uPad]; the panel
 *     occupies the inset rect, so displacement near the rim reaches real content beyond the
 *     edge instead of clamping against it.
 *  2. **The rim is sharper than the middle.** This is the most recognisable thing about the
 *     material and the easiest to lose. The edge band shows a compressed, warped, *legible*
 *     image of what lies just outside the element; the interior is soft. A backdrop that is
 *     blurred before it reaches the shader cannot do this — the detail the rim is supposed to
 *     bend has already been destroyed — so the blur lives here instead, with its radius keyed
 *     to distance from the edge.
 *  3. **Parameters are keyed to element size.** Larger glass reads more opaque, with deeper
 *     shadow and stronger lensing; smaller glass reads clearer. [uScale] carries that factor.
 *  4. **Tint is a tone mapping, not an overlay.** One colour generates a range of tones
 *     indexed by the brightness of the backdrop, varying hue, saturation and brightness the
 *     way real coloured glass does.
 *  5. **The edge is lit, not outlined.** A pane of glass carries a thin bright line where its
 *     edge turns into the light. It does not carry a dark border, and a dark border is the
 *     fastest way to make a material like this read as a drawn rectangle rather than a solid.
 *
 * Geometry comes from a signed distance field. Distance to the edge drives refraction, so the
 * bend is a band hugging the rim that fades to nothing in the flat centre; the gradient of the
 * field is the surface normal, which gives refraction its direction and specular its angle and
 * stays correct through corners where a per-edge normal pops.
 */
internal const val GLASS_SHADER_SOURCE = """
uniform shader content;        // padded backdrop, unblurred

uniform float2  uSize;         // panel size, px (not counting padding)
uniform float   uPad;          // px of backdrop recorded beyond each edge
uniform float4  uBackdrop;     // l, t, r, b of the region that actually holds recorded pixels
uniform float3  uBase;         // opaque ground the backdrop is painted on
uniform float4  uRadii;        // corner radii: top-left, top-right, bottom-right, bottom-left
uniform float   uRefractBand;  // px from the rim over which refraction acts
uniform float   uRefractDepth; // peak displacement at the rim, px
uniform float   uAberration;   // fraction by which red and blue split from green at the rim
uniform float   uBlur;         // px radius of the interior blur, tapering to 0 at the rim
uniform float   uBevel;        // px width of the lit bevel
uniform float2  uLight;        // unit vector toward the key light
uniform float   uSpecular;     // rim highlight intensity, 0..1
uniform float   uSpecularPow;  // rim highlight tightness
uniform float4  uTint;         // rgb + strength
uniform float   uInnerShadow;  // strength of the inner thickness line
uniform float   uAdaptive;     // how much backdrop luminance modulates the tint
uniform float   uScale;        // element-size factor: 0 small and clear, 1 large and opaque
uniform float   uFlip;         // 1 when this element may invert light/dark, 0 when it may not

float sdRoundRect(float2 p, float2 halfSize, float4 r) {
    float2 rr = (p.x > 0.0) ? r.yz : r.xw;
    float radius = (p.y > 0.0) ? rr.y : rr.x;
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - radius;
}

// One backdrop sample, clamped to the region that actually holds recorded pixels.
//
// The recorded layer is filled with the ground before the backdrop is drawn into it, so it is
// opaque by construction and any alpha shortfall here is filtering error at its own boundary
// rather than real transparency. Dividing it out reconstructs the colour; compositing it
// toward the ground instead paints a halo of the ground colour around every panel.
half3 backdropAt(float2 p) {
    float2 q = clamp(p, uBackdrop.xy, uBackdrop.zw);
    half4 c = content.eval(q);
    return (c.a > half(0.004)) ? c.rgb / c.a : half3(uBase);
}

float2 rotate(float2 v, float2 rot) {
    return float2(v.x * rot.x - v.y * rot.y, v.x * rot.y + v.y * rot.x);
}

// A cheap per-pixel angle. Any hash with no visible structure will do; this one is the
// standard three-round float shuffle, chosen because it needs no texture and no state.
float2 tapRotation(float2 coord) {
    float3 q = fract(float3(coord.x, coord.y, coord.x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    float a = fract((q.x + q.y) * q.z) * 6.2831853;
    return float2(cos(a), sin(a));
}

// A nineteen-tap disc, for the soft interior of the material.
//
// Three rings of six at staggered angles, plus the centre. Nineteen taps is still too few to
// blur legible text on its own — the taps land as discrete ghosts of it — so the whole pattern
// is rotated by an angle that varies per pixel. Neighbouring pixels then sample different
// points and the ghosts break up into noise, which the eye integrates as blur. One sine and
// cosine cover all twelve offsets, so this costs a rotation rather than a bigger kernel.
//
// It is only ever called with a radius that falls to zero at the rim, so the expensive case
// never coincides with the case that needs detail.
half3 blurredAt(float2 p, float radius, float2 rot) {
    if (radius < 0.5) {
        return backdropAt(p);
    }
    float inner = radius * 0.55;
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
    float mid = radius * 0.8;
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

// Tone-map one tint colour across the backdrop's brightness, the way a pane of coloured glass
// does: it darkens and saturates over bright ground, lifts and desaturates over dark ground,
// and stays recognisably the same colour throughout.
half3 toneMappedTint(half3 tint, float bgLuma) {
    float g = clamp(bgLuma, 0.0, 1.0);
    float value = mix(1.10, 0.88, g);            // lift over dark, darken over bright
    float sat   = mix(0.88, 1.06, g);            // saturate as the ground brightens
    float mean  = dot(float3(tint), float3(1.0 / 3.0));
    half3 shifted = half3(half(mean)) + (tint - half3(half(mean))) * half(sat);
    return clamp(shifted * half(value), half3(0.0), half3(1.0));
}

half4 main(float2 coord) {
    // The panel is inset inside the padded backdrop.
    float2 local = coord - float2(uPad);
    float2 halfSize = uSize * 0.5;
    float2 p = local - halfSize;

    float d = sdRoundRect(p, halfSize, uRadii);

    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    float e = 1.0;
    float dx = sdRoundRect(p + float2(e, 0.0), halfSize, uRadii)
             - sdRoundRect(p - float2(e, 0.0), halfSize, uRadii);
    float dy = sdRoundRect(p + float2(0.0, e), halfSize, uRadii)
             - sdRoundRect(p - float2(0.0, e), halfSize, uRadii);
    float2 n = normalize(float2(dx, dy) + float2(1e-6));

    float depth = max(-d, 0.0);

    // Refraction. Normalised so 1 sits on the rim and 0 at the inner limit of the band, then
    // shaped so the bend accelerates toward the edge the way a bevel's curvature does — a
    // linear ramp reads as a gradient, not as glass. Larger elements lens harder.
    float t = clamp(1.0 - depth / max(uRefractBand, 0.001), 0.0, 1.0);
    float bend = t * t * t * mix(0.8, 1.3, uScale);

    // Sampling outward pulls the surroundings inward and compresses them into the bevel. This
    // only works because the backdrop was recorded with uPad to spare beyond the rim.
    float2 push = n * bend * uRefractDepth;

    // Dispersion. A real bevel does not bend every wavelength equally, so the channels land
    // apart and the rim carries a faint colour fringe. It is a small effect, and it is part of
    // the difference between a blurred panel and a piece of glass.
    float split = uAberration * bend;
    half3 sharp = half3(
        backdropAt(coord + push * (1.0 + split)).r,
        backdropAt(coord + push).g,
        backdropAt(coord + push * (1.0 - split)).b
    );

    // Blur where the glass is flat, sharp where it curves. See note 2 in the file comment:
    // this ordering is what lets the rim carry a legible compressed image of the surroundings
    // while the middle stays soft.
    half3 soft = blurredAt(coord + push, uBlur * (1.0 - t), tapRotation(coord));
    // Squared, so the crisp compressed image stays tight against the rim while the
    // displacement itself still spans the whole band.
    half3 bg = mix(soft, sharp, half(t * t));

    float bgLuma = luma(bg);

    // Whole-element light/dark inversion, gated by size: small chrome flips with its backdrop
    // to hold contrast, large surfaces adapt without flipping because the change would be
    // distracting across that much area.
    float inversion = uFlip * smoothstep(0.42, 0.62, bgLuma);

    half3 tinted = toneMappedTint(half3(uTint.rgb), bgLuma);
    tinted = mix(tinted, half3(1.0) - tinted, half(inversion));

    // Larger elements sit a little more opaque over their backdrop. Kept gentle: the material
    // has to stay transparent enough that the refracted detail behind it survives, which is
    // the whole point of lensing.
    float strength = clamp(uTint.a * mix(0.9, 1.25, uScale), 0.0, 1.0);
    half3 col = mix(bg, tinted, half(strength));

    float facing = dot(n, uLight);

    // The faint dark line that reads as thickness, sitting just inside the bevel rather than
    // on the rim. Deliberately weak — see note 5 about outlines.
    float bevelBand = smoothstep(uBevel, 0.0, depth);
    float innerLine = max(smoothstep(uBevel * 2.2, 0.0, depth) - bevelBand, 0.0);
    col -= half3(half(innerLine * uInnerShadow * mix(0.9, 1.15, uScale)));

    // Specular: the bevel is lit where its normal faces the light. Confined to the bevel band
    // and raised to a power, so it is a glint on two sides rather than a halo on all four.
    float spec = pow(max(facing, 0.0), uSpecularPow) * bevelBand * uSpecular;

    // A weaker counter-highlight opposite the key light. Without it a bead of glass looks lit
    // from one side only, which reads as a gradient rather than as a solid.
    float counter = pow(max(-facing, 0.0), uSpecularPow * 1.6) * bevelBand * uSpecular * 0.35;

    // The edge line itself: a couple of pixels of brightness right at the boundary. It carries
    // a small floor so the shape stays defined all the way round, but most of it is
    // directional, because a line of even brightness reads as a stroke and not as an edge.
    float edge = smoothstep(2.0, 0.0, depth);
    float edgeLine = edge * uSpecular * (0.07 + 0.5 * max(facing, 0.0));

    col += half3(half(spec + counter + edgeLine));

    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0) * half(coverage);
}
"""
