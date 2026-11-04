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
