// Apache-2.0. liquiglass: Liquid Glass for Flutter, the liquidglass production material over the live backdrop.
//
// `GlassSurface` refracts whatever Flutter has drawn behind it, every frame, through the shader the
// Compose library runs (generated from it: tool/generate_shader.mjs). It needs Impeller, Flutter's
// renderer on Android and iOS; elsewhere it draws a frosted stand-in.
library;

import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

part 'src/uniforms.g.dart';
part 'src/material.dart';

/// A rounded piece of Liquid Glass with [child] on top, sized like a [Container] around it.
///
/// It refracts what is painted behind it, live: lists scrolling under it, images, video. Put it in a
/// [Stack] over the content, as iOS puts a tab bar or a toolbar.
class GlassSurface extends StatelessWidget {
  const GlassSurface({
    super.key,
    this.child,
    this.radius = 20,
    this.dark = false,
    this.tintAmount = 50,
    this.tint,
    this.light = const Offset(0, -1),
    this.materialize = 1,
    this.reducedTransparency = 0,
    this.increasedContrast = 0,
  });

  /// What sits on the glass: labels and icons, drawn sharp above it.
  final Widget? child;

  /// Corner radius, logical pixels.
  final double radius;

  /// The dark material.
  final bool dark;

  /// How much the material tints, 0 to 100.
  final double tintAmount;

  /// Replaces the tint colour and its opacity, as Compose's `Glass.tint(color)`.
  final Color? tint;

  /// Toward the light, y down positive. `Offset(0, -1)` is iOS 27's measured overhead light.
  final Offset light;

  /// Whether the material is present, 0 to 1: glass materializes rather than fading.
  final double materialize;

  /// Reduce Transparency, 0 to 1.
  final double reducedTransparency;

  /// Increase Contrast, 0 to 1.
  final double increasedContrast;

  @override
  Widget build(BuildContext context) {
    final material = GlassMaterial(
      radius: radius,
      dark: dark,
      tintAmount: tintAmount,
      tint: tint,
      light: light,
      materialize: materialize,
      reducedTransparency: reducedTransparency,
      increasedContrast: increasedContrast,
    );
    return _GlassSurface(
      material: material,
      devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
      child: child,
    );
  }
}

/// The material's settings, in logical pixels.
@immutable
class GlassMaterial {
  const GlassMaterial({
    this.radius = 20,
    this.dark = false,
    this.tintAmount = 50,
    this.tint,
    this.light = const Offset(0, -1),
    this.materialize = 1,
    this.reducedTransparency = 0,
    this.increasedContrast = 0,
  });
  final double radius;
  final bool dark;
  final double tintAmount;
  final Color? tint;
  final Offset light;
  final double materialize;
  final double reducedTransparency;
  final double increasedContrast;

  @override
  bool operator ==(Object other) =>
      other is GlassMaterial &&
      other.radius == radius &&
      other.dark == dark &&
      other.tintAmount == tintAmount &&
      other.tint == tint &&
      other.light == light &&
      other.materialize == materialize &&
      other.reducedTransparency == reducedTransparency &&
      other.increasedContrast == increasedContrast;

  @override
  int get hashCode => Object.hash(radius, dark, tintAmount, tint, light, materialize, reducedTransparency, increasedContrast);
}

/// Loads the compiled shader once. [GlassSurface] draws a stand-in until it is ready.
class LiquidGlass {
  LiquidGlass._();

  static ui.FragmentProgram? _program;
  static ui.Image? _placeholderField;
  static Future<void>? _loading;

  /// Whether the platform can run the material: Impeller with shader image filters.
  static bool get isSupported => ui.ImageFilter.isShaderFilterSupported;

  /// Starts loading the shader; [GlassSurface] calls it too, so calling it early only saves a frame.
  static Future<void> ensureInitialized() => _loading ??= _load();

  /// Why the shader did not load, if it did not; glass then keeps drawing the stand-in.
  static Object? get loadError => _loadError;
  static Object? _loadError;

  static Future<void> _load() async {
    try {
      // An app reads a package's assets under packages/<name>/; the package's own tests read them bare.
      _program = await ui.FragmentProgram.fromAsset('packages/liquiglass/shaders/liquid_glass.frag')
          .catchError((Object _) => ui.FragmentProgram.fromAsset('shaders/liquid_glass.frag'));
    } catch (error) {
      _loadError = error;
      return;
    }
    final recorder = ui.PictureRecorder();
    ui.Canvas(recorder).drawRect(const Rect.fromLTWH(0, 0, 1, 1), Paint()..color = const Color(0xFF000000));
    _placeholderField = recorder.endRecording().toImageSync(1, 1);
  }
}

