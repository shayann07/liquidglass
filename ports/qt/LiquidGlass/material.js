.pragma library
// Apache-2.0. The in-app material's uniforms: a line-for-line port of the Flutter port's
// lib/src/material.dart, itself a port of ports/skia's clearMaterialUniforms (renderer.mjs) and
// inAppMaterialUniforms (material.mjs), which carry Compose's GlassStyle.inApp. Lengths are device
// pixels; the density converts the dp constants. Plain ES5 so QML's engine and Node both run it
// (test/material.test.mjs checks it against ports/flutter/test/material_fixture.json).

var LIGHT_OPACITY = [[0, .435], [31, .514], [50, .565], [62, .600], [100, .733]];
var DARK_OPACITY = [[0, .416], [29, .439], [50, .451], [62, .510], [86, .655], [100, .737]];

function interpolate(table, t) {
    var i = 0;
    while (i < table.length - 2 && table[i + 1][0] <= t) i++;
    var x = table[i][0], a = table[i][1], y = table[i + 1][0], b = table[i + 1][1];
    return a + (b - a) * (t - x) / (y - x);
}

function clamp01(v) { return Math.min(1, Math.max(0, v)); }

// GlassStyle.towardLab: the lab material at a Tint Amount. Measured at Tint 50 in the Safari lab (#199, #200) and at
// Tint 0 and 100 on the Photos chrome (#208); piecewise-linear between those three nodes (authored). The constants are
// GlassMaterial.lab* (Tint 50) and the node values below (GlassMaterial.measured*) in the Compose library.
var LAB_WIDE_KERNEL = 15.5;
var LAB_DETAIL_REACH = 1 / 3;

// GlassMaterial.measuredNodes + interpolate: linear between the values at Tint 0, 50 and 100.
function nodes3(atZero, atDefault, atHundred, amount) {
    var t = Math.min(100, Math.max(0, amount));
    return t <= 50 ? atZero + (atDefault - atZero) * (t / 50) : atDefault + (atHundred - atDefault) * ((t - 50) / 50);
}
function labOpacity(dark) { return dark ? .3985 : .498; }
function labLift(dark) { return dark ? 31.72 / 255 : 5.5 / 255; }
function labDetailShare(dark) { return dark ? .277 : .273; }
function labDetailLift(dark) { return dark ? -.22 : .22; }
function labToneCurve(dark) { return dark ? 101.01 / 255 : 0; }
// GlassMaterial.measuredDetailShare: measured at 0 and 100 (dark 0.663 / 0, light 0.664 / 0), the lab value at 50.
function measuredDetailShare(dark, t) { return nodes3(dark ? .663 : .664, labDetailShare(dark), 0, t); }
// GlassMaterial.measuredDetailLift: |c| 0.336 (light 0.334) at 0, 0.22 at 50, 0 at 100; negative on dark glass.
function measuredDetailLift(dark, t) {
    var m = nodes3(dark ? .336 : .334, Math.abs(labDetailLift(dark)), 0, t);
    return dark ? -m : m;
}
// GlassMaterial.measuredToneCurve: dark bow 101.6 / 255 at 0, 101.01 / 255 at 50, 49.8 / 255 at 100; light none.
function measuredToneCurve(dark, t) { return dark ? nodes3(101.6 / 255, labToneCurve(true), 49.8 / 255, t) : 0; }
// GlassMaterial.measuredOpacity: dark 0.401, 0.3985, 0.703 and light 0.382, 0.498, 0.682 at 0, 50 and 100.
function measuredOpacity(dark, t) { return dark ? nodes3(.401, labOpacity(true), .703, t) : nodes3(.382, labOpacity(false), .682, t); }
// GlassMaterial.labChromaGain / labChromaSlope (#201): the colour transfer's g0 and g1 per channel (R, G, B).
function labChromaGain(dark) { return dark ? [1.317, 1.256, 1.314] : [.988, .952, .979]; }
function labChromaSlope(dark) { return dark ? [-.427, -.351, -.429] : [-.076, .028, -.045]; }

