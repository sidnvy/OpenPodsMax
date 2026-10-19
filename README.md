# OpenPodsMax

<img src="app/src/main/res/drawable-nodpi/openpodsmax_icon.png" alt="OpenPodsMax icon" width="96">

AirPods Max 2 in Android’s **Connected devices**, with an **Apple-inspired settings page**.

[Download APK](https://github.com/sidnvy/OpenPodsMax/releases/tag/v0.2.1) · [Get started](#get-started)

<table>
  <tr>
    <th>Android device details</th>
    <th>Dedicated settings</th>
  </tr>
  <tr>
    <td><img src="docs/images/pixel-system-controls.png" alt="AirPods controls inside Android's native Bluetooth device details page" width="300"></td>
    <td><img src="docs/images/pixel-playback.png" alt="Apple-inspired settings with audio and automatic playback controls" width="300"></td>
  </tr>
  <tr>
    <td>Connected devices → AirPods Max → gear</td>
    <td>OpenPodsMax → audio and playback settings</td>
  </tr>
</table>

Real Pixel 10 Pro XL captures: system page after registration (left), dedicated app (right). **System integration is experimental:** separate ADB registration is required and can be reset by Bluetooth cycles or system updates. Installing the APK alone enables the dedicated app. [Screenshot details](docs/images/README.md).

## Supported controls

| Feature | Current support |
| --- | --- |
| System device details | Listening modes and supported settings in Android Settings after experimental registration |
| Dedicated settings | Apple-inspired grouped interface with additional Android playback controls |
| Battery | Headset-reported charge in the app when available |
| Listening modes | Off, Transparency, Adaptive and Noise Cancellation |
| Headset settings | Supported controls include Personalized Volume and Conversation Awareness; headset readback confirms changes |
| Removal pause and wear resume | Optional same-player playback control; manual pauses stay paused |
| Two-phone use | Manual switching through Android Bluetooth settings; the inactive phone does not continually reclaim the headset |

Simultaneous multipoint and Apple-style automatic device switching are **not implemented**.

## Compatibility and limits

Hardware acceptance testing used **AirPods Max 2**, **Pixel 10 Pro XL** and **Motorola Edge 70**, with both phones running Android API 37. Removal pause, wear resume, prevention of unwanted resume after manual pause, lock-screen playback controls and manual switching in both directions were tested.

- Other AirPods models, phones and Android versions are not validated. The minimum SDK of 32 is a build constraint, not a compatibility guarantee.
- Adaptive Audio strength persistence is unverified. Settings the headset does not report remain unavailable.
- Deep Doze, long-duration reliability, arbitrary media players and calls are not comprehensively tested.
- System Settings registration can be overwritten by Bluetooth cycles or system updates. Installing the app alone does not enable it, and an ordinary APK cannot restore it automatically. Battery in Android's connected-device list is not implemented.

## Get started

1. Download `OpenPodsMax-0.2.1.apk` from [GitHub Releases](https://github.com/sidnvy/OpenPodsMax/releases/tag/v0.2.1) and open it on your phone. Android may ask you to allow installation from your browser or file manager.
2. Pair and connect the headset in Android's Bluetooth settings.
3. Open OpenPodsMax, grant Bluetooth access, and explicitly approve the selected bonded Max 2.
4. Wait for headset state before changing listening modes or settings.
5. If desired, enable **Pause when removed** and **Resume when worn**. Resume needs notification access to locate the active media session; Android may list the permission under the app name.

To move to another phone, connect from that phone's system Bluetooth page. Ordinary app use does not require root, Shizuku or ADB metadata registration.

Existing private/debug builds use a different signing certificate and cannot be updated with the release APK. To migrate, uninstall the old app first (this resets app settings and permissions), then install the release and configure it again. Future official releases will use the same release signing key.

### Permissions and privacy

| Permission or service | Purpose |
| --- | --- |
| Bluetooth access | Communicate with the selected, paired headset |
| Notification access, optional | Locate media sessions for same-player resume; the implementation does not read or store notification contents |
| Foreground service | Keep enabled playback features running in the background |
| Bluetooth scanning and display over other apps, optional | Nearby pairing popup |

Microphone recording is not used. When reporting a problem, remove device addresses, personal names and notification content from logs or screenshots.

## Build

Use **JDK 17**, **Android SDK Platform 36** and **Build Tools 36.0.0**. Set `ANDROID_HOME` to your SDK directory, or use an untracked `local.properties` file.

```sh
git clone https://github.com/sidnvy/OpenPodsMax.git
cd OpenPodsMax
./gradlew assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. With your intended phone connected through ADB:

```sh
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

An existing installation signed with a different key cannot be updated in place. Keep a consistent local development key for subsequent builds.

`./gradlew assembleRelease` produces an **unsigned** release APK. Official APKs are signed separately with an offline release key. See [release verification](docs/RELEASING.md); never commit signing keys or passwords. A debug build is not an official production release.

Dependencies require internet on the first build; subsequent builds can use `--offline` when cached. Gradle distribution/wrapper hashes are pinned against the [official checksum reference](https://gradle.org/release-checksums/).

Public builds use basic vector artwork and a static pairing fallback. Product renders and animations used in private testing are not distributed in this repository.

## Development

Install a JDK and `ripgrep`, then run the offline checks:

```sh
bash tests/run_max2_jvm.sh
python3 tests/test_settings_bootstrap_editor.py
python3 tests/test_bootstrap_wrapper.py
python3 tests/portability_static_contract.py
python3 scripts/check_public_tree.py
```

[Source checks](https://github.com/sidnvy/OpenPodsMax/actions/workflows/checks.yml) runs credential scanning, public-tree checks, regressions, builds and lint. Its status does not replace hardware acceptance testing.

Android instrumentation and metadata helpers are development tools. Inspect their scope before use, snapshot the precise target and verify restoration support before metadata writes. The included artwork registration helper cannot exactly restore an original null value.

## Help and contributions

[Open an issue](https://github.com/sidnvy/OpenPodsMax/issues) with your phone model, Android version, headset firmware, app commit and steps to reproduce. State what you expected and what happened. Include only sanitized logs or screenshots; do not post credentials or signing material.

See [Contributing](CONTRIBUTING.md) for validation expectations and [Security](SECURITY.md) for reporting security concerns.

## License and acknowledgments

Licensed under [GPLv3](LICENSE). See [third-party notices](NOTICE.md) for CAPod and LSPosed attribution and applicable licenses.

OpenPodsMax is a community project, not affiliated with Apple or Google. AirPods is an Apple trademark. The repository began from one reviewed source snapshot; private development history and raw device evidence are not included.