class _GlassSurface extends SingleChildRenderObjectWidget {
  const _GlassSurface({required this.material, required this.devicePixelRatio, super.child});
  final GlassMaterial material;
  final double devicePixelRatio;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderGlassSurface(material, devicePixelRatio);

  @override
  void updateRenderObject(BuildContext context, _RenderGlassSurface renderObject) {
    renderObject
      ..material = material
      ..devicePixelRatio = devicePixelRatio;
  }
}

class _RenderGlassSurface extends RenderProxyBox {
  _RenderGlassSurface(this._material, this._devicePixelRatio) {
    if (LiquidGlass._program == null) {
      LiquidGlass.ensureInitialized().then((_) {
        if (attached) markNeedsPaint();
      });
    }
  }

  GlassMaterial _material;
  set material(GlassMaterial value) {
    if (value == _material) return;
    _material = value;
    markNeedsPaint();
  }

  double _devicePixelRatio;
  set devicePixelRatio(double value) {
    if (value == _devicePixelRatio) return;
    _devicePixelRatio = value;
    markNeedsPaint();
  }

  ui.FragmentShader? _shader;
  final LayerHandle<BackdropFilterLayer> _backdrop = LayerHandle<BackdropFilterLayer>();
  final LayerHandle<ClipRRectLayer> _clip = LayerHandle<ClipRRectLayer>();

  @override
  bool get alwaysNeedsCompositing => true;

  @override
  void dispose() {
    _backdrop.layer = null;
    _clip.layer = null;
    _shader?.dispose();
    super.dispose();
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final program = LiquidGlass._program;
    if (program == null || !LiquidGlass.isSupported || size.isEmpty) {
      _paintStandIn(context, offset);
      super.paint(context, offset);
      return;
    }
    final shader = _shader ??= program.fragmentShader();
    final ratio = _devicePixelRatio;
    // Two device pixels of padding hold the contour drawn just outside the outline.
    final pad = 2 / ratio;
    final global = localToGlobal(Offset.zero);
    _setUniforms(shader, ratio, (global - Offset(pad, pad)) * ratio);
    final padded = (offset & size).inflate(pad);
    _clip.layer = context.pushClipRRect(
      needsCompositing,
      Offset.zero,
      padded,
      RRect.fromRectAndRadius(padded, Radius.circular(_material.radius + pad)),
      (context, _) {
        _backdrop.layer ??= BackdropFilterLayer();
        _backdrop.layer!.filter = ui.ImageFilter.shader(shader);
        context.pushLayer(_backdrop.layer!, (context, offset) {}, Offset.zero);
      },
      oldLayer: _clip.layer,
    );
    super.paint(context, offset);
  }

  void _setUniforms(ui.FragmentShader shader, double ratio, Offset origin) {
    final values = glassMaterialUniforms(_material, Size(size.width * ratio, size.height * ratio), ratio);
    var index = 0;
    void put(List<double> components) {
      for (final c in components) {
        shader.setFloat(index++, c);
      }
    }

    put(const [0, 0]); // uTexSize: the engine's
    put([origin.dx, origin.dy]); // uOrigin
    put(const [1, 1]); // uFieldSize
    for (final (name, length) in _materialUniforms) {
      final v = values[name];
      if (v == null || v.length != length) {
        throw StateError('liquidglass: uniform $name needs $length values, got ${v?.length}');
      }
      put(v);
    }
    shader.setImageSampler(1, LiquidGlass._placeholderField!);
  }

  /// Before the shader loads, and where Impeller is unavailable: a frosted, rim-lit panel.
  void _paintStandIn(PaintingContext context, Offset offset) {
    final rrect = RRect.fromRectAndRadius(offset & size, Radius.circular(_material.radius));
    _clip.layer = context.pushClipRRect(needsCompositing, Offset.zero, offset & size, rrect, (context, _) {
      _backdrop.layer ??= BackdropFilterLayer();
      _backdrop.layer!.filter = ui.ImageFilter.blur(sigmaX: 12, sigmaY: 12);
      context.pushLayer(_backdrop.layer!, (context, offset) {}, Offset.zero);
      context.canvas.drawRRect(rrect,
          Paint()..color = _material.dark ? const Color(0x66202020) : const Color(0x88F2F2F2));
    }, oldLayer: _clip.layer);
    context.canvas.drawRRect(
        rrect.deflate(0.5),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1
          ..color = const Color(0x40FFFFFF));
  }
}
