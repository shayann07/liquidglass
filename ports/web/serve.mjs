import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, extname, sep } from 'node:path';
const root=fileURLToPath(new URL('.',import.meta.url));
createServer(async(req,res)=>{
  try {
    const path=resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname));
    if((path!==resolve(root)&&!path.startsWith(root))||path.includes(sep+'node_modules'+sep)) {res.writeHead(403);res.end();return;}
    const file=path===resolve(root)?resolve(root,'index.html'):path;
    const mime={'.html':'text/html','.mjs':'text/javascript','.css':'text/css'}[extname(file)];
    if(!mime) {res.writeHead(404);res.end();return;}
    const body=await readFile(file);
    res.writeHead(200,{'Content-Type':mime,'Cache-Control':'no-store'});res.end(body);
  }catch {res.writeHead(404);res.end();}
}).listen(8766,'127.0.0.1',()=>console.log('Web preview http://127.0.0.1:8766'));
