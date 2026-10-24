#!/usr/bin/env bash
# Offline regression only. JVM Android doubles never contact a phone.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d "${TMPDIR:-/tmp}/max2-jvm.XXXXXXXX")
trap 'rm -rf "$OUT"' EXIT
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
mapfile -t STUBS < <(rg --files tests/jvm-stubs -g '*.java')
"$JAVAC" --release 17 -d "$OUT" "${STUBS[@]}" \
 app/src/main/java/dev/airpodsnative/companion/{AapController,AapFrameValidator,ConnectionPolicy,WearPausePolicy,WearResumePolicy}.java \
 tests/{WearResumePolicyTest,WearPausePolicyTest,AapControllerQueryLockTest,AapControllerOwnershipTest,AapControllerRaceTest,AapControllerParsingTest,AapControllerTimeoutTest,AapControllerReadbackPresentationTest,AapFrameValidatorTest,ConnectionPolicyTest}.java
for TEST in WearResumePolicyTest WearPausePolicyTest AapFrameValidatorTest ConnectionPolicyTest AapControllerQueryLockTest AapControllerOwnershipTest AapControllerRaceTest AapControllerParsingTest AapControllerTimeoutTest AapControllerReadbackPresentationTest; do "$JAVA" -cp "$OUT" "$TEST"; done
