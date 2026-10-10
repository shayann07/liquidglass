import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, extname, relative, sep } from 'node:path';
const root=fileURLToPath(new URL('.',import.meta.url));
const skiaRoot=fileURLToPath(new URL('../skia/',import.meta.url));
const skiaRuntime=new Set(['node_modules/canvaskit-wasm/bin/canvaskit.js','node_modules/canvaskit-wasm/bin/canvaskit.wasm']);
createServer(async(req,res)=>{
  try {
    const requested=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
    const skia=requested.startsWith('/skia/'),base=resolve(skia?skiaRoot:root);
    const path=resolve(base,'.'+(skia?requested.slice(5):requested.startsWith('/web/')?requested.slice(4):requested));
    const local=relative(base,path).split(sep).join('/');
    if((path!==base&&!path.startsWith(base+sep))||
       (local.includes('node_modules/')&&!(skia&&skiaRuntime.has(local)))) {res.writeHead(403);res.end();return;}
    const file=path===base?resolve(base,'index.html'):path;
    const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript',
      '.wasm':'application/wasm','.sksl':'text/plain','.css':'text/css'}[extname(file)];
    if(!mime) {res.writeHead(404);res.end();return;}
    const body=await readFile(file);
    res.writeHead(200,{'Content-Type':mime,'Cache-Control':'no-store'});res.end(body);
  }catch {res.writeHead(404);res.end();}
}).listen(8766,'127.0.0.1',()=>console.log('Web preview http://127.0.0.1:8766'));