// GlassMaterial.chromaOf: [g0 R, G, B, g1 R, G, B, weight] from GlassStyle.detailLift. The sign picks the appearance
// and |c| / |labDetailLift| is the weight; zero lift is seven zeros (the per-channel tone alone).
function chromaOf(detailLift) {
    var dark = detailLift < 0;
    var weight = clamp01(Math.abs(detailLift) / Math.abs(labDetailLift(dark)));
    if (weight <= 0) return [0, 0, 0, 0, 0, 0, 0];
    return labChromaGain(dark).concat(labChromaSlope(dark), [weight]);
}

// Compose's Color lerp runs in Oklab and stores 8 bits per channel again; the Oklab intermediate keeps alpha in
// 10 bits, truncated, which is the step the golden values show.
function srgbToLinear(c) { return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
function linearToSrgb(c) { return c <= .0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - .055; }
function q8(x) { return Math.floor(clamp01(x) * 255 + .5) / 255; }
function cbrt(x) { return Math.pow(x, 1 / 3); }

function toOklab(c) {
    var r = srgbToLinear(c[0]), g = srgbToLinear(c[1]), b = srgbToLinear(c[2]);
    var l = cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b);
    var m = cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b);
    var s = cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
    return [.2104542553 * l + .793617785 * m - .0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + .4505937099 * s,
            .0259040371 * l + .7827717662 * m - .808675766 * s];
}

function fromOklab(c) {
    var l = Math.pow(c[0] + .3963377774 * c[1] + .2158037573 * c[2], 3);
    var m = Math.pow(c[0] - .1055613458 * c[1] - .0638541728 * c[2], 3);
    var s = Math.pow(c[0] - .0894841775 * c[1] - 1.291485548 * c[2], 3);
    return [4.0767416621 * l - 3.3077115913 * m + .2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
            -.0041960863 * l - .7034186147 * m + 1.707614701 * s].map(function (v) { return linearToSrgb(clamp01(v)); });
}

function lerpColor(a, b, w) {
    var p = toOklab(a), q = toOklab(b);
    var rgb = fromOklab([p[0] + (q[0] - p[0]) * w, p[1] + (q[1] - p[1]) * w, p[2] + (q[2] - p[2]) * w]);
    var alpha = Math.floor(clamp01(a[3] + (b[3] - a[3]) * w) * 1023) / 1023;
    return [q8(rgb[0]), q8(rgb[1]), q8(rgb[2]), q8(alpha)];
}

/**
 * Every material uniform the shader reads, {name: [numbers]}, for a surface of `width` × `height`
 * device pixels (without the padding) at `density` device pixels per logical pixel.
 * m: {radius, dark, tintAmount, light: [x, y], tint: [r, g, b, a] or null, materialize,
 *     reducedTransparency, increasedContrast}; radius is in logical pixels.
 */
