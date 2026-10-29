// Native settings and Office integration remain in the complete desktop host.
if (process.platform !== 'darwin') throw Error('macOS entry requires macOS');
require('../../../desktop/main.cjs');
