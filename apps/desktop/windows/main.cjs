// Explicit platform entry; retain the complete, tested desktop host.
if (process.platform !== 'win32') throw Error('Windows entry requires Windows');
require('../../../desktop/main.cjs');
