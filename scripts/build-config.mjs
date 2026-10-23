// electron-builder reads a single config file, so the platform-neutral part of
// package.json's build section is shared and the platform-specific resources,
// targets and artifact names are written here before packaging.
import fs from 'node:fs/promises';
const platform = process.argv[2];
if (!['win', 'mac'].includes(platform))
  throw Error('用法: node scripts/build-config.mjs <win|mac>');
const pkg = JSON.parse(await fs.readFile('package.json', 'utf8'));
const build = pkg.build;
delete build.extraFiles;
delete build.extraResources;
delete build.win;
delete build.mac;
delete build.nsis;
delete build.dmg;
delete build.artifactName;
delete build.fileAssociations;
build.compression = 'normal';
build.directories = { output: 'release' };
build.asar = true;

if (platform === 'win') {
  Object.assign(build, {
    extraFiles: [
      {
        from: 'scripts/register-project-files.cmd',
        to: 'register-project-files.cmd',
      },
      {
        from: 'scripts/register-project-files.ps1',
        to: 'register-project-files.ps1',
      },
    ],
    extraResources: [
      { from: 'desktop/word-host.ps1', to: 'word-host.ps1' },
      { from: 'convert-office.ps1', to: 'convert-office.ps1' },
      { from: 'mcp-dist/page-notes-mcp.cjs', to: 'page-notes-mcp.cjs' },
      { from: 'mcp-dist/mcp-node.exe', to: 'mcp-node.exe' },
      { from: 'mcp-dist/NODE-LICENSE.txt', to: 'NODE-LICENSE.txt' },
      { from: 'mcp-dist/node-runtime.json', to: 'node-runtime.json' },
      { from: 'THIRD-PARTY-NOTICES.txt', to: 'THIRD-PARTY-NOTICES.txt' },
      { from: 'LICENSE', to: 'LICENSE-pagenote.txt' },
      { from: 'LICENSING.md', to: 'LICENSING.md' },
      { from: 'licenses/MIT-legacy.txt', to: 'licenses/MIT-legacy.txt' },
      {
        from: 'build/licenses/PaddleOCR-LICENSE.txt',
        to: 'LICENSE-PaddleOCR.txt',
      },
      {
        from: 'build/licenses/Guten-OCR-LICENSE.txt',
        to: 'LICENSE-Guten-OCR.txt',
      },
    ],
    win: {
      target: ['nsis', 'zip'],
      icon: 'build/icon.ico',
      signAndEditExecutable: true,
      signExecutable: false,
    },
    nsis: {
      oneClick: false,
      perMachine: false,
      allowToChangeInstallationDirectory: true,
      createDesktopShortcut: true,
      createStartMenuShortcut: true,
      shortcutName: 'pagenote',
      artifactName: `pagenote-\${version}-windows-x64-setup.exe`,
      deleteAppDataOnUninstall: false,
    },
    artifactName: 'pagenote-${version}-windows-${arch}.${ext}',
    fileAssociations: [
      {
        ext: 'pagenote',
        name: 'pagenote Project',
        description: 'pagenote project',
        role: 'Editor',
      },
    ],
  });
} else {
  // macOS remains a prerelease independently of the Windows stable channel.
  build.extraMetadata = { ...build.extraMetadata, version: '1.0.0-beta.1' };
  build.files = build.files.filter(
    (file) =>
      !file.startsWith('!node_modules/onnxruntime-node/bin/napi-v6/darwin/'),
  );
  Object.assign(build, {
    extraResources: [
      { from: 'mcp-dist/page-notes-mcp.cjs', to: 'page-notes-mcp.cjs' },
      { from: 'mcp-dist/mcp-node', to: 'mcp-node' },
      { from: 'desktop/mac-office.applescript', to: 'mac-office.applescript' },
      {
        from: 'desktop/mac-powerpoint.applescript',
        to: 'mac-powerpoint.applescript',
      },
      { from: 'mcp-dist/NODE-LICENSE.txt', to: 'NODE-LICENSE.txt' },
      { from: 'mcp-dist/node-runtime.json', to: 'node-runtime.json' },
      { from: 'THIRD-PARTY-NOTICES.txt', to: 'THIRD-PARTY-NOTICES.txt' },
      { from: 'LICENSE', to: 'LICENSE-pagenote.txt' },
      { from: 'LICENSING.md', to: 'LICENSING.md' },
      { from: 'licenses/MIT-legacy.txt', to: 'licenses/MIT-legacy.txt' },
      {
        from: 'build/licenses/PaddleOCR-LICENSE.txt',
        to: 'LICENSE-PaddleOCR.txt',
      },
      {
        from: 'build/licenses/Guten-OCR-LICENSE.txt',
        to: 'LICENSE-Guten-OCR.txt',
      },
    ],
    // macOS uses the AppleScript bridge; omit Windows-only helpers.
    files: build.files.concat([
      '!desktop/word-host.ps1',
      '!convert-office.ps1',
    ]),
    mac: {
      target: [
        { target: 'dmg', arch: ['arm64', 'x64'] },
        { target: 'zip', arch: ['arm64', 'x64'] },
      ],
      icon: 'build/icon.icns',
      category: 'public.app-category.education',
      hardenedRuntime: false,
      identity: null,
      extendInfo: {
        NSAppleEventsUsageDescription:
          'pagenote uses Microsoft Word and PowerPoint to convert documents and save your requested Word edits.',
        CFBundleDocumentTypes: [
          {
            CFBundleTypeName: 'pagenote Project',
            CFBundleTypeExtensions: ['pagenote'],
            CFBundleTypeRole: 'Editor',
            LSHandlerRank: 'Owner',
          },
        ],
      },
    },
    artifactName: 'pagenote-${version}-mac-${arch}.${ext}',
  });
}
pkg.build = build;
await fs.writeFile(
  'electron-builder.json',
  JSON.stringify(pkg.build, null, 2) + '\n',
);
console.log(`已写入 electron-builder.json（${platform}）`);
