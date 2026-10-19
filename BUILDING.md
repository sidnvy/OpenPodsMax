# Building OpenPodsMax

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
