#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$BASE/../.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT}}"
OUT="$ROOT/state/p0-shell-metadata"
mkdir -p "$OUT/classes" "$OUT/dex"
javac --release 17 -cp "$SDK/platforms/android-36/android.jar" \
  -d "$OUT/classes" "$BASE/MetadataProbe.java"
"$SDK/build-tools/36.0.0/d8" --min-api 31 \
  --lib "$SDK/platforms/android-36/android.jar" \
  --output "$OUT/dex" "$OUT/classes/dev/airpodsnative/bootstrap/MetadataProbe.class"
python3 - "$OUT/metadata-probe.jar" "$OUT/dex/classes.dex" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1],"w") as z:
    z.write(sys.argv[2],"classes.dex",compress_type=zipfile.ZIP_DEFLATED)
PY
echo "$OUT/metadata-probe.jar"
