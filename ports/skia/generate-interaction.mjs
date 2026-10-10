/** Apache-2.0. Bundle the existing portable controller without another maintained copy. */
import {readFile, writeFile} from 'node:fs/promises';

const read = async name => (await readFile(new URL(`../web/${name}.mjs`, import.meta.url), 'utf8')).replace(/\r\n/g, '\n');
const core = await read('core');
const interaction = await read('interaction');
const imports = interaction.match(/^import .* from '\.\/core\.mjs';$/gm);
if (imports?.length !== 1 || /^import /m.test(core))
  throw new Error('Portable module imports changed; update the interaction bundler explicitly.');
const output = '// Apache-2.0. Generated from ports/web/core.mjs and interaction.mjs.\n' +
  '// Do not edit. npm test rejects drift; this package needs no sibling directory at runtime.\n' +
  core.replace(/^export function /gm, 'function ').trimEnd() + '\n\n' +
  interaction.replace(imports[0] + '\n', '').trimEnd() + '\n';
const target = new URL('interaction.mjs', import.meta.url);
if (process.argv.includes('--check')) {
  const actual = await readFile(target, 'utf8').catch(error => { if (error.code === 'ENOENT') return ''; throw error; });
  if (actual.replace(/\r\n/g, '\n') !== output)
    throw new Error('Bundled interaction is stale. Run npm run generate:interaction in ports/skia.');
  console.log('Bundled interaction matches the portable source modules.');
} else {
  await writeFile(target, output);
  console.log('Generated self-contained interaction.mjs.');
}
