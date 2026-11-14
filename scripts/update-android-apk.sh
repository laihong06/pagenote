#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
main_worktree="$(git -C "$repo_dir" worktree list --porcelain | sed -n '1s/^worktree //p')"
apk_dir="${PAGENOTE_APK_DIR:-${main_worktree}/android/build/apk-latest}"
if [[ -z "${ANDROID_HOME:-}" && -d "${HOME}/Library/Android/sdk" ]]; then
  export ANDROID_HOME="${HOME}/Library/Android/sdk"
fi

(cd "$repo_dir/android" && ./gradlew :app:assembleDebug)
mkdir -p "$apk_dir"
staging_dir="$(mktemp -d "$apk_dir/.update.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT
output_dir="$repo_dir/android/app/build/outputs/apk/debug"
cp "$output_dir/app-arm64-v8a-debug.apk" "$staging_dir/PageNote-latest-arm64.apk"
cp "$output_dir/app-armeabi-v7a-debug.apk" "$staging_dir/PageNote-latest-armeabi-v7a.apk"
cp "$output_dir/output-metadata.json" "$staging_dir/output-metadata.json"
(
  cd "$staging_dir"
  shasum -a 256 PageNote-latest-arm64.apk PageNote-latest-armeabi-v7a.apk > SHA256SUMS.txt
)
{
  printf 'Local debug build, not a public release.\n'
  printf 'Built at: %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
  printf 'Source HEAD: %s\n' "$(git -C "$repo_dir" rev-parse HEAD)"
  if [[ -n "$(git -C "$repo_dir" status --porcelain)" ]]; then
    printf 'Source worktree: contains uncommitted changes\n'
  else
    printf 'Source worktree: clean\n'
  fi
  printf 'Application ID: io.pagenote.android.debug\n'
  printf 'Minimum Android version: 14 (API 34)\n'
} > "$staging_dir/BUILD-INFO.txt"
for file in PageNote-latest-arm64.apk PageNote-latest-armeabi-v7a.apk output-metadata.json SHA256SUMS.txt BUILD-INFO.txt; do
  mv -f "$staging_dir/$file" "$apk_dir/$file"
done
printf '\nLatest APKs: %s\n' "$apk_dir"
