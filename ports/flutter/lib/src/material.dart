// Apache-2.0. The in-app material's uniforms: a line-for-line port of ports/skia's
// clearMaterialUniforms (renderer.mjs) and inAppMaterialUniforms (material.mjs), which carry
// Compose's GlassStyle.inApp. Lengths are device pixels; the density converts the dp constants.
part of '../liquiglass.dart';

const List<(double, double)> _lightOpacity = [(0, .435), (31, .514), (50, .565), (62, .600), (100, .733)];
const List<(double, double)> _darkOpacity = [(0, .416), (29, .439), (50, .451), (62, .510), (86, .655), (100, .737)];

double _interpolate(List<(double, double)> table, double t) {
  var i = 0;
  while (i < table.length - 2 && table[i + 1].$1 <= t) {
    i++;
  }
  final (x, a) = table[i];
  final (y, b) = table[i + 1];
  return a + (b - a) * (t - x) / (y - x);
}

// GlassStyle.towardLab: the lab material at a Tint Amount. Measured at Tint 50 in the Safari lab (#199, #200) and at
// Tint 0 and 100 on the Photos chrome (#208); piecewise-linear between those three nodes (authored). The constants are
// GlassMaterial.lab* (Tint 50) and the node values below (GlassMaterial.measured*) in the Compose library.
const double _labWideKernel = 15.5;
const double _labDetailReach = 1 / 3;

// GlassMaterial.measuredNodes + interpolate: linear between the values at Tint 0, 50 and 100.
double _nodes3(double atZero, double atDefault, double atHundred, double amount) {
  final t = amount.clamp(0.0, 100.0);
  return t <= 50 ? atZero + (atDefault - atZero) * (t / 50) : atDefault + (atHundred - atDefault) * ((t - 50) / 50);
}
double _labOpacity(bool dark) => dark ? .3985 : .498;
double _labLift(bool dark) => dark ? 31.72 / 255 : 5.5 / 255;
double _labDetailShare(bool dark) => dark ? .277 : .273;
double _labDetailLift(bool dark) => dark ? -.22 : .22;
double _labToneCurve(bool dark) => dark ? 101.01 / 255 : 0;
// GlassMaterial.measuredDetailShare: measured at 0 and 100 (dark 0.663 / 0, light 0.664 / 0), the lab value at 50.
double _measuredDetailShare(bool dark, double t) => _nodes3(dark ? .663 : .664, _labDetailShare(dark), 0, t);
// GlassMaterial.measuredDetailLift: |c| 0.336 (light 0.334) at 0, 0.22 at 50, 0 at 100; negative on dark glass.
double _measuredDetailLift(bool dark, double t) {
  final m = _nodes3(dark ? .336 : .334, _labDetailLift(dark).abs(), 0, t);
  return dark ? -m : m;
}
// GlassMaterial.measuredToneCurve: dark bow 101.6 / 255 at 0, 101.01 / 255 at 50, 49.8 / 255 at 100; light none.
double _measuredToneCurve(bool dark, double t) => dark ? _nodes3(101.6 / 255, _labToneCurve(true), 49.8 / 255, t) : 0;
// GlassMaterial.measuredOpacity: dark 0.401, 0.3985, 0.703 and light 0.382, 0.498, 0.682 at 0, 50 and 100.
double _measuredOpacity(bool dark, double t) =>
    dark ? _nodes3(.401, _labOpacity(true), .703, t) : _nodes3(.382, _labOpacity(false), .682, t);
// GlassMaterial.labChromaGain / labChromaSlope (#201): the colour transfer's g0 and g1 per channel (R, G, B).
List<double> _labChromaGain(bool dark) => dark ? [1.317, 1.256, 1.314] : [.988, .952, .979];
List<double> _labChromaSlope(bool dark) => dark ? [-.427, -.351, -.429] : [-.076, .028, -.045];

/// GlassMaterial.chromaOf: [g0 R, G, B, g1 R, G, B, weight] from GlassStyle.detailLift. The sign picks the appearance
/// and |c| / |labDetailLift| is the weight; zero lift is seven zeros (the per-channel tone alone).
List<double> _chromaOf(double detailLift) {
  final dark = detailLift < 0;
  final weight = (detailLift.abs() / _labDetailLift(dark).abs()).clamp(0.0, 1.0);
  if (weight <= 0) return List<double>.filled(7, 0);
  return [..._labChromaGain(dark), ..._labChromaSlope(dark), weight];
}

// Compose stores 8 bits per channel, the step the golden values show.
double _clamp01(double x) => x.clamp(0.0, 1.0);
double _q8(double x) => (_clamp01(x) * 255 + .5).floorToDouble() / 255;

