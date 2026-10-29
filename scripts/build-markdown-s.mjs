import { build } from 'esbuild';
import fs from 'node:fs/promises';
// Offline, pinned dependency. Never fetch a CDN from a user's notebook.
const result = await build({
  stdin: {
    contents: "import * as THREE from 'three'; window.THREE = THREE;",
    resolveDir: process.cwd(),
  },
  bundle: true,
  minify: true,
  format: 'iife',
  platform: 'browser',
  write: false,
  legalComments: 'inline',
});
await fs.mkdir('src/generated', { recursive: true });
await fs.writeFile(
  'src/generated/markdown-s-three.js',
  'export default ' + JSON.stringify(result.outputFiles[0].text) + ';\n',
);
console.log('Built offline Markdown S Three.js runtime');
