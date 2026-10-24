# Moto Edge 70 background wear/playback investigation

## Baseline (2026-10-24)

- Public 0.2.2 source, commit 50b2b04, matches Moto installed versionCode 5.
- Moto Edge 70 Android 17 / API 37, authorized wireless ADB.
- WearPauseService alive as foreground service for about ten hours.
- Bluetooth CONNECT granted; notification listener access enabled.
- POST_NOTIFICATIONS denied; user battery whitelist includes the app.
- No active AirPods audio route at snapshot time.
- Installed service START_NOT_STICKY and stopIfKilled=true: confirmed restart gap, but no evidence yet this caused the intermittent issue.

## Fixes under investigation

- Return START_STICKY after successful foreground service starts. Explicit STOP and invalid settings remain non-sticky.
- Transient service-start failure must not erase opt-in preference.
- Record session transitions and failed pause-guard boolean fields without recording Bluetooth addresses, track names or raw events.
- Preserve the same-player resume and media-route safety guards.

## Moto hardware acceptance

1. Confirm selected AirPods Max 2, live AAP session and media output to this headset.
2. Play music from a single local media session; remove and rewear the headset multiple times both unlocked and locked.
3. Match WEAR_STATUS_OBSERVED with WEAR_PAUSE_REQUESTED / WEAR_PAUSE_SKIPPED_PRECONDITION and, where enabled, WEAR_RESUME_REQUESTED.
4. Repeat after reconnect, sleep/wake, and simulated OS process reclaim. Force-stop must be treated separately and never expected to auto-recover.
5. When events are lost, differentiate no AAP event, AAP connection loss, route guard fail, competing session, and command not acknowledged. Do not relax guards without evidence.

Suggested log filter: adb -s DEVICE_SERIAL logcat -v time -s AirPodsAAP:I '*:S'

## Safe deployment

- Moto installed 0.2.2 is release-signed, not Android debug-signed.
- Original APK backed up outside Git in state/moto-wear-debug-20261024.
- CI outputs only an unsigned APK on this branch; sign locally on trusted Workstation after verifying release signing certificate fingerprint.
- Reinstall using adb install -r, retaining data and verified package/signature. Restore original same-version signed APK for rollback.
- Do not uninstall or clear app data to bypass a signature mismatch.
