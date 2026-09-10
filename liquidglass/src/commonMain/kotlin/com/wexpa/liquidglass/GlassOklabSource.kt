package com.wexpa.liquidglass

/**
 * Shared SkSL for applying a highlight as lightness and chroma rather than as white.
 *
 * Adding white to a colour desaturates it, which is why a lit rim over a saturated backdrop reads
 * as a milky smear rather than as bright coloured glass. Raising lightness in a perceptually
 * uniform space keeps the hue, and raising chroma alongside it makes the highlight *more*
 * saturated than the ground beneath it, which is what a specular on a tinted transparent medium
 * actually does.
 *
 * Oklab rather than CIE LCH: no piecewise transfer, one cube root per channel each way, and it is
 * closer to hue-linear than CIE Lab anyway. The idea of compositing the highlight in a perceptual
 * space with a chroma term is iyinchao/liquid-glass-studio's, which uses LCH; see NOTICE.
 *
 * Included by both [GLASS_SHADER_SOURCE] and [GLASS_CONTAINER_SHADER_SOURCE], which are separate
 * programs and cannot share a function any other way.
 */
internal const val GLASS_OKLAB_SOURCE = """
// The sRGB transfer is approximated as gamma 2.0 rather than the exact piecewise 2.4. The error
// is a fraction of a code value, on a term that is itself a highlight, and it buys three squares
// and three square roots in place of six pow calls.
float3 toLinearApprox(float3 c) { return c * c; }
float3 fromLinearApprox(float3 c) { return sqrt(max(c, float3(0.0))); }

float3 linearToOklab(float3 c) {
    float l = 0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b;
    float m = 0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b;
    float s = 0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b;
    float l_ = pow(max(l, 0.0), 0.3333333333);
    float m_ = pow(max(m, 0.0), 0.3333333333);
    float s_ = pow(max(s, 0.0), 0.3333333333);
    return float3(
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_);
}

float3 oklabToLinear(float3 lab) {
    float l_ = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
    float m_ = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
    float s_ = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;
    return float3(
         4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

// Apply `amount` of highlight to `col`. `chroma` crossfades between adding white, which is what
// this did before and what the measured presets still do, and raising lightness and chroma in
// Oklab. Lightness is deliberately allowed past its own ceiling: the core of a lobe should still
// clip to white, which is what a specular does, and only the shoulder should stay coloured. That
// asymmetry is most of the difference between glass that glows and a white overlay.
half3 applyHighlight(half3 col, float amount, float chroma) {
    if (amount <= 0.0) {
        return col;
    }
    half3 additive = col + half3(half(amount));
    if (chroma <= 0.0) {
        return additive;
    }
    float3 lab = linearToOklab(toLinearApprox(float3(col)));
    lab.x = lab.x + amount * 0.90;
    lab.yz = lab.yz * (1.0 + amount * 1.20);
    half3 lifted = half3(fromLinearApprox(clamp(oklabToLinear(lab), float3(0.0), float3(1.0))));
    return mix(additive, lifted, half(clamp(chroma, 0.0, 1.0)));
}
"""
