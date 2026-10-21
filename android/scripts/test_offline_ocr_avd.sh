#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# != 1 || $1 != emulator-* ]]; then
  echo "Usage: $0 emulator-serial" >&2
  exit 2
fi

serial=$1
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

"$adb" -s "$serial" get-state >/dev/null
api_level=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
page_size=$("$adb" -s "$serial" shell getconf PAGESIZE | tr -d '\r')
if ((api_level < 34)); then
  echo "Offline OCR AVD acceptance requires API 34 or later (found API $api_level)." >&2
  exit 2
fi
if [[ $page_size != 16384 ]]; then
  echo "Offline OCR page-size acceptance requires a 16 KB AVD (found $page_size bytes)." >&2
  exit 2
fi

cd "$android_dir"
./gradlew :feature-ocr:assembleDebugAndroidTest --offline
apk="$android_dir/feature-ocr/build/outputs/apk/androidTest/debug/feature-ocr-debug-androidTest.apk"
if [[ ! -f $apk ]]; then
  echo "OCR instrumentation APK was not produced: $apk" >&2
  exit 1
fi

prior_airplane=$("$adb" -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')
prior_mobile=$("$adb" -s "$serial" shell settings get global mobile_data | tr -d '\r')
if [[ $("$adb" -s "$serial" shell cmd wifi status) == "Wifi is enabled"* ]]; then
  prior_wifi=1
else
  prior_wifi=0
fi
if [[ ! $prior_airplane =~ ^[01]$ || ! $prior_mobile =~ ^[01]$ ]]; then
  echo "Could not read the emulator's airplane/mobile-data state." >&2
  exit 1
fi

restore_networks() {
  local result=$?
  local restore_failed=0
  trap - EXIT

  if [[ $prior_airplane == 1 ]]; then
    "$adb" -s "$serial" shell cmd connectivity airplane-mode enable >/dev/null 2>&1 || restore_failed=1
  else
    "$adb" -s "$serial" shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || restore_failed=1
  fi
  if [[ $prior_wifi == 1 ]]; then
    "$adb" -s "$serial" shell cmd wifi set-wifi-enabled enabled >/dev/null 2>&1 || restore_failed=1
  else
    "$adb" -s "$serial" shell cmd wifi set-wifi-enabled disabled >/dev/null 2>&1 || restore_failed=1
  fi
  if [[ $prior_mobile == 1 ]]; then
    "$adb" -s "$serial" shell svc data enable >/dev/null 2>&1 || restore_failed=1
  else
    "$adb" -s "$serial" shell svc data disable >/dev/null 2>&1 || restore_failed=1
  fi

  local restored_airplane restored_mobile restored_wifi
  restored_airplane=$("$adb" -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')
  restored_mobile=$("$adb" -s "$serial" shell settings get global mobile_data | tr -d '\r')
  if [[ $("$adb" -s "$serial" shell cmd wifi status) == "Wifi is enabled"* ]]; then
    restored_wifi=1
  else
    restored_wifi=0
  fi
  if [[ $restored_airplane != "$prior_airplane" || $restored_mobile != "$prior_mobile" || $restored_wifi != "$prior_wifi" ]]; then
    echo "Failed to restore the emulator's original radio state." >&2
    restore_failed=1
  else
    echo "Restored emulator network settings."
  fi

  if ((restore_failed != 0 && result == 0)); then result=1; fi
  exit "$result"
}
trap restore_networks EXIT

"$adb" -s "$serial" shell cmd connectivity airplane-mode enable >/dev/null
"$adb" -s "$serial" shell cmd wifi set-wifi-enabled disabled >/dev/null
"$adb" -s "$serial" shell svc data disable >/dev/null

active_network=
for _ in {1..15}; do
  active_network=$("$adb" -s "$serial" shell dumpsys connectivity | sed -n 's/^Active default network: //p' | head -n 1 | tr -d '\r')
  [[ $active_network == none ]] && break
  sleep 1
done
airplane=$("$adb" -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')
wifi_status=$("$adb" -s "$serial" shell cmd wifi status | sed -n '1p' | tr -d '\r')
mobile=$("$adb" -s "$serial" shell settings get global mobile_data | tr -d '\r')
if [[ $airplane != 1 || $wifi_status != 'Wifi is disabled' || $mobile != 0 || $active_network != none ]]; then
  echo "Emulator is not fully offline; refusing to run OCR acceptance." >&2
  printf 'airplane=%s wifi=%s mobile_data=%s active_default_network=%s\n' "$airplane" "$wifi_status" "$mobile" "$active_network" >&2
  exit 1
fi

echo "Offline verified: API $api_level, page size $page_size, no active default network."
"$adb" -s "$serial" install -r "$apk"
set +e
output=$("$adb" -s "$serial" shell am instrument -w -r io.pagenote.feature.ocr.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
test_status=$?
set -e
printf '%s\n' "$output"
if ((test_status != 0)) || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' <<< "$output"; then
  echo "Offline OCR instrumentation did not report a passing test suite." >&2
  exit 1
fi
