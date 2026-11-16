/** Apache-2.0. In-app material parameters from GlassStyle.inApp / GlassMaterial.
 * This is an adapter to the production shader, not another optical implementation.
 */
import {clearMaterialUniforms} from './renderer.mjs';

const lightOpacity=[[0,.435],[31,.514],[50,.565],[62,.600],[100,.733]];
const darkOpacity=[[0,.416],[29,.439],[50,.451],[62,.510],[86,.655],[100,.737]];
function interpolate(table,t) {
  let i=0;while(i<table.length-2&&table[i+1][0]<=t)i++;
  const [x,a]=table[i],[y,b]=table[i+1];return a+(b-a)*(t-x)/(y-x);
}

// GlassStyle.towardLab: the lab fit (#199, #200) laid over the earlier fit by a weight that is 1 at the
// default Tint Amount and 0 at 0 and 100. The constants are GlassMaterial.lab*.
const LAB_WIDE_KERNEL=15.5,LAB_DETAIL_REACH=1/3;
// Single precision, as GlassMaterial.labWeight computes it.
const labWeight=amount=>{
  const r=Math.fround,away=r(Math.abs(r(Math.min(100,Math.max(0,r(amount)))-50)));
  return Math.min(1,Math.max(0,r(1-r(away/50))));
};
const labOpacity=dark=>dark?.3985:.498;
const labLift=dark=>dark?31.72/255:5.5/255;
const labDetailShare=dark=>dark?.277:.273;
const labDetailLift=dark=>dark?-.22:.22;
const labToneCurve=dark=>dark?101.01/255:0;
// GlassMaterial.labChromaGain / labChromaSlope (#201): the colour transfer's g0 and g1 per channel (R, G, B).
const labChromaGain=dark=>dark?[1.317,1.256,1.314]:[.988,.952,.979];
const labChromaSlope=dark=>dark?[-.427,-.351,-.429]:[-.076,.028,-.045];
/** GlassMaterial.chromaOf: [g0 R,G,B, g1 R,G,B, weight] from GlassStyle.detailLift. The sign picks the appearance and
 * |c| / |labDetailLift| is the weight; zero lift is seven zeros (the per-channel tone alone). */
export function chromaOf(detailLift) {
  const r=Math.fround,dark=detailLift<0;
  const weight=Math.min(1,Math.max(0,r(r(Math.abs(r(detailLift)))/r(Math.abs(labDetailLift(dark))))));
  if(weight<=0) return Array(7).fill(0);
  return [...labChromaGain(dark).map(r),...labChromaSlope(dark).map(r),weight];
}
// Compose's Color lerp runs in Oklab and stores 8 bits per channel again (androidx.compose.ui.graphics.lerp).
// The Oklab intermediate keeps alpha in 10 bits, truncated: that is the step the golden values show.
const clamp01=x=>Math.min(1,Math.max(0,x));
const srgbToLinear=c=>c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);
const linearToSrgb=c=>c<=.0031308?c*12.92:1.055*Math.pow(c,1/2.4)-.055;
const q8=x=>Math.floor(clamp01(x)*255+.5)/255;
function toOklab([r,g,b]) {
  const R=srgbToLinear(r),G=srgbToLinear(g),B=srgbToLinear(b);
  const l=Math.cbrt(.4122214708*R+.5363325363*G+.0514459929*B);
  const m=Math.cbrt(.2119034982*R+.6806995451*G+.1073969566*B);
  const s=Math.cbrt(.0883024619*R+.2817188376*G+.6299787005*B);
  return [.2104542553*l+.793617785*m-.0040720468*s,1.9779984951*l-2.428592205*m+.4505937099*s,
    .0259040371*l+.7827717662*m-.808675766*s];
}
function fromOklab([L,a,b]) {
  const l=Math.pow(L+.3963377774*a+.2158037573*b,3),m=Math.pow(L-.1055613458*a-.0638541728*b,3),
    s=Math.pow(L-.0894841775*a-1.291485548*b,3);
  return [4.0767416621*l-3.3077115913*m+.2309699292*s,-1.2684380046*l+2.6097574011*m-.3413193965*s,
    -.0041960863*l-.7034186147*m+1.707614701*s].map(c=>linearToSrgb(clamp01(c)));
}
function lerpColor(a,b,w) {
  const p=toOklab(a),q=toOklab(b),rgb=fromOklab(p.map((x,i)=>x+(q[i]-x)*w));
  return [...rgb.map(q8),q8(Math.floor(clamp01(a[3]+(b[3]-a[3])*w)*1023)/1023)];
}

