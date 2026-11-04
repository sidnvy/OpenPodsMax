# Moto Edge 70 device reliability investigation — 2026-11-04

## Findings

- The original installed 0.2.2 build had an active sticky connected-device foreground service. Notification permission was denied, so an ongoing service did not imply a visible notification in the notification shade. The initial physical removal/rewear test passed, with a wear event and targeted media pause request. This does not resolve the reported intermittent long-duration failure.
- During a Smart Case wake test the same headset connected to the Linux laptop, while the Moto A2DP connection policy was already ALLOWED (100). OpenPodsMax deliberately opens its AAP control connection only after the selected headset has a phone audio endpoint. It does not implement cross-device automatic switching or continually reclaim the headset from another host. The laptop link was disconnected without removing pairing, and the Moto was reconnected for testing.
- System device artwork metadata key 5 was null and provider metadata key 25 pointed to Google Play services. The guarded repository bootstrap restored the native controls, verified in Android Settings after refreshing its cached page. A later connection cycle returned key 25 to Google's provider again. An APK-only boot receiver cannot repair privileged Bluetooth metadata; a one-time ADB repair is temporary.
- With explicit user approval of the null-restoration limitation, the existing device-specific persistent artwork helper registered the companion's image URI in key 5. It verified key 25 was unchanged by that operation. No pairing was removed.
- Loud Sound Reduction was explicitly disabled in the source. The separate headset Ear Detection setting is writable only if actually reported by the device; it is distinct from local Android pause/resume based on wear events. Unsupported or unreported controls must not be presented as working features.

## Changes

- Dispatch remaining Compose listening-mode, adaptive, accessibility, head-detection and tone-volume writes on the IO dispatcher.
- Release the controller monitor before socket writes, retain serialized writes, reject completion on a retired socket, and close a stalled command/initialization socket after three seconds. Preserve four separate L2CAP initialization packets; combining those packets was caught during device testing and corrected before final acceptance.
- Restore an existing wear-pause opt-in after package replacement, boot, Bluetooth ON and an ACL connection from the exact selected bond. Never turn the preference on automatically; transient Android launch restrictions leave the preference intact. The existing service retains identity, route and session-generation guards.
- Explain the distinction between headset sensor settings and Android playback controls. Show Loud Sound Reduction as unavailable rather than an inert switch.
- Preserve the unavailable Crown/button-cycle UI removal already present in the installed private build, instead of reintroducing those controls from main.

## Verification and limits

- Offline JVM suite covers packet validation, connection ownership, stale readers, mode/setting confirmation, wear policies, readback presentation and timeouts.
- New blocked-write test verifies that stop remains responsive, a retired socket cannot report a successful write, initialization packets retain their boundaries, and a stalled initialization is closed by the watchdog.
- Existing handshake waits of two seconds could race the controller's two-second local-route polling interval. Those waits now allow four seconds; production timing is unchanged.
- Metadata editor, fake-ADB bootstrap (including exact-preimage rollback and unsupported-OEM rejection), portability checks, public-tree checks, and Android build/lint are required for this candidate.
- Device update uses the original release certificate on the existing signing host, followed by an in-place install. No uninstall or app-data clearing.
- Package-replacement recovery was observed: WEAR_RECOVERY_REQUESTED followed by an active sticky foreground service. The phone denies shell-generated BOOT_COMPLETED broadcasts. Actual reboot acceptance and long-duration wear reliability remain separate, unproven gates.
- Native Settings persistence across Bluetooth cycles/reboot is not solved by these APK changes. A durable solution needs a separately designed privileged integration, including its availability after reboot. Do not claim permanent registration from an immediate successful ADB readback.

## Follow-up: wear tracing and planned-reconnect backoff

