// Apache-2.0. The Unity package's checks, run without Unity (Windows):
//
//   dotnet run --project ports/unity/Checks -- OUT_DIR
//
// 1. GlassMaterial.cs equals the CanvasKit port's in-app material (ports/flutter/test/material_fixture.json).
// 2. The generated LiquidGlassMaterial.hlsl and LiquidGlassScreen.hlsl compile with FXC as ps_5_0, the
//    profile Unity uses for Direct3D 11.
// 3. That shader renders on D3D11's WARP software device: a light card and a dark bar over a striped
//    backdrop, through the same screen mapping (LiquidGlassBeginFragment) the Unity shaders use. The
//    pixels are checked, and unity-warp.png / unity-backdrop.png are saved to OUT_DIR.
//
// What this does not check: ShaderLab (the .shader wrappers), Unity's own compiler and its other
// targets (Vulkan, Metal, GLES), GrabPass and URP's opaque texture, and GlassSurface.cs.
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text.Json;
using LiquidGlass;
using Vortice.D3DCompiler;
using Vortice.Direct3D;
using Vortice.Direct3D11;
using Vortice.Direct3D11.Shader;
using Vortice.DXGI;

var outDir = args.Length > 0 ? args[0] : "unity-checks";
Directory.CreateDirectory(outDir);
var root = FindRoot();
var failures = new List<string>();
void Check(bool ok, string what) { Console.WriteLine((ok ? "ok   " : "FAIL ") + what); if (!ok) failures.Add(what); }

// 1. The material.
var fixture = JsonDocument.Parse(File.ReadAllText(Path.Combine(root, "ports/flutter/test/material_fixture.json"))).RootElement;
var known = GlassUniforms.All.ToDictionary(u => u.Name, u => u.Size);
var unityOnly = new HashSet<string> { "uBackdrop", "uWideStrip" };  // the live texture, unbounded; wide tone always on
var caseIndex = 0;
foreach (var entry in fixture.EnumerateArray())
{
    var c = entry.GetProperty("input");
    double N(string k, double fallback) => c.TryGetProperty(k, out var e) ? e.GetDouble() : fallback;
    var light = c.GetProperty("light");
    var look = GlassLook.Default;
    look.Radius = N("radius", 20); look.Dark = c.GetProperty("dark").GetBoolean(); look.TintAmount = N("tintAmount", 50);
    look.LightX = light[0].GetDouble(); look.LightY = light[1].GetDouble();
    look.Materialize = N("materialize", 1); look.ReducedTransparency = N("reducedTransparency", 0);
    look.IncreasedContrast = N("increasedContrast", 0);
    if (c.TryGetProperty("tint", out var tint))
    {
        look.HasTint = true; look.TintR = tint[0].GetDouble(); look.TintG = tint[1].GetDouble(); look.TintB = tint[2].GetDouble(); look.TintA = tint[3].GetDouble();
    }
    var d = N("density", 1);
    var ours = GlassMaterial.Uniforms(look, N("width", 0) * d, N("height", 0) * d, d);
    var worst = 0.0; var where = "";
    foreach (var u in entry.GetProperty("uniforms").EnumerateObject())
    {
        if (!known.ContainsKey(u.Name)) { Check(false, $"case {caseIndex}: {u.Name} is not a shader uniform"); continue; }
        if (unityOnly.Contains(u.Name)) continue;
        var theirs = u.Value.EnumerateArray().Select(x => x.GetDouble()).ToArray();
        var mine = ours.TryGetValue(u.Name, out var m) ? m : new double[theirs.Length];
        if (mine.Length != theirs.Length) { Check(false, $"case {caseIndex}: {u.Name} has {mine.Length} values, not {theirs.Length}"); continue; }
        for (var j = 0; j < mine.Length; j++)
        {
            var diff = Math.Abs(mine[j] - theirs[j]);
            if (diff > worst) { worst = diff; where = $"{u.Name}[{j}]"; }
        }
    }
    Check(worst <= 1e-6, $"case {caseIndex} matches the CanvasKit port's material (worst {worst:g3} at {(where == "" ? "-" : where)})");
    caseIndex++;
}