/** Lengths are device pixels; density converts the measured dp material constants only.
 * `tint` ([r, g, b, a], sRGB 0..1) is Compose's `Glass.tint(color)`: it replaces the tint colour and
 * its opacity, quantized to 8 bits per channel as Compose's sRGB Color is; the dark lift stays.
 * `light` ([x, y], toward the light, y up negative) is Compose's GlassLight: the default is
 * `GlassLight.Default`, as `Glass.Regular` binds it; [0, -1] is iOS 27's measured overhead light. */
export function inAppMaterialUniforms(effects, {width,height,pad=2,radius=20,density=1,
  dark=false,tintAmount=50,materialize=1,reducedTransparency=0,increasedContrast=0,tint=null,
  light=[-.35,-.94]}) {
  if (![density,tintAmount,materialize,reducedTransparency,increasedContrast].every(Number.isFinite)||
      density<=0||tintAmount<0||tintAmount>100||
      [materialize,reducedTransparency,increasedContrast].some(x=>x<0||x>1)||typeof dark!=='boolean')
    throw new RangeError('Invalid material options: positive density, tint 0..100, preferences 0..1, boolean dark required');
  const lightDir=Array.from(light??[]);
  if(lightDir.length!==2||lightDir.some(x=>!Number.isFinite(x)))
    throw new RangeError('Light requires two finite components');
  const tintColor=tint==null?null:Array.from(tint);
  if(tintColor&&(tintColor.length!==4||tintColor.some(x=>!Number.isFinite(x)||x<0||x>1)))
    throw new RangeError('Tint requires four sRGB components between 0 and 1');
  const radii=typeof radius==='number'?Array(4).fill(radius):Array.from(radius??[]);
  if(radii.length!==4||radii.some(x=>!Number.isFinite(x)||x<0))
    throw new RangeError('Radius requires one or four nonnegative finite pixel values');
  const values=clearMaterialUniforms(effects,{width,height,pad,magnification:1});
  const t=tintAmount/100,limit=Math.min(width,height)/2;
  const r=radii.map(x=>Math.min(x,limit));
  const grey=dark?0:241/255;
  Object.assign(values,{
    uRadii:r,uRefractBand:.6*Math.max(...r),uRefractDepth:14*density,
    uProfile:1,uFormation:0,uHeldLens:0,uHeldMagnification:0,uHeldGlow:1,
    uAberration:.07,
    uWideKernel:10*density,uFineShare:.95*Math.pow(1-t,1.5),
    // Compose's sRGB Color.copy quantizes alpha to8 bits; preserve its actual shader input.
    uTint:[grey,grey,grey,Math.round(interpolate(dark?darkOpacity:lightOpacity,tintAmount)*255)/255],
    uLift:dark?35/255:0,uBevel:1.33*density,uBevelPeak:.25,
    uSpecular:.25,uSpecularPow:6,uCounterLight:1,uEdgeLight:0,uLight:lightDir,
    uLegibility:.6,uScale:Math.max(0,Math.min(1,(Math.min(width,height)/density-56)/264)),
    uMaterialize:materialize,uFrost:reducedTransparency,uContrast:increasedContrast,
  });
  // The lab fit (GlassStyle.towardLab). The detail reach is the lab's at every tint, which is what selects the
  // four-tap detail kernel (uDetailCross: 0 < blurRadius <= 1/3 dp, decided in dp).
  const w=labWeight(tintAmount);
  Object.assign(values,{uBlur:LAB_DETAIL_REACH*density,uDetailCross:1});
  if(w>0) {
    // Kotlin blends in Float, step by step; the goldens are compared at single precision. `f` is towardLab's
    // a + (b - a) * w, `lerpDp` the Dp lerp (1 - w) * a + w * b.
    const r=Math.fround;
    const f=(a,b)=>r(r(a)+r(r(r(b)-r(a))*w));
    const lerpDp=(a,b)=>r(r(r(1-w)*r(a))+r(w*r(b)));
    Object.assign(values,{
      uTint:lerpColor(values.uTint,[dark?0:1,dark?0:1,dark?0:1,q8(labOpacity(dark))],w),
      uLift:f(values.uLift,labLift(dark)),
      uFineShare:f(values.uFineShare,labDetailShare(dark)),
      uWideKernel:lerpDp(values.uWideKernel,LAB_WIDE_KERNEL*density),
      uDetailLift:labDetailLift(dark)*w,uToneCurve:labToneCurve(dark)*w,
      uLegibility:f(values.uLegibility,0),uSoftDetail:f(values.uSoftDetail,1),
    });
  }
  const chroma=chromaOf(values.uDetailLift??0);
  Object.assign(values,{uChromaGain:chroma.slice(0,3),uChromaSlope:chroma.slice(3,6),uChromaWeight:chroma[6]});
  if(tintColor)values.uTint=tintColor.map(x=>Math.round(x*255)/255);
  return values;
}
