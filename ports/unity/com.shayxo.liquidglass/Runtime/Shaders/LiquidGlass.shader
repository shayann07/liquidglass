// Apache-2.0. Liquid Glass for Unity's Built-in render pipeline: the production material over a GrabPass
// copy of what is already drawn. Use it on a quad or a UI Image (Screen Space - Camera or World Space
// canvas) with a GlassSurface component, which sets the material's uniforms.
Shader "LiquidGlass/Surface"
{
    Properties
    {
        [HideInInspector] _MainTex ("Texture (unused; UI Graphic needs it)", 2D) = "white" {}
    }
    SubShader
    {
        Tags { "Queue" = "Transparent" "RenderType" = "Transparent" "IgnoreProjector" = "True" "PreviewType" = "Plane" }

        GrabPass { "_LiquidGlassGrab" }

        Pass
        {
            ZWrite Off
            Cull Off
            Blend One OneMinusSrcAlpha   // the material is premultiplied

            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #pragma target 3.5
            #include "UnityCG.cginc"
            #include "LiquidGlassScreen.hlsl"

            sampler2D _LiquidGlassGrab;

            struct appdata { float4 vertex : POSITION; float2 uv : TEXCOORD0; };
            struct v2f { float4 pos : SV_POSITION; float2 uv : TEXCOORD0; float4 grab : TEXCOORD1; };

            v2f vert(appdata v)
            {
                v2f o;
                o.pos = UnityObjectToClipPos(v.vertex);
                o.uv = v.uv;
                o.grab = ComputeGrabScreenPos(o.pos);
                return o;
            }

            float4 sampleInput(float2 p) { return float4(tex2Dlod(_LiquidGlassGrab, float4(LiquidGlassScreenUV(p), 0, 0)).rgb, 1); }
            float4 sampleField(float2 p) { return float4(0, 0, 0, 0); }

            #include "LiquidGlassMaterial.hlsl"

            float4 frag(v2f i) : SV_Target
            {
                float2 p = LiquidGlassLayerPixel(i.uv);
                LiquidGlassBeginFragment(p, i.grab.xy / i.grab.w);
                return glassMain(p);
            }
            ENDCG
        }
    }
    FallBack Off
}