/// Every material uniform for a surface of [size] device pixels at [density] device pixels per
/// logical pixel, keyed by its name in liquid_glass.frag.
Map<String, List<double>> glassMaterialUniforms(GlassMaterial m, Size size, double density) {
  if (m.tintAmount < 0 || m.tintAmount > 100) throw RangeError.range(m.tintAmount, 0, 100, 'tintAmount');
  const pad = 2.0;
  final width = size.width, height = size.height;
  final values = <String, List<double>>{
    for (final (name, length) in _materialUniforms) name: List<double>.filled(length, 0),
  };
  void set(String name, List<double> v) {
    if (!values.containsKey(name)) throw StateError('liquidglass: no uniform $name in the shader');
    values[name] = v;
  }

  // clearMaterialUniforms (magnification 1).
  set('uSize', [width, height]);
  set('uPad', [pad]);
  // The backdrop texture is sampled clamped at its edges, so the backdrop region is unbounded here.
  set('uBackdrop', [-1e5, -1e5, 1e5, 1e5]);
  set('uRefractBand', [24]);
  set('uRefractDepth', [12]);
  set('uIor', [1.5]);
  set('uBevelPower', [2]);
  set('uCornerPower', [2]);
  set('uFieldRange', [1]);
  set('uFieldScale', [1]);
  set('uBevel', [2]);
  set('uSpecularPow', [4]);
  set('uCounterLight', [1]);
  set('uEdgeLight', [1]);
  set('uTint', [1, 1, 1, 0]);
  set('uFineShare', [1]);
  set('uWideScale', [.25]);
  set('uTouch', [width / 2, height / 2]);
  set('uPoseA', [1, 0, 0, 1]);
  set('uPoseAInv', [1, 0, 0, 1]);
  set('uPoseD', [1, 1, 1, 0]);
  set('uPoseE', [0, 3, 0, 0]);

  // inAppMaterialUniforms.
  final t = m.tintAmount / 100;
  final limit = math.min(width, height) / 2;
  final r = math.min(m.radius * density, limit);
  final grey = m.dark ? 0.0 : 241 / 255;
  final opacity = (_interpolate(m.dark ? _darkOpacity : _lightOpacity, m.tintAmount) * 255).roundToDouble() / 255;
  set('uRadii', [r, r, r, r]);
  set('uRefractBand', [.6 * r]);
  set('uRefractDepth', [14 * density]);
  set('uProfile', [1]);
  set('uFormation', [0]);
  set('uHeldLens', [0]);
  set('uHeldMagnification', [0]);
  set('uHeldGlow', [1]);
  set('uAberration', [.07]);
  set('uWideKernel', [10 * density]);
  set('uFineShare', [.95 * math.pow(1 - t, 1.5).toDouble()]);
  set('uTint', [grey, grey, grey, opacity]);
  set('uLift', [m.dark ? 35 / 255 : 0]);
  set('uBevel', [1.33 * density]);
  set('uBevelPeak', [.25]);
  set('uSpecular', [.25]);
  set('uSpecularPow', [6]);
  set('uCounterLight', [1]);
  set('uEdgeLight', [0]);
  set('uLight', [m.light.dx, m.light.dy]);
  set('uLegibility', [.6]);
  set('uScale', [((math.min(width, height) / density - 56) / 264).clamp(0.0, 1.0)]);
  set('uMaterialize', [m.materialize.clamp(0.0, 1.0)]);
  set('uFrost', [m.reducedTransparency.clamp(0.0, 1.0)]);
  set('uContrast', [m.increasedContrast.clamp(0.0, 1.0)]);
  // The lab fit (GlassStyle.towardLab). The detail reach is the lab's at every tint, which selects the four-tap
  // detail kernel (uDetailCross: 0 < blurRadius <= 1/3 dp, decided in dp).
  // Everything the lab material sets is replaced outright, not blended with the earlier fit (towardLab, #208).
  final labGrey = m.dark ? 0.0 : 1.0;
  set('uBlur', [_labDetailReach * density]);
  set('uDetailCross', [1]);
  set('uTint', [labGrey, labGrey, labGrey, _q8(_measuredOpacity(m.dark, m.tintAmount))]);
  set('uLift', [_labLift(m.dark)]);
  set('uFineShare', [_measuredDetailShare(m.dark, m.tintAmount)]);
  set('uWideKernel', [_labWideKernel * density]);
  set('uDetailLift', [_measuredDetailLift(m.dark, m.tintAmount)]);
  set('uToneCurve', [_measuredToneCurve(m.dark, m.tintAmount)]);
  set('uLegibility', [0]);
  set('uSoftDetail', [1]);
  final chroma = _chromaOf(values['uDetailLift']?[0] ?? 0);
  set('uChromaGain', chroma.sublist(0, 3));
  set('uChromaSlope', chroma.sublist(3, 6));
  set('uChromaWeight', [chroma[6]]);
  final tint = m.tint;
  if (tint != null) {
    double q(double c) => (c * 255).roundToDouble() / 255;
    set('uTint', [q(tint.r), q(tint.g), q(tint.b), q(tint.a)]);
  }
  // Flutter reads the wide tone from the backdrop directly (see generate_shader.mjs): switch it on.
  set('uWideStrip', [1]);
  return values;
}
