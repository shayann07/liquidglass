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

// GlassStyle.towardLab: the lab fit (#199, #200) laid over the earlier fit by a weight that is 1 at the default
// Tint Amount and 0 at 0 and 100. The constants are GlassMaterial.lab* in the Compose library.
const double _labWideKernel = 15.5;
const double _labDetailReach = 1 / 3;

double _labWeight(double amount) => (1 - ((amount.clamp(0.0, 100.0) - 50).abs() / 50)).clamp(0.0, 1.0);
double _labOpacity(bool dark) => dark ? .3985 : .498;
double _labLift(bool dark) => dark ? 31.72 / 255 : 5.5 / 255;
double _labDetailShare(bool dark) => dark ? .277 : .273;
double _labDetailLift(bool dark) => dark ? -.22 : .22;
double _labToneCurve(bool dark) => dark ? 101.01 / 255 : 0;
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

// Compose's Color lerp runs in Oklab and stores 8 bits per channel again; the Oklab intermediate keeps alpha in
// 10 bits, truncated, which is the step the golden values show.
double _srgbToLinear(double c) => c <= .04045 ? c / 12.92 : math.pow((c + .055) / 1.055, 2.4).toDouble();
double _linearToSrgb(double c) => c <= .0031308 ? c * 12.92 : 1.055 * math.pow(c, 1 / 2.4).toDouble() - .055;
double _clamp01(double x) => x.clamp(0.0, 1.0);
double _q8(double x) => (_clamp01(x) * 255 + .5).floorToDouble() / 255;
double _cbrt(double x) => math.pow(x, 1 / 3).toDouble();

List<double> _toOklab(List<double> c) {
  final r = _srgbToLinear(c[0]), g = _srgbToLinear(c[1]), b = _srgbToLinear(c[2]);
  final l = _cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b);
  final m = _cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b);
  final s = _cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
  return [
    .2104542553 * l + .793617785 * m - .0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + .4505937099 * s,
    .0259040371 * l + .7827717662 * m - .808675766 * s,
  ];
}

List<double> _fromOklab(List<double> c) {
  final l = math.pow(c[0] + .3963377774 * c[1] + .2158037573 * c[2], 3).toDouble();
  final m = math.pow(c[0] - .1055613458 * c[1] - .0638541728 * c[2], 3).toDouble();
  final s = math.pow(c[0] - .0894841775 * c[1] - 1.291485548 * c[2], 3).toDouble();
  return [
    4.0767416621 * l - 3.3077115913 * m + .2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
    -.0041960863 * l - .7034186147 * m + 1.707614701 * s,
  ].map((v) => _linearToSrgb(_clamp01(v))).toList();
}

List<double> _lerpColor(List<double> a, List<double> b, double w) {
  final p = _toOklab(a), q = _toOklab(b);
  final rgb = _fromOklab([for (var i = 0; i < 3; i++) p[i] + (q[i] - p[i]) * w]);
  final alpha = (_clamp01(a[3] + (b[3] - a[3]) * w) * 1023).floorToDouble() / 1023;
  return [...rgb.map(_q8), _q8(alpha)];
}

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
  final w = _labWeight(m.tintAmount);
  set('uBlur', [_labDetailReach * density]);
  set('uDetailCross', [1]);
  if (w > 0) {
    double mix(double a, double b) => a + (b - a) * w;
    final labGrey = m.dark ? 0.0 : 1.0;
    set('uTint', _lerpColor([grey, grey, grey, opacity], [labGrey, labGrey, labGrey, _q8(_labOpacity(m.dark))], w));
    set('uLift', [mix(m.dark ? 35 / 255 : 0, _labLift(m.dark))]);
    set('uFineShare', [mix(.95 * math.pow(1 - t, 1.5).toDouble(), _labDetailShare(m.dark))]);
    set('uWideKernel', [mix(10 * density, _labWideKernel * density)]);
    set('uDetailLift', [_labDetailLift(m.dark) * w]);
    set('uToneCurve', [_labToneCurve(m.dark) * w]);
    set('uLegibility', [mix(.6, 0)]);
    set('uSoftDetail', [mix(0, 1)]);
  }
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
