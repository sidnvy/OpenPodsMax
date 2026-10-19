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

Official 0.2.0 installations can update directly to 0.2.1 with the same release signing key. Private/debug builds use a different certificate and require uninstalling first, resetting app data and permissions. See [release verification](RELEASING.md).

## Permissions and privacy

| Permission or service | Purpose |
| --- | --- |
| Bluetooth access | Communicate with the selected, paired headset |
| Notification access, optional | Locate media sessions for same-player resume; the implementation does not read or store notification contents |
| Foreground service | Keep enabled playback features running in the background |
| Bluetooth scanning and display over other apps, optional | Nearby pairing popup |

Microphone recording is not used. When reporting a problem, remove device addresses, personal names and notification content from logs or screenshots.
