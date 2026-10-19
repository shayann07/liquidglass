/** Apache-2.0. Write the .meta file Unity needs beside every asset and folder of the package.
 *
 *   node tool/write_meta.mjs           write them
 *   node tool/write_meta.mjs --check   fail if one is missing or different
 *
 * A package installed from git or a registry is immutable, and Unity ignores an asset there that has no
 * .meta file. Unity is not installed where this package is developed, so the files are written here: each
 * GUID is derived from the asset's path (MD5), so it never changes while the file keeps its name, and
 * references to the shaders or scripts from a user's project stay valid across versions.
 */
import {createHash} from 'node:crypto';
import {readdir, readFile, writeFile, stat} from 'node:fs/promises';
import {join, relative, extname, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('../com.shayxo.liquidglass', import.meta.url));
const guid = path => createHash('md5').update(`com.shayxo.liquidglass/${path}`).digest('hex');

// Unity writes "userData: " with a trailing space; YAML reads it the same without, and git diff --check stays clean.
const tail = `  userData:\n  assetBundleName:\n  assetBundleVariant:\n`;
const importer = {
  folder: () => `folderAsset: yes\nDefaultImporter:\n  externalObjects: {}\n${tail}`,
  '.cs': () => `MonoImporter:\n  externalObjects: {}\n  serializedVersion: 2\n  defaultReferences: []\n  executionOrder: 0\n  icon: {instanceID: 0}\n${tail}`,
  '.shader': () => `ShaderImporter:\n  externalObjects: {}\n  defaultTextures: []\n  nonModifiableTextures: []\n${tail}`,
  '.hlsl': () => `ShaderIncludeImporter:\n  externalObjects: {}\n${tail}`,
  '.asmdef': () => `AssemblyDefinitionImporter:\n  externalObjects: {}\n${tail}`,
  'package.json': () => `PackageManifestImporter:\n  externalObjects: {}\n${tail}`,
  text: () => `TextScriptImporter:\n  externalObjects: {}\n${tail}`,
};

async function walk(dir, out) {
  for (const name of (await readdir(dir)).sort()) {
    if (name.endsWith('.meta') || name.startsWith('.')) continue;
    const full = join(dir, name);
    const rel = relative(root, full).split(sep).join('/');
    const isDir = (await stat(full)).isDirectory();
    const kind = isDir ? 'folder' : name === 'package.json' ? 'package.json' : importer[extname(name)] ? extname(name) : 'text';
    out.push({path: `${full}.meta`, text: `fileFormatVersion: 2\nguid: ${guid(rel)}\n${importer[kind]()}`});
    if (isDir) await walk(full, out);
  }
  return out;
}

const metas = await walk(root, []);
if (process.argv.includes('--check')) {
  const stale = [];
  for (const m of metas) {
    const actual = await readFile(m.path, 'utf8').catch(() => null);
    if (actual?.replace(/\r\n/g, '\n') !== m.text) stale.push(relative(root, m.path));
  }
  if (stale.length) throw new Error(`.meta files missing or stale: ${stale.join(', ')}. Run node tool/write_meta.mjs.`);
  console.log(`${metas.length} .meta files match the package's assets.`);
} else {
  for (const m of metas) await writeFile(m.path, m.text);
  console.log(`Wrote ${metas.length} .meta files.`);
}