- A corrected-build physical test still failed while the service was foreground and the phone was awake. Raw full-removal and reinsertion events arrived, but there was no pause-request or guard-rejection log. This individual failure's cause remains unproven.
- Added address-free logs for accepted/rejected wear delivery, main-thread dispatch latency, debounce scheduling and debounce consumption. The diagnostic build then passed two user-confirmed pause/resume cycles, with corresponding targeted pause and resume requests. Logging alone must not be described as a proven playback fix.
- A reversible Conversation Awareness roundtrip was independently read back and restored to its original enabled value. The first deliberate AAP readback reconnect waited 1000 ms; the second waited 2000 ms. Source inspection confirms each planned readback was incorrectly consuming the genuine-failure exponential backoff, eventually creating 30-second control/wear-reporting gaps after rapid setting changes.
- Planned readback socket closures now use a fixed one-second reconnect delay without incrementing or clearing genuine failure history. Actual socket failures retain the existing backoff and audio-ownership gate. The marker is scoped to the precise socket and cleared on selection, stop, route loss and reader completion.
- A new controller regression performs three consecutive setting readbacks, verifies that the delay does not grow, and confirms that a subsequent genuine disconnect still increments failure history.

## 2026-11-05 hardware acceptance and silent-resume diagnosis

- Installed the reconnect-backoff fix with the original signing certificate after explicit approval of this investigation's test builds. Two Conversation Awareness off/on roundtrips completed: all four planned reconnects stayed at 1000 ms, all values were independently read back, and the original enabled value was restored. The user confirmed pause/resume still worked afterward.
- Backgrounded OpenPodsMax, deliberately crashed only its process (not force-stopped), and observed automatic sticky-service recovery, AAP reconnection, and a fresh worn baseline without reopening the app. This is a process-recovery PASS, not proof of long-duration reliability or reboot acceptance.
- In the next test, pause worked and the player resumed but sound was absent. The active YouTube Music AudioTrack was routed to Bluetooth A2DP, with normal stream volume and `mutedState:opControlAudio`. AudioHardening logged a full background-playback mute for YouTube Music. Its CONTROL_AUDIO app-op remained at the default `foreground` mode.
- The user brought YouTube Music to the foreground and sound immediately returned. The next audio snapshot showed `mutedState:none`; a subsequent Home screen did not immediately re-mute the newly authorized playback. This confirms system background-audio hardening for this silent-resume occurrence, rather than treating PLAYING as proof of audible output.
- Android's official guidance explains that `level: full` denotes a foreground service lacking while-in-use capability. This is a player lifecycle requirement on API 37. See [Background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio). No audio-hardening or app-op setting was changed.
- Some observed removal motions changed only one cup. The current conservative sensor policy requires both cups removed; it intentionally does not pause on a one-cup transition. This is distinct from an app lifecycle failure. Sensor-policy changes require separate device acceptance; none were made here.
- The earlier raw-wear-without-pause occurrence remains unexplained. Continue to distinguish a missing sensor event, an intentionally ignored partial transition, delayed debounce processing, failed pause guards, and player/system muting. Do not describe this task as resolving every intermittent failure.

## Bluetooth recovery receiver delivery

The recovery receiver now exports its protected broadcast entry point with the receiver-level `BLUETOOTH_PRIVILEGED` sender permission. Bluetooth ACL events originate in the privileged Bluetooth app UID, which cannot deliver to an unexported receiver. This grants no privileged permission to OpenPodsMax. Boot/package recovery, exact selected bond filtering, existing opt-in and Bluetooth runtime permission checks remain in place. JVM tests execute the production receiver branches across API 32–37; a manifest contract verifies the export, sender permission and action whitelist. Cross-UID delivery still requires real-device acceptance.

References: [Bluetooth ACL sender](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/android/app/src/com/android/bluetooth/btservice/RemoteDevices.java), [manifest receiver delivery checks](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/BroadcastSkipPolicy.java), [component permission enforcement](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityManager.java).


## 2026-11-05 actual boot and either-cup follow-up

- The user rebooted/unlocked the version-6 candidate without opening OpenPodsMax. Its BOOT_COMPLETED broadcast was queued for97.778seconds, then delivered and processed in8ms. A later ACL_CONNECTED broadcast from BluetoothUID1002 was delivered in53ms and processed in6ms. The opted-in foreground service and live wear/ANC events recovered automatically. This validates receiver delivery; Android broadcast scheduling can delay recovery. No particular other app is identified as causing the entire queue wait.
- The user chose pause when either cup is lifted. The release now arms a500ms pause after a fresh fully worn baseline on any valid off-cup shape, retaining deadlines across asymmetric/duplicate reports. Resume still requires both cups worn; route, identity, session, audio mode and same-player protections are unchanged. The recorded asymmetric removal is covered by regression; final on-device gesture acceptance remains separate.