// 2. Compile with FXC (d3dcompiler_47), ps_5_0.
var shaders = Path.Combine(root, "ports/unity/com.shayxo.liquidglass/Runtime/Shaders");
var material = File.ReadAllText(Path.Combine(shaders, "LiquidGlassMaterial.hlsl"));
var screen = File.ReadAllText(Path.Combine(shaders, "LiquidGlassScreen.hlsl"));
var source = $$"""
    {{screen}}
    Texture2D _Backdrop : register(t0);
    SamplerState _Linear : register(s0);
    float2 _ScreenSize;   // the render target, px
    float4 sampleInput(float2 p) { return float4(_Backdrop.SampleLevel(_Linear, LiquidGlassScreenUV(p), 0).rgb, 1); }
    float4 sampleField(float2 p) { return float4(0, 0, 0, 0); }
    {{material}}
    struct V { float4 pos : SV_Position; float2 uv : TEXCOORD0; };
    V vs(uint id : SV_VertexID)
    {
        // Two triangles over the viewport, uv as Unity's quad has it (v up).
        float2 c = float2((id == 1 || id == 4 || id == 5) ? 1.0 : 0.0, (id == 2 || id == 3 || id == 5) ? 1.0 : 0.0);
        V o; o.pos = float4(c.x * 2 - 1, c.y * 2 - 1, 0, 1); o.uv = c; return o;
    }
    float4 ps(V i) : SV_Target
    {
        float2 p = LiquidGlassLayerPixel(i.uv);
        LiquidGlassBeginFragment(p, i.pos.xy / _ScreenSize);
        return glassMain(p);
    }
    """;
Blob Compile(string entry, string profile)
{
    var result = Compiler.Compile(source, entry, "liquidglass-unity-check.hlsl", profile, out var blob, out var errors);
    var log = errors?.AsString() ?? "";
    if (result.Failure || blob is null) throw new InvalidOperationException($"FXC {profile} failed:\n{log}");
    errors?.Dispose();
    return blob;
}
Blob psBlob, vsBlob;
try
{
    psBlob = Compile("ps", "ps_5_0");
    vsBlob = Compile("vs", "vs_5_0");
    Check(true, $"FXC compiles the generated HLSL as ps_5_0 ({psBlob.BufferSize} bytes)");
}
catch (Exception e)
{
    Check(false, e.Message);
    return Finish();
}

// 3. Render on WARP.
const int Width = 720, Height = 400;
D3D11.D3D11CreateDevice(null, DriverType.Warp, DeviceCreationFlags.None, new[] { FeatureLevel.Level_11_0 },
    out ID3D11Device? device, out ID3D11DeviceContext? context).CheckError();
using var _d = device!; using var _c = context!;

// The backdrop: diagonal colour bands and a black-and-white checker, so refraction shows.
var backdrop = new byte[Width * Height * 4];
for (var y = 0; y < Height; y++)
for (var x = 0; x < Width; x++)
{
    var i = (y * Width + x) * 4;
    var band = ((x + y) / 28) % 5;
    (byte r, byte g, byte b) col = band switch
    {
        0 => (227, 93, 106), 1 => (76, 141, 246), 2 => (60, 196, 124), 3 => (242, 179, 61), _ => (246, 243, 248),
    };
    if ((x / 20 + y / 20) % 2 == 0 && y > 170 && y < 260) col = ((x / 20) % 2 == 0) ? ((byte)20, (byte)20, (byte)20) : ((byte)250, (byte)250, (byte)250);
    backdrop[i] = col.r; backdrop[i + 1] = col.g; backdrop[i + 2] = col.b; backdrop[i + 3] = 255;
}
var texDesc = new Texture2DDescription(Format.R8G8B8A8_UNorm, Width, Height, 1, 1, BindFlags.ShaderResource | BindFlags.RenderTarget);
using var backdropTex = CreateTexture(device!, texDesc, backdrop);
using var backdropView = device!.CreateShaderResourceView(backdropTex);
using var target = device.CreateTexture2D(texDesc);
using var targetView = device.CreateRenderTargetView(target);
using var sampler = device.CreateSamplerState(new SamplerDescription(Filter.MinMagMipLinear, TextureAddressMode.Clamp));
using var vertexShader = device.CreateVertexShader(vsBlob);
using var pixelShader = device.CreatePixelShader(psBlob);
var blendDesc = new BlendDescription(Blend.One, Blend.InverseSourceAlpha);
using var blend = device.CreateBlendState(blendDesc);
using var rasterizer = device.CreateRasterizerState(RasterizerDescription.CullNone);

