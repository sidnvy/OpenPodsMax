# Compatibility and limits

Hardware acceptance testing used **AirPods Max 2**, **Pixel 10 Pro XL** and **Motorola Edge 70**, with both phones running Android API 37. Removal pause, wear resume, prevention of unwanted resume after manual pause, lock-screen playback controls and manual switching in both directions were tested.

- Other AirPods models, phones and Android versions are not validated. The minimum SDK of 32 is a build constraint, not a compatibility guarantee.
- Adaptive Audio strength persistence is unverified. Settings the headset does not report remain unavailable.
- Deep Doze, long-duration reliability, arbitrary media players and calls are not comprehensively tested.
- System Settings registration can be overwritten by Bluetooth cycles or system updates. Installing the app alone does not enable it, and an ordinary APK cannot restore it automatically. Battery in Android's connected-device list is not implemented.

## System Settings integration

The native device-details screenshot shows the result after experimental ADB metadata registration. Installing the APK alone does not enable this integration. Bluetooth cycles or system updates can overwrite the entry, and the ordinary APK cannot repair it automatically. The included [bootstrap helper](../scripts/bootstrap_settings_for_bond.sh) is restricted to Moto Edge 70 on API 37; it is development tooling, not a universal installer. Review its target checks and snapshot/rollback behavior before use. The artwork registration helper cannot exactly restore an original null value.

Simultaneous multipoint and Apple-style automatic switching are not implemented. Switch phones manually using the destination phone's Bluetooth settings.

## Installing over an existing version

Official 0.2.0 and 0.2.1 installations can update directly to 0.2.2 with the same release signing key. Private/debug builds use a different certificate and require uninstalling first, resetting app data and permissions. See [release verification](RELEASING.md).

## Permissions and privacy

| Permission or service | Purpose |
| --- | --- |
| Bluetooth access | Communicate with the selected, paired headset |
| Notification access, optional | Locate media sessions for same-player resume; the implementation does not read or store notification contents |
| Foreground service | Keep enabled playback features running in the background |
| Bluetooth scanning and display over other apps, optional | Nearby pairing popup |
| Android companion-device association, optional | Let this app control the selected headset's codec without a privileged grant; Android presents its own approval dialog |
| Receive boot completed | Attempt to restore explicitly enabled automatic SBC for the still-approved, paired headset; no Bluetooth connection is initiated |

Microphone recording is not used. When reporting a problem, remove device addresses, personal names and notification content from logs or screenshots.

## Automatic SBC

The 0.2.4 development build adds **Android Playback → Use SBC automatically**. It is off by default and saved per owner-approved Bluetooth bond. Enable it for a headset whose AAC playback is unreliable; on Android 13+ approve Android's device-link dialog once. No Samsung or cloud account, root, Shizuku, signature permission grant or ongoing nearby scan is used.

The existing playback foreground service observes A2DP connections. For the selected, approved, already-connected headset only, it requests SBC and reads the actual codec back. A short audio interruption can occur when switching. It never connects a disconnected headset or changes another headset. Retries are limited to four writes per connection; unsupported phones show a failure status rather than a success claim.

Disabling the switch cancels pending codec changes. It does not force AAC into a currently playing session; reconnect or select AAC manually if desired. The playback notification's **Stop** action disables both automatic SBC and removal pause. The Android companion-device link remains until removed in Android Settings or the app is uninstalled.

The preference survives app replacement. A boot/update receiver attempts to restore the opted-in service for a still-paired headset, but OEM background restrictions and Bluetooth readiness can require opening the app again. Reboot recovery, deep Doze and other OEMs remain unverified. A saved preference is not a permanent change to the phone's codec negotiation defaults; this feature reapplies SBC after each connection.

### Hardware check — 2026-11-12

On Galaxy Z Fold8 / SM-F9710, One UI 9 / Android API 37, with AirPods Max 2:

- The native HD audio switch disabled optional codecs for the current connection, but the phone restored AAC and optional codecs on reconnection.
- With automatic SBC enabled, a real disconnect/reconnect while Android Settings was foreground produced an app-side SBC request and system readback of SBC, 44.1 kHz / 16-bit / stereo.
- With the app switch off, a real reconnect used AAC again. Re-enabling the switch requested and confirmed SBC.
- The release-certificate update preserved existing app settings and permissions. No privileged permission was granted; the app used Android's companion-device association.

These checks establish codec automation and its off switch, not the cause of the AAC distortion. Listening quality, boot recovery, forced-stop recovery and full AAP feature acceptance on this Samsung were not tested in this session.
