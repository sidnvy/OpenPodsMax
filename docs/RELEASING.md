# Release verification and maintenance

Official APKs are published only on [GitHub Releases](https://github.com/sidnvy/OpenPodsMax/releases). Version 0.2.2 uses application ID `dev.airpodsnative.companion`, version code 5, and a dedicated RSA-4096 release certificate.

## Verify a download

Download the APK and `SHA256SUMS` from the same release, then run:

```sh
sha256sum --check SHA256SUMS
apksigner verify --verbose --print-certs OpenPodsMax-0.2.2.apk
```

The release certificate SHA-256 fingerprint is:

```text
6fa357bfb05132d45fd003b9f03c9f98385a2cdff0da2cf1e65f6bff9145b293
```

A checksum detects file corruption; the certificate identifies the signing key. APK Signature Scheme v3 is supported by all Android versions targeted by this app (API 32+).

Private/debug APKs use a different certificate. Android will reject an in-place update from those builds. Uninstalling the old app resets its settings and permissions; after installing the release, select your headset and enable desired features again. Future official APKs must use the same release key and an increasing version code.

## Maintainer procedure

1. Update version name/code and release notes. Run the offline tests and release build/lint; confirm CI on the exact source commit.
2. Build from a clean checkout of that commit using JDK 17 and the SDK versions in README. Public assets only. `./gradlew assembleRelease lintRelease` produces an unsigned APK.
3. Sign outside the repository using Android SDK `apksigner`, a protected keystore, and `--ks-pass file:<private-password-file>`. Never put passwords directly into command arguments or commit keystores, password files or local configuration. CI does not receive the release key.
4. Verify the signature, certificate, application ID, version, non-debuggable manifest and ZIP alignment. Test installation, launch and same-key replacement; distinguish these checks from physical-headset acceptance.
5. Publish a tag at the verified commit and attach only the signed APK, `SHA256SUMS`, `SIGNING-CERTIFICATE.txt` and `BUILD-INFO.json`. Include known limitations and the debug-to-release migration note.
6. Download the published asset again and compare its hash with the local signed file.

Keep the keystore and its password in restricted storage outside the checkout. Preserve an encrypted, independently stored backup before moving or retiring the signing workstation. Losing the key prevents ordinary updates to existing installations. No signing material belongs in a release asset or source archive.

The 0.2.1 release adds the launcher icon and retains the release certificate from the initial 0.2.0 package. Existing official 0.2.0 installations can update in place. Public source history was consolidated at the owner’s request; the old release/tag was retired.
