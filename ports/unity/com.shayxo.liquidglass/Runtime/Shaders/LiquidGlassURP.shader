// Apache-2.0. Liquid Glass for the Universal Render Pipeline: the production material over URP's opaque
// texture. Turn on Opaque Texture in the URP asset. The glass refracts opaque geometry drawn before it;
// transparent objects and Screen Space - Overlay UI are not in that texture. GlassSurface sets the uniforms.
Shader "LiquidGlass/Surface (URP)"
{
    Properties
    {
        [HideInInspector] _MainTex ("Texture (unused; UI Graphic needs it)", 2D) = "white" {}
    }
    SubShader
    {
        Tags { "RenderPipeline" = "UniversalPipeline" "Queue" = "Transparent" "RenderType" = "Transparent" "IgnoreProjector" = "True" }

        Pass
        {
            Name "LiquidGlass"
            Tags { "LightMode" = "UniversalForward" }
            ZWrite Off
            Cull Off
            Blend One OneMinusSrcAlpha   // the material is premultiplied

            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #pragma target 3.5
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"
            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/DeclareOpaqueTexture.hlsl"
            #include "LiquidGlassScreen.hlsl"

            struct Attributes { float4 positionOS : POSITION; float2 uv : TEXCOORD0; };
            struct Varyings { float4 positionCS : SV_POSITION; float2 uv : TEXCOORD0; float4 screen : TEXCOORD1; };

            Varyings vert(Attributes v)
            {
                Varyings o;
                o.positionCS = TransformObjectToHClip(v.positionOS.xyz);
                o.uv = v.uv;
                o.screen = ComputeScreenPos(o.positionCS);
                return o;
            }

            float4 sampleInput(float2 p)
            {
                return float4(SAMPLE_TEXTURE2D_X_LOD(_CameraOpaqueTexture, sampler_CameraOpaqueTexture, LiquidGlassScreenUV(p), 0).rgb, 1);
            }
            float4 sampleField(float2 p) { return float4(0, 0, 0, 0); }

            #include "LiquidGlassMaterial.hlsl"

            float4 frag(Varyings i) : SV_Target
            {
                float2 p = LiquidGlassLayerPixel(i.uv);
                LiquidGlassBeginFragment(p, i.screen.xy / i.screen.w);
                return glassMain(p);
            }
            ENDHLSL
        }
    }
    FallBack Off
}
