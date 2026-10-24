# Moto Edge 70 / Android 17: toggle readback UI continuity

Issue: https://github.com/sidnvy/OpenPodsMax/issues/2

## Baseline verified on Moto (2026-10-24, installed 0.2.2 + lifecycle fix)

- 11:05:54.903 — Conversation Awareness write accepted (control ID 40, 1 -> 2)
- 11:05:56.105 — independent readback requested, auxiliary AAP socket closed
- 11:05:56.108 — reader disconnected, all observed device state cleared
- 11:05:57.164 — AAP socket reconnected
- 11:05:57.199 — independent device readback confirmed value 2
- Restored initial value 1; independent device readback confirmed at 11:06:16.264

Observed cause: Compose polls the controller every 450 ms. The forced control-channel
reconnect clears the entire settings map, producing a transient unknown state for
unrelated toggles. A separate risk is calling synchronous socket write/flush on the UI thread.

## Scope

- Keep the last verified visual settings snapshot for at most 3 seconds after
  a planned same-generation AAP readback reconnect, only while the Bluetooth
  audio route to that bond still exists.
- Do not treat cached presentation values as authorization to write settings.
  Controller session and verified-readback guards remain the source of truth.
- Show Checking headset for a setting awaiting readback, without displaying
  an unconfirmed desired switch position.
- Dispatch boolean setting writes off the Compose main/UI thread.
- Genuine disconnect, route loss, timeout or device switch must invalidate
  stale presentation state as before.

## Acceptance

1. JVM regression tests validate the bounded presentation window and failure closure.
2. Run GitHub Actions Android build, public-file checks and Gitleaks.
3. Sign unsigned CI build locally with matching OpenPodsMax release key, install via
   adb install -r, preserve existing Moto settings and original signed APK.
4. On Moto, toggle Conversation Awareness and Personalized Volume several times.
   Verify unrelated controls remain visually stable during AAP reconnect and target
   values change only after genuine readback.
5. Confirm no unintended value change or subsequent write on an actual Bluetooth
   disconnect. Never unlock bootloader, delete app data or change system settings.
