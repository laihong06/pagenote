import fs from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const root = path.resolve(import.meta.dirname, '..');
if (process.platform !== 'darwin') {
  console.log('Skip macOS native settings helper on this platform');
  process.exit(0);
}
const bundle = path.join(root, 'build/mac-settings/pagenote Settings.app');
const binary = path.join(bundle, 'Contents/MacOS/pagenote-settings');
await fs.mkdir(path.dirname(binary), { recursive: true });
await fs.writeFile(
  path.join(bundle, 'Contents/Info.plist'),
  `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>io.pagenote.settings-helper</string>
<key>CFBundleName</key><string>pagenote Settings</string>
<key>CFBundleExecutable</key><string>pagenote-settings</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleVersion</key><string>1</string>
<key>LSMinimumSystemVersion</key><string>12.0</string>
<key>LSUIElement</key><true/>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>`,
);
const { stderr } = await promisify(execFile)(
  'xcrun',
  [
    'swiftc',
    '-O',
    '-target',
    'arm64-apple-macosx12.0',
    path.join(root, 'desktop/mac-settings.swift'),
    '-o',
    binary,
  ],
  { maxBuffer: 2 * 1024 * 1024 },
);
if (stderr) process.stderr.write(stderr);
console.log('Built native AppKit settings helper (macOS 12+, arm64)');