function uniforms(m, width, height, density) {
    var tintAmount = m.tintAmount === undefined ? 50 : m.tintAmount;
    if (tintAmount < 0 || tintAmount > 100) throw new RangeError("tintAmount must be 0 to 100");
    var v = {};
    // clearMaterialUniforms (magnification 1).
    v.uSize = [width, height];
    v.uPad = [2];
    // The backdrop texture is sampled clamped at its edges, so the backdrop region is unbounded here.
    v.uBackdrop = [-1e5, -1e5, 1e5, 1e5];
    v.uRefractBand = [24];
    v.uRefractDepth = [12];
    v.uIor = [1.5];
    v.uBevelPower = [2];
    v.uCornerPower = [2];
    v.uFieldRange = [1];
    v.uFieldScale = [1];
    v.uBevel = [2];
    v.uSpecularPow = [4];
    v.uCounterLight = [1];
    v.uEdgeLight = [1];
    v.uTint = [1, 1, 1, 0];
    v.uFineShare = [1];
    v.uWideScale = [.25];
    v.uTouch = [width / 2, height / 2];
    v.uPoseA = [1, 0, 0, 1];
    v.uPoseAInv = [1, 0, 0, 1];
    v.uPoseD = [1, 1, 1, 0];
    v.uPoseE = [0, 3, 0, 0];

    // inAppMaterialUniforms.
    var t = tintAmount / 100;
    var limit = Math.min(width, height) / 2;
    var r = Math.min((m.radius === undefined ? 20 : m.radius) * density, limit);
    var grey = m.dark ? 0 : 241 / 255;
    var opacity = Math.round(interpolate(m.dark ? DARK_OPACITY : LIGHT_OPACITY, tintAmount) * 255) / 255;
    var light = m.light || [0, -1];
    v.uRadii = [r, r, r, r];
    v.uRefractBand = [.6 * r];
    v.uRefractDepth = [14 * density];
    v.uProfile = [1];
    v.uFormation = [0];
    v.uHeldLens = [0];
    v.uHeldMagnification = [0];
    v.uHeldGlow = [1];
    v.uAberration = [.07];
    v.uWideKernel = [10 * density];
    v.uFineShare = [.95 * Math.pow(1 - t, 1.5)];
    v.uTint = [grey, grey, grey, opacity];
    v.uLift = [m.dark ? 35 / 255 : 0];
    v.uBevel = [1.33 * density];
    v.uBevelPeak = [.25];
    v.uSpecular = [.25];
    v.uSpecularPow = [6];
    v.uCounterLight = [1];
    v.uEdgeLight = [0];
    v.uLight = [light[0], light[1]];
    v.uLegibility = [.6];
    v.uScale = [clamp01((Math.min(width, height) / density - 56) / 264)];
    v.uMaterialize = [clamp01(m.materialize === undefined ? 1 : m.materialize)];
    v.uFrost = [clamp01(m.reducedTransparency || 0)];
    v.uContrast = [clamp01(m.increasedContrast || 0)];
    // The lab fit (GlassStyle.towardLab). The detail reach is the lab's at every tint, which selects the four-tap
    // detail kernel (uDetailCross: 0 < blurRadius <= 1/3 dp, decided in dp).
    // Everything the lab material sets is replaced outright, not blended with the earlier fit (towardLab, #208).
    var labGrey = m.dark ? 0 : 1;
    v.uBlur = [LAB_DETAIL_REACH * density];
    v.uDetailCross = [1];
    v.uTint = [labGrey, labGrey, labGrey, q8(measuredOpacity(m.dark, tintAmount))];
    v.uLift = [labLift(m.dark)];
    v.uFineShare = [measuredDetailShare(m.dark, tintAmount)];
    v.uWideKernel = [LAB_WIDE_KERNEL * density];
    v.uDetailLift = [measuredDetailLift(m.dark, tintAmount)];
    v.uToneCurve = [measuredToneCurve(m.dark, tintAmount)];
    v.uLegibility = [0];
    v.uSoftDetail = [1];
    if (m.tint) {
        var q = function (c) { return Math.round(c * 255) / 255; };
        v.uTint = [q(m.tint[0]), q(m.tint[1]), q(m.tint[2]), q(m.tint[3])];
    }
    // The colour transfer (#201). All three are written on every call, zeros included: the QML skips absent keys, so
    // a missing key would keep the previous material's value (as it just did for the lift and the tone curve).
    var chroma = chromaOf(v.uDetailLift ? v.uDetailLift[0] : 0);
    v.uChromaGain = chroma.slice(0, 3);
    v.uChromaSlope = chroma.slice(3, 6);
    v.uChromaWeight = [chroma[6]];
    // Qt reads the wide tone from the backdrop directly (see tool/generate_shader.mjs): switch it on.
    v.uWideStrip = [1];
    return v;
}

if (typeof module !== "undefined") module.exports = { uniforms: uniforms };
