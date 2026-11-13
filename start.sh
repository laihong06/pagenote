#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# A local runtime can be selected without changing the system-wide Node.js.
if [ -n "${PAGENOTE_NODE_HOME:-}" ]; then
  PATH="$PAGENOTE_NODE_HOME/bin:$PATH"
  export PATH
elif [ -x "$HOME/.local/share/pagenote-runtime/node-v22.22.0-linux-x64/bin/node" ]; then
  PATH="$HOME/.local/share/pagenote-runtime/node-v22.22.0-linux-x64/bin:$PATH"
  export PATH
fi
node -e 'const [a,b]=process.versions.node.split(".").map(Number);if(a<22||(a===22&&b<13)){console.error("pagenote 需要 Node.js >=22.13");process.exit(1)}'
exec npm run desktop -- "$@"