// The $Globals constant buffer, laid out by reflection.
using var reflection = Compiler.Reflect<ID3D11ShaderReflection>(psBlob.AsBytes());
var globals = reflection.GetConstantBufferByName("$Globals");
var cbSize = (int)globals.Description.Size;
var variables = new Dictionary<string, (int Offset, int Size)>();
for (var i = 0u; i < globals.Description.VariableCount; i++)
{
    var v = globals.GetVariableByIndex(i).Description;
    variables[v.Name] = ((int)v.StartOffset, (int)v.Size);
}
using var constants = device.CreateBuffer(new BufferDescription((uint)cbSize, BindFlags.ConstantBuffer));

void DrawGlass(float x, float y, float w, float h, GlassLook look, float density)
{
    var data = new byte[cbSize];
    void Put(string name, params double[] values)
    {
        if (!variables.TryGetValue(name, out var v)) return;  // unread uniforms are optimised away
        for (var k = 0; k < values.Length && k * 4 < v.Size; k++)
            BitConverter.TryWriteBytes(data.AsSpan(v.Offset + k * 4, 4), (float)values[k]);
    }
    foreach (var (name, value) in GlassMaterial.Uniforms(look, w - 4, h - 4, density)) Put(name, value);
    Put("_LayerSize", w, h);
    Put("_ScreenSize", Width, Height);
    context!.UpdateSubresource(data, constants);
    context.OMSetRenderTargets(targetView);
    context.OMSetBlendState(blend);
    context.RSSetState(rasterizer);
    context.RSSetViewport(new Vortice.Mathematics.Viewport(x, y, w, h));
    context.IASetPrimitiveTopology(PrimitiveTopology.TriangleList);
    context.VSSetShader(vertexShader);
    context.PSSetShader(pixelShader);
    context.PSSetConstantBuffer(0, constants);
    context.PSSetShaderResource(0, backdropView);
    context.PSSetSampler(0, sampler);
    context.Draw(6, 0);
}

context!.CopyResource(target, backdropTex);
var density = 2f;
var card = (X: 40f, Y: 40f, W: 640f, H: 150f);
var bar = (X: 60f, Y: 270f, W: 600f, H: 96f);
var lightLook = GlassLook.Default; lightLook.Radius = 28; lightLook.LightX = 0; lightLook.LightY = -1;
var darkLook = GlassLook.Default; darkLook.Radius = 24; darkLook.Dark = true;
DrawGlass(card.X, card.Y, card.W, card.H, lightLook, density);
DrawGlass(bar.X, bar.Y, bar.W, bar.H, darkLook, density);
var rendered = ReadBack(device, context, target);
WritePng(Path.Combine(outDir, "unity-backdrop.png"), backdrop, Width, Height);
WritePng(Path.Combine(outDir, "unity-warp.png"), rendered, Width, Height);

