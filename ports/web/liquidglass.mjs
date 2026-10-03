/**
 * Apache-2.0. Framework-independent continuous-lens renderer.
 * Receives a caller-owned image/canvas/video; it does not capture arbitrary DOM content.
 * Implements the authored Lens map, not the complete Compose measured material/tab model.
 */
const VERTEX = `attribute vec2 position;
varying vec2 uv;
void main() { uv=vec2(position.x*.5+.5, .5-position.y*.5); gl_Position=vec4(position,0.,1.); }`;
const FRAGMENT = `precision highp float;
varying vec2 uv;
uniform sampler2D source;
uniform vec2 size;
uniform vec4 lens;
uniform float strength;
uniform float pixel;
uniform vec3 ground;
vec3 sampleAt(vec2 p) { vec4 c=texture2D(source,p); return c.rgb*c.a+ground*(1.-c.a); }
void main() {
  vec2 p=uv*size-lens.xy, halfSize=max(lens.zw,vec2(.001));
  vec2 q=p/halfSize;
  float r2=clamp(dot(q,q),0.,1.);
  float shoulder=1.-r2;
  vec2 sourcePoint=uv-p*strength*shoulder*shoulder/size;
  float distance=(length(q)-1.)*min(halfSize.x,halfSize.y);
  float coverage=1.-smoothstep(-pixel,pixel,distance);
  vec3 base=sampleAt(uv), refracted=sampleAt(sourcePoint);
  float light=pow(max(dot(normalize(q+vec2(.00001)),normalize(vec2(-.4,-.8))),0.),2.);
  float rim=exp(-abs(distance)*1.6)*light*.18;
  gl_FragColor=vec4(mix(base,mix(refracted,vec3(1.),rim),coverage),1.);
}`;

export function createGlassRenderer(canvas, { background = [0.035, 0.067, 0.118] } = {}) {
  if (background.length !== 3 || background.some(n => !Number.isFinite(n) || n < 0 || n > 1))
    throw new RangeError('background must contain three normalized RGB values');
  const gl = canvas.getContext('webgl', { alpha: false, antialias: false, preserveDrawingBuffer: true });
  if (!gl) throw new Error('WebGL unavailable: use the documented CSS fallback');
  let resources, disposed = false, lastSource, lastFrame;
  function initialize() {
    function compile(type, code) {
      const shader = gl.createShader(type);
      gl.shaderSource(shader, code); gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        const error = gl.getShaderInfoLog(shader); gl.deleteShader(shader); throw new Error(error);
      }
      return shader;
    }
    const vertex = compile(gl.VERTEX_SHADER, VERTEX), fragment = compile(gl.FRAGMENT_SHADER, FRAGMENT);
    const program = gl.createProgram(); gl.attachShader(program, vertex); gl.attachShader(program, fragment);
    gl.linkProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      const error = gl.getProgramInfoLog(program); gl.deleteProgram(program); throw new Error(error);
    }
    const buffer = gl.createBuffer(), texture = gl.createTexture();
    gl.useProgram(program); gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, -1,1, 1,-1, 1,1]), gl.STATIC_DRAW);
    const position = gl.getAttribLocation(program, 'position');
    gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    const uniforms = Object.fromEntries(['source','size','lens','strength','pixel','ground'].map(n => [n, gl.getUniformLocation(program,n)]));
    gl.uniform1i(uniforms.source,0); gl.uniform3fv(uniforms.ground,background);
    resources = { program, buffer, texture, uniforms };
  }
  function assertLive() { if (disposed) throw new Error('Renderer disposed'); }
  function setSource(source) {
    assertLive();
    if (source === canvas) throw new Error('Backdrop must not contain the glass output itself');
    lastSource = source;
    if (gl.isContextLost()) return;
    gl.bindTexture(gl.TEXTURE_2D, resources.texture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    // The browser enforces same-origin/CORS rules. Security failures are not suppressed.
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,source);
  }
  function render({ width, height, x, y, radius = 90, magnification = 1.25, pixelRatio = 1 }) {
    assertLive();
    if (![width,height,x,y,radius,magnification,pixelRatio].every(Number.isFinite) ||
        width <= 0 || height <= 0 || radius <= 0 || pixelRatio <= 0 || pixelRatio > 4 ||
        magnification < 1 || magnification > 2.5) throw new RangeError('Invalid lens frame');
    lastFrame = {width,height,x,y,radius,magnification,pixelRatio};
    if (!lastSource) throw new Error('Call setSource before rendering');
    if (gl.isContextLost()) return false;
    const w = Math.round(width*pixelRatio), h = Math.round(height*pixelRatio);
    if (w < 1 || h < 1 || Math.max(w,h) > gl.getParameter(gl.MAX_TEXTURE_SIZE)) throw new RangeError('Canvas exceeds device limits');
    if (canvas.width !== w) canvas.width = w;
    if (canvas.height !== h) canvas.height = h;
    gl.viewport(0,0,w,h); gl.useProgram(resources.program);
    const u = resources.uniforms;
    gl.uniform2f(u.size,width,height); gl.uniform4f(u.lens,x,y,radius,radius);
    gl.uniform1f(u.strength,1-1/magnification); gl.uniform1f(u.pixel,1/pixelRatio);
    gl.drawArrays(gl.TRIANGLES,0,6);
    return true;
  }
  const lost = event => event.preventDefault();
  const restored = () => { if (!disposed) { initialize(); if(lastSource) setSource(lastSource); if(lastFrame) render(lastFrame); } };
  initialize();
  canvas.addEventListener('webglcontextlost',lost); canvas.addEventListener('webglcontextrestored',restored);
  return { setSource, render, dispose() {
    if(disposed) return; disposed=true;
    canvas.removeEventListener('webglcontextlost',lost); canvas.removeEventListener('webglcontextrestored',restored);
    gl.deleteTexture(resources.texture); gl.deleteBuffer(resources.buffer); gl.deleteProgram(resources.program);
    lastSource=null; lastFrame=null;
  }};
}
