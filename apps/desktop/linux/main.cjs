// Preserve the complete shared desktop host and isolate the Linux entry.
if (process.platform !== 'linux') throw Error('Linux entry requires Linux');
require('../../../desktop/main.cjs');
