#!/usr/bin/env bash
# Device-specific bootstrap; never use P0's fixed index/hash.
# Usage: scripts/bootstrap_settings_for_bond.sh <adb-target> <confirmed-classic-bond-address> --confirmed-max2
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ $# -ne 3 || "$3" != "--confirmed-max2" ]]; then
  echo 'Usage: bootstrap_settings_for_bond.sh <adb-target> <confirmed-bond-address> --confirmed-max2' >&2
  exit 2
fi
SERIAL="$1"
BOND="$2"
if [[ ! "$BOND" =~ ^([[:xdigit:]]{2}:){5}[[:xdigit:]]{2}$ ]] || [[ -z "$SERIAL" ]] || [[ "$SERIAL" == *" "* ]] || [[ "$SERIAL" == *";"* ]]; then
  echo 'Invalid device target/address; refusing operation' >&2
  exit 2
fi
BASE=/data/local/tmp/airpods-native-p0
JAR=/data/local/tmp/airpods-p0-metadata.jar
mkdir -p "$ROOT/state"
SESSION="$(mktemp -d "$ROOT/state/bootstrap-XXXXXXXX")"
chmod 700 "$SESSION"
# Never overwrite P0's stored original snapshot. Each new bootstrap has an
# independently named private remote transaction directory.
REMOTE="$BASE/$(basename "$SESSION")"
ORIGINAL="$SESSION/key25.original"
PROPOSED="$SESSION/key25.proposed"
CMD="CLASSPATH=$JAR app_process /system/bin dev.airpodsnative.bootstrap.MetadataProbe"
CHANGED=false
RESTORED=false
restore() {
  if [[ "$CHANGED" == "true" && "$RESTORED" == "false" ]]; then
    echo "Bootstrap failed after change: attempting exact-hash rollback..." >&2
    if adb -s "$SERIAL" shell "$CMD swap-target $BOND $REMOTE/key25.proposed $REMOTE/key25.original $NEXT_SHA" | grep -q 'READBACK_MATCH=true'; then
      RESTORED=true
      echo 'Rollback verified.' >&2
    else
      echo "CRITICAL: automatic rollback unverified. Keep private snapshot in $SESSION and reconnect the device." >&2
    fi
  fi
}
trap restore EXIT
if [[ "$(adb -s "$SERIAL" get-state)" != "device" ]]; then
  echo 'ADB target unavailable/not authorized' >&2; exit 3
fi
# The Settings AIDL wire layout was observed only on Moto Edge 70 Android 17.
# A Pixel or other OEM may require a completely different integration adapter.
MANUFACTURER="$(adb -s "$SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
SDK="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "${MANUFACTURER,,}" != "motorola" || "${MODEL,,}" != "motorola edge 70" || "$SDK" != "37" ]]; then
  echo 'BLOCKED: unknown OEM Settings Binder format; only tested Moto Edge 70 API37 is accepted.' >&2
  exit 5
fi
if ! adb -s "$SERIAL" shell pm path dev.airpodsnative.companion | grep -q '^package:'; then
  echo 'BLOCKED: companion APK is not installed.' >&2; exit 6
fi
if ! adb -s "$SERIAL" shell dumpsys package com.android.shell | grep -q 'android.permission.BLUETOOTH_PRIVILEGED: granted=true'; then
  echo 'ADB Shell lacks Bluetooth privilege; cannot initialize' >&2; exit 4
fi
adb -s "$SERIAL" shell "mkdir -p $REMOTE && chmod 700 $REMOTE"
bash tests/p0-shell-metadata/build.sh > "$SESSION/build.log"
adb -s "$SERIAL" push state/p0-shell-metadata/metadata-probe.jar "$JAR" >/dev/null
adb -s "$SERIAL" shell "$CMD backup-target $BOND $REMOTE/key25.original" > "$SESSION/receipt-backup.txt"
grep -q 'BACKUP_OK=true' "$SESSION/receipt-backup.txt"
adb -s "$SERIAL" pull "$REMOTE/key25.original" "$ORIGINAL" >/dev/null
python3 scripts/settings_bootstrap_editor.py --source "$ORIGINAL" --destination "$SESSION" > "$SESSION/editor-receipt.json"
PREV_SHA="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["expected_original_sha256"])' "$SESSION/manifest.json")"
NEXT_SHA="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["expected_proposed_sha256"])' "$SESSION/manifest.json")"
adb -s "$SERIAL" push "$PROPOSED" "$REMOTE/key25.proposed" >/dev/null
if [[ "$PREV_SHA" == "$NEXT_SHA" ]]; then
  echo 'Settings Provider metadata already registered; verifying without writing.'
else
  CHANGED=true
  adb -s "$SERIAL" shell "$CMD swap-target $BOND $REMOTE/key25.original $REMOTE/key25.proposed $PREV_SHA" > "$SESSION/receipt-write.txt"
  grep -q 'READBACK_MATCH=true' "$SESSION/receipt-write.txt"
fi
adb -s "$SERIAL" shell "$CMD inspect-target $BOND" > "$SESSION/receipt-final.txt"
grep -q "SHA256=$NEXT_SHA" "$SESSION/receipt-final.txt"
CHANGED=false
trap - EXIT
echo "SETTINGS_BOOTSTRAP_OK=true"
echo "PROPOSED_SHA256=$NEXT_SHA"
echo "ROLLBACK_SNAPSHOT=$SESSION"
echo 'Requires actual Android Settings UI regression before claiming product acceptance.'
