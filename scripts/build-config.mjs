// electron-builder reads a single config file, so the platform-neutral part of
// package.json's build section is shared and the platform-specific resources,
// targets and artifact names are written here before packaging.
import fs from 'node:fs/promises';
import { loadPlatform } from './platform-config.mjs';
const platform = process.argv[2];
if (!['win', 'mac', 'linux'].includes(platform))
  throw Error('用法: node scripts/build-config.mjs <win|mac|linux>');
const pkg = JSON.parse(await fs.readFile('package.json', 'utf8'));
const id =
  platform === 'win' ? 'windows' : platform === 'mac' ? 'macos' : 'linux';
let platformConfig;
try {
  platformConfig = await loadPlatform(process.cwd(), id);
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
if (platformConfig) pkg.version = platformConfig.developmentVersion;
const build = pkg.build;
if (platformConfig) {
  build.extraMetadata = {
    ...build.extraMetadata,
    version: pkg.version,
    main: `apps/desktop/${id}/main.cjs`,
  };
  build.files = [...build.files, 'apps/desktop/**', 'platforms/**'];
}
delete build.extraFiles;
delete build.extraResources;
delete build.win;
delete build.mac;
delete build.linux;
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
} else if (platform === 'linux') {
  build.files = build.files
    .filter(
      (file) =>
        !file.startsWith('!node_modules/onnxruntime-node/bin/napi-v6/linux/'),
    )
    .concat([
      '!desktop/word-host.ps1',
      '!convert-office.ps1',
      '!node_modules/onnxruntime-node/bin/napi-v6/win32/**',
    ]);
  build.extraResources = [
    'page-notes-mcp.cjs',
    'mcp-node',
    'NODE-LICENSE.txt',
    'node-runtime.json',
  ]
    .map((name) => ({ from: 'mcp-dist/' + name, to: name }))
    .concat([
      { from: 'THIRD-PARTY-NOTICES.txt', to: 'THIRD-PARTY-NOTICES.txt' },
      { from: 'LICENSE', to: 'LICENSE-pagenote.txt' },
      { from: 'build/licenses', to: 'licenses' },
      { from: 'LICENSING.md', to: 'LICENSING.md' },
      { from: 'licenses/MIT-legacy.txt', to: 'licenses/MIT-legacy.txt' },
    ]);
  build.linux = {
    target: ['AppImage', 'deb'],
    icon: 'build/icon.png',
    category: 'Education',
    maintainer: 'pagenote contributors',
  };
  build.artifactName = 'pagenote-${version}-linux-${arch}.${ext}';
  build.fileAssociations = [
    {
      ext: 'pagenote',
      name: 'pagenote Project',
      mimeType: 'application/x-pagenote',
      role: 'Editor',
    },
  ];
} else {
  // macOS remains a prerelease independently of the Windows stable channel.
  build.extraMetadata = {
    ...build.extraMetadata,
    version: pkg.version.includes('-') ? pkg.version : pkg.version + '-beta.1',
  };
  build.files = build.files.filter(
    (file) =>
      !file.startsWith('!node_modules/onnxruntime-node/bin/napi-v6/darwin/'),
  );
  Object.assign(build, {
    extraResources: [
      {
        from: 'build/mac-settings/pagenote Settings.app',
        to: 'pagenote Settings.app',
      },
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
        { target: 'dmg', arch: ['arm64'] },
        { target: 'zip', arch: ['arm64'] },
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
if (/-beta\./.test(pkg.version)) {
  build.appId = 'io.pagenote.desktop.beta';
  build.productName = 'pagenote Beta';
  if (platform === 'win') build.executableName = 'pagenote';
  else delete build.executableName;
  build.directories.output = 'release/beta-' + pkg.version;
  // Beta installs alongside stable and never claims its project association.
  delete build.fileAssociations;
  if (build.nsis) build.nsis.shortcutName = 'pagenote Beta';
  if (build.mac?.extendInfo) delete build.mac.extendInfo.CFBundleDocumentTypes;
  if (platform === 'win') build.extraFiles = [];
}
pkg.build = build;
await fs.writeFile(
  'electron-builder.json',
  JSON.stringify(pkg.build, null, 2) + '\n',
);
console.log(`已写入 electron-builder.json（${platform}）`);