(double Mean, double Spread) Stats(byte[] px, (float X, float Y, float W, float H) r)
{
    var values = new List<double>();
    for (var y = (int)r.Y + 8; y < (int)(r.Y + r.H) - 8; y += 2)
    for (var x = (int)r.X + 8; x < (int)(r.X + r.W) - 8; x += 2)
    {
        var i = (y * Width + x) * 4;
        values.Add(.2126 * px[i] + .7152 * px[i + 1] + .0722 * px[i + 2]);
    }
    var mean = values.Average();
    return (mean, Math.Sqrt(values.Average(v => (v - mean) * (v - mean))));
}
double Difference((float X, float Y, float W, float H) r)
{
    double total = 0; var n = 0;
    for (var y = (int)r.Y + 8; y < (int)(r.Y + r.H) - 8; y += 2)
    for (var x = (int)r.X + 8; x < (int)(r.X + r.W) - 8; x += 2)
    {
        var i = (y * Width + x) * 4;
        total += (Math.Abs(rendered[i] - backdrop[i]) + Math.Abs(rendered[i + 1] - backdrop[i + 1]) + Math.Abs(rendered[i + 2] - backdrop[i + 2])) / 3.0;
        n++;
    }
    return total / n;
}
foreach (var (name, r) in new[] { ("card", card), ("bar", bar) })
{
    var diff = Difference(r);
    Check(diff > 3, $"{name} changes the pixels under it (mean difference {diff:F1} levels)");
    var (mean, spread) = Stats(rendered, r);
    Check(spread > 2, $"{name} is not a flat fill (grey {mean:F0}, spread {spread:F1})");
}
Check(Stats(rendered, bar).Mean < Stats(backdrop, bar).Mean - 10, "the dark bar is darker than the backdrop under it");
Check(Stats(rendered, card).Mean > 150, "the light card stays light");
var outside = (X: 4f, Y: 370f, W: 40f, H: 26f);
Check(Difference(outside) < 0.5, "pixels outside the glass are untouched");
Console.WriteLine($"saved unity-warp.png and unity-backdrop.png to {Path.GetFullPath(outDir)}");
return Finish();

int Finish()
{
    Console.WriteLine(failures.Count == 0 ? "All Unity checks passed." : $"{failures.Count} check(s) failed.");
    return failures.Count == 0 ? 0 : 1;
}

static string FindRoot()
{
    for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir != null; dir = dir.Parent)
        if (File.Exists(Path.Combine(dir.FullName, "gradle.properties")) && Directory.Exists(Path.Combine(dir.FullName, "ports")))
            return dir.FullName;
    throw new InvalidOperationException("Run from inside the repository");
}

static ID3D11Texture2D CreateTexture(ID3D11Device device, Texture2DDescription desc, byte[] pixels)
{
    var handle = GCHandle.Alloc(pixels, GCHandleType.Pinned);
    try { return device.CreateTexture2D(desc, new SubresourceData(handle.AddrOfPinnedObject(), desc.Width * 4)); }
    finally { handle.Free(); }
}

static byte[] ReadBack(ID3D11Device device, ID3D11DeviceContext context, ID3D11Texture2D texture)
{
    var desc = texture.Description;
    desc.BindFlags = BindFlags.None; desc.Usage = ResourceUsage.Staging; desc.CPUAccessFlags = CpuAccessFlags.Read;
    using var staging = device.CreateTexture2D(desc);
    context.CopyResource(staging, texture);
    var map = context.Map(staging, 0, MapMode.Read);
    var pixels = new byte[desc.Width * desc.Height * 4];
    for (var y = 0; y < desc.Height; y++)
        Marshal.Copy(map.DataPointer + (nint)(y * map.RowPitch), pixels, (int)(y * desc.Width * 4), (int)(desc.Width * 4));
    context.Unmap(staging, 0);
    return pixels;
}

static void WritePng(string path, byte[] rgba, int width, int height)
{
    using var raw = new MemoryStream();
    using (var z = new ZLibStream(raw, CompressionLevel.Optimal, leaveOpen: true))
        for (var y = 0; y < height; y++) { z.WriteByte(0); z.Write(rgba, y * width * 4, width * 4); }
    using var file = File.Create(path);
    file.Write(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 });
    void Chunk(string type, byte[] data)
    {
        var t = System.Text.Encoding.ASCII.GetBytes(type);
        file.Write(BigEndian((uint)data.Length)); file.Write(t); file.Write(data);
        file.Write(BigEndian(Crc(t.Concat(data).ToArray())));
    }
    var header = BigEndian((uint)width).Concat(BigEndian((uint)height)).Concat(new byte[] { 8, 6, 0, 0, 0 }).ToArray();
    Chunk("IHDR", header); Chunk("IDAT", raw.ToArray()); Chunk("IEND", Array.Empty<byte>());
}

static byte[] BigEndian(uint v) => new[] { (byte)(v >> 24), (byte)(v >> 16), (byte)(v >> 8), (byte)v };

static uint Crc(byte[] data)
{
    var c = 0xFFFFFFFFu;
    foreach (var b in data)
    {
        c ^= b;
        for (var k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
    }
    return ~c;
}
