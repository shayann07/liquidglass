// Apache-2.0. Drives a LiquidGlass material on a quad (any Renderer) or a UI Graphic: measures the glass in
// screen pixels every frame and sets the material's uniforms (GlassMaterial, GlassUniforms).
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace LiquidGlass
{
    /// <summary>
    /// A rounded piece of Liquid Glass. Put it on a GameObject that has a Renderer showing a quad, or a UI
    /// Graphic (an Image) on a Screen Space - Camera or World Space canvas, whose material uses
    /// "LiquidGlass/Surface" (Built-in) or "LiquidGlass/Surface (URP)". The glass refracts what the camera
    /// has drawn before it. The quad is the padded layer: the glass is drawn 2 device pixels inside it.
    /// </summary>
    [ExecuteAlways]
    [DisallowMultipleComponent]
    [AddComponentMenu("LiquidGlass/Glass Surface")]
    public sealed class GlassSurface : MonoBehaviour
    {
        [Tooltip("Corner radius, dp; clamped to half the shorter side.")]
        public float radius = 20;
        [Tooltip("The dark material.")]
        public bool dark;
        [Range(0, 100)] public float tintAmount = 50;
        [Tooltip("Toward the light, y up negative. (0, -1) is iOS 27's measured overhead light.")]
        public Vector2 light = new Vector2(-.35f, -.94f);
        [Tooltip("Replace the tint colour and opacity (alpha is the opacity).")]
        public bool useTint;
        public Color tint = new Color(1, 1, 1, .5f);
        [Range(0, 1)] public float materialize = 1;
        [Range(0, 1)] public float reducedTransparency;
        [Range(0, 1)] public float increasedContrast;
        [Tooltip("Device pixels per dp. 0 uses Screen.dpi / 160 (1 when the dpi is unknown).")]
        public float density;
        [Tooltip("The camera that renders the glass. Empty: the canvas's camera, else Camera.main.")]
        public Camera targetCamera;

        static readonly int LayerSize = Shader.PropertyToID("_LayerSize");
        static readonly Dictionary<string, int> Ids = new Dictionary<string, int>();
        readonly Vector3[] corners = new Vector3[4];
        MaterialPropertyBlock block;
        Material graphicMaterial;

        void LateUpdate() => Apply();

        void OnDisable()
        {
            if (graphicMaterial != null) DestroyImmediate(graphicMaterial);
            graphicMaterial = null;
        }

        /// <summary>Measures the glass and sets every uniform. Runs each frame; call it after moving the glass
        /// within a frame if it must be right in that frame.</summary>
        public void Apply()
        {
            if (!MeasureScreenPixels(out var width, out var height) || width < 8 || height < 8) return;
            var d = density > 0 ? density : (Screen.dpi > 0 ? Screen.dpi / 160f : 1f);
            var look = new GlassLook
            {
                Radius = radius, Dark = dark, TintAmount = Mathf.Clamp(tintAmount, 0, 100),
                LightX = light.x, LightY = light.y, HasTint = useTint,
                TintR = tint.r, TintG = tint.g, TintB = tint.b, TintA = tint.a,
                Materialize = materialize, ReducedTransparency = reducedTransparency, IncreasedContrast = increasedContrast,
            };
            var values = GlassMaterial.Uniforms(look, width - 4, height - 4, d);

            if (TryGetComponent<Graphic>(out var graphic))
            {
                if (graphicMaterial == null || graphic.material != graphicMaterial)
                {
                    if (graphic.material == null || graphic.material.shader == null || !graphic.material.shader.name.StartsWith("LiquidGlass/")) return;
                    graphicMaterial = new Material(graphic.material) { hideFlags = HideFlags.HideAndDontSave };
                    graphic.material = graphicMaterial;
                }
                Set(graphicMaterial, null, values, width, height);
                graphic.SetMaterialDirty();
            }
            else if (TryGetComponent<Renderer>(out var renderer))
            {
                block ??= new MaterialPropertyBlock();
                renderer.GetPropertyBlock(block);
                Set(null, block, values, width, height);
                renderer.SetPropertyBlock(block);
            }
        }

        static void Set(Material material, MaterialPropertyBlock block, Dictionary<string, double[]> values, float width, float height)
        {
            var layer = new Vector4(width, height, 0, 0);
            if (material != null) material.SetVector(LayerSize, layer); else block.SetVector(LayerSize, layer);
            foreach (var (name, size) in GlassUniforms.All)
            {
                if (!Ids.TryGetValue(name, out var id)) Ids[name] = id = Shader.PropertyToID(name);
                values.TryGetValue(name, out var v);
                float At(int i) => v != null && i < v.Length ? (float)v[i] : 0f;
                if (size == 1)
                {
                    if (material != null) material.SetFloat(id, At(0)); else block.SetFloat(id, At(0));
                }
                else
                {
                    var vector = new Vector4(At(0), At(1), At(2), At(3));
                    if (material != null) material.SetVector(id, vector); else block.SetVector(id, vector);
                }
            }
        }

        bool MeasureScreenPixels(out float width, out float height)
        {
            width = height = 0;
            Camera cam = targetCamera;
            if (transform is RectTransform rect)
            {
                rect.GetWorldCorners(corners);
                var canvas = GetComponentInParent<Canvas>();
                if (canvas != null && canvas.renderMode == RenderMode.ScreenSpaceOverlay)
                {
                    Debug.LogWarning("LiquidGlass: a Screen Space - Overlay canvas draws after every camera, so there is nothing to refract. Use Screen Space - Camera.", this);
                    enabled = false;
                    return false;
                }
                if (cam == null && canvas != null) cam = canvas.worldCamera;
            }
            else if (TryGetComponent<MeshFilter>(out var filter) && filter.sharedMesh != null)
            {
                // The quad's corners: the mesh's local bounds in its XY plane (Unity's Quad is 1 x 1).
                var b = filter.sharedMesh.bounds;
                corners[0] = transform.TransformPoint(new Vector3(b.min.x, b.min.y, 0));
                corners[1] = transform.TransformPoint(new Vector3(b.min.x, b.max.y, 0));
                corners[2] = transform.TransformPoint(new Vector3(b.max.x, b.max.y, 0));
                corners[3] = transform.TransformPoint(new Vector3(b.max.x, b.min.y, 0));
            }
            else return false;
            if (cam == null) cam = Camera.main;
            if (cam == null) return false;
            var p0 = cam.WorldToScreenPoint(corners[0]);
            var p1 = cam.WorldToScreenPoint(corners[1]);
            var p3 = cam.WorldToScreenPoint(corners[3]);
            width = Vector2.Distance(p0, p3);
            height = Vector2.Distance(p0, p1);
            return true;
        }
    }
}
