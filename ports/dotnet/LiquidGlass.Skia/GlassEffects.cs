// Apache-2.0. Uses the evaluated production shaders, embedded at build time.
using System.Reflection;
using System.Text.RegularExpressions;
using SkiaSharp;

namespace LiquidGlass.Skia;

/// <summary>One compiled production SkSL pass. Call on the owning rendering thread.</summary>
public sealed class GlassEffectPass : IDisposable
{
    private readonly SKRuntimeEffect effect;
    private readonly Dictionary<string, int> layout;
    private readonly HashSet<string> children;
    private bool disposed;

    public GlassEffectPass(string pass)
    {
        Source = ReadSource(pass);
        effect = SKRuntimeEffect.CreateShader(Source, out var errors)
            ?? throw new InvalidOperationException($"Cannot compile {pass}: {errors}");
        layout = Regex.Matches(Source, @"uniform\s+(?:float|half)([234]?)\s+(\w+)\s*;")
            .ToDictionary(m => m.Groups[2].Value, m => m.Groups[1].Length == 0 ? 1 : int.Parse(m.Groups[1].Value));
        children = new HashSet<string>(effect.Children);
    }

    public string Source { get; }

    public static string ReadSource(string pass)
    {
        if (pass is not ("material" or "content" or "endpoint")) throw new ArgumentOutOfRangeException(nameof(pass));
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream($"LiquidGlass.Shaders.{pass}.sksl")
            ?? throw new InvalidOperationException("Missing embedded production shader");
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }

    /// <summary>Fresh zero values for the exact production ABI; no caller-owned arrays retained.</summary>
    public Dictionary<string, float[]> CreateUniforms()
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        return layout.ToDictionary(e => e.Key, e => new float[e.Value]);
    }

    /// <summary>Requires every declared uniform and child. Caller owns the returned shader.</summary>
    public SKShader CreateShader(IReadOnlyDictionary<string, float[]> values,
        IReadOnlyDictionary<string, SKShader> inputs)
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        ArgumentNullException.ThrowIfNull(values);
        ArgumentNullException.ThrowIfNull(inputs);
        if (values.Count != layout.Count || values.Keys.Any(k => !layout.ContainsKey(k)))
            throw new ArgumentException("Supply exactly the declared uniform names", nameof(values));
        if (inputs.Count != children.Count || inputs.Keys.Any(k => !children.Contains(k)))
            throw new ArgumentException("Supply exactly the declared shader children", nameof(inputs));
        using var uniforms = new SKRuntimeEffectUniforms(effect);
        using var bound = new SKRuntimeEffectChildren(effect);
        foreach (var (name, count) in layout)
        {
            if (!values.TryGetValue(name, out var value) || value is null || value.Length != count || value.Any(v => !float.IsFinite(v)))
                throw new ArgumentException($"{name} needs {count} finite values", nameof(values));
            // SkiaSharp distinguishes vectors from uniform arrays in its typed ABI.
            switch (count)
            {
                case 1: uniforms[name] = value[0]; break;
                case 2: uniforms[name] = new SKPoint(value[0], value[1]); break;
                case 3: uniforms[name] = new SKPoint3(value[0], value[1], value[2]); break;
                case 4: uniforms[name] = new SKColorF(value[0], value[1], value[2], value[3]); break;
            }
        }
        foreach (var name in children)
        {
            if (!inputs.TryGetValue(name, out var child) || child is null || child.Handle == IntPtr.Zero)
                throw new ArgumentException($"Missing or disposed child: {name}", nameof(inputs));
            bound[name] = child;
        }
        return effect.ToShader(uniforms, bound) ?? throw new InvalidOperationException("Cannot create glass shader");
    }

    public void Dispose() { if (!disposed) { disposed = true; effect.Dispose(); } }
}
