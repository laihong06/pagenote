#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
android_dir=$(cd "$script_dir/.." && pwd)

sdk_dir=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z $sdk_dir ]]; then
  if [[ -f $android_dir/local.properties ]]; then
    sdk_dir=$(sed -n 's/^sdk.dir=//p' "$android_dir/local.properties" | head -n 1)
    sdk_dir=${sdk_dir//\\:/:}
  elif [[ -d $HOME/Library/Android/sdk ]]; then
    sdk_dir=$HOME/Library/Android/sdk
  elif [[ -d $HOME/Android/Sdk ]]; then
    sdk_dir=$HOME/Android/Sdk
  fi
fi

adb=${ADB:-$sdk_dir/platform-tools/adb}
if [[ ! -x $adb ]]; then
  echo "adb not found at $adb" >&2
  exit 2
fi

connected_serials=()
while IFS= read -r serial; do
  [[ -n $serial ]] && connected_serials+=("$serial")
done < <("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if ((${#connected_serials[@]} == 0)); then
  echo "No connected Android devices found." >&2
  exit 2
fi

target_serial=${TARGET_AVD_SERIAL:-}
api34_serials=()
for serial in "${connected_serials[@]}"; do
  [[ $serial == emulator-* ]] || continue
  api_level=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
  if [[ $api_level == 34 ]]; then
    api34_serials+=("$serial")
  fi
done

if [[ -n $target_serial ]]; then
  if [[ $target_serial != emulator-* ]]; then
    echo "TARGET_AVD_SERIAL must identify an emulator, not '$target_serial'." >&2
    exit 2
  fi
  if [[ ! " ${connected_serials[*]} " == *" $target_serial "* ]]; then
    echo "Selected API 34 emulator '$target_serial' is not connected." >&2
    exit 2
  fi
  api_level=$("$adb" -s "$target_serial" shell getprop ro.build.version.sdk | tr -d '\r')
  if [[ $api_level != 34 ]]; then
    echo "Selected emulator '$target_serial' must run Android 14/API 34 (found '$api_level')." >&2
    exit 2
  fi
else
  if ((${#api34_serials[@]} == 0)); then
    echo "No connected Android 14/API 34 emulator found." >&2
    exit 2
  fi
  if ((${#api34_serials[@]} > 1)); then
    printf 'More than one API 34 emulator is connected. Set TARGET_AVD_SERIAL to one of: %s\n' "${api34_serials[*]}" >&2
    exit 2
  fi
  target_serial=${api34_serials[0]}
fi

target_abi=$("$adb" -s "$target_serial" shell getprop ro.product.cpu.abi | tr -d '\r')
if [[ $target_abi != arm64-v8a ]]; then
  echo "Selected API 34 emulator '$target_serial' must use arm64-v8a (found '$target_abi'); this helper builds only the target ABI APK." >&2
  exit 2
fi

cd "$android_dir"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

app_apk="$android_dir/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
test_apk="$android_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
for apk in "$app_apk" "$test_apk"; do
  if [[ ! -s $apk ]]; then
    echo "Expected APK was not built: $apk" >&2
    exit 1
  fi
  "$adb" -s "$target_serial" install -r "$apk"
done

remote_dir=/sdcard/Android/data/io.pagenote.android.debug/files/markdown-preview-captures
test_runner=io.pagenote.android.debug.test/androidx.test.runner.AndroidJUnitRunner
if ! output=$("$adb" -s "$target_serial" shell am instrument -w -r \
  -e class io.pagenote.android.MarkdownPreviewVisualCaptureTest#capturesRichLightMarkdownPreviewAndChecksMermaidRasterBudget \
  "$test_runner" 2>&1); then
  printf '%s\n' "$output" >&2
  echo "Markdown preview instrumentation failed on $target_serial." >&2
  exit 1
fi
printf '%s\n' "$output"
if ! grep -Eq 'OK \(1 test\)' <<< "$output"; then
  echo "Markdown preview instrumentation did not report exactly one passing test on $target_serial." >&2
  exit 1
fi

report_dir="$android_dir/build/reports/markdown-preview/$target_serial"
mkdir -p "$report_dir"
output_path="$report_dir/rich-light.png"
"$adb" -s "$target_serial" pull "$remote_dir/rich-light.png" "$output_path"
if [[ ! -s $output_path ]]; then
  echo "Missing or empty screenshot from $target_serial: rich-light.png" >&2
  exit 1
fi
signature=$(od -An -tx1 -N8 "$output_path" | tr -d ' \n')
if [[ $signature != 89504e470d0a1a0a ]]; then
  echo "Invalid PNG screenshot from $target_serial: rich-light.png" >&2
  exit 1
fi
printf 'Captured one Markdown preview from %s: %s\n' "$target_serial" "$report_dir"
