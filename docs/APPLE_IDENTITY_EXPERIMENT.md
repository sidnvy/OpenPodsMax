# AirPods Max 2: Linux Apple identity with an unrooted Moto

Historical unrooted result: **tested, no stable two-host retention achieved**. A later owner-authorized temporary-root experiment succeeded in changing the Moto identity and achieved bidirectional audible handoff; see the follow-up below. The owner chose LibrePods-style identity emulation instead of same-network coordination and required the Moto to remain unrooted. No LAN coordinator or automatic reclaim service is running.

## What was tested

Devices: AirPods Max 2, Moto Edge 70 on Android API 37, Linux BlueZ 5.87. The Moto's Bluetooth identity and bond were not changed. Its bootloader is locked, verified boot is green, and no `su` command was present. The actual Bluetooth APEX is mounted read-only and its DID configuration uses Motorola vendor ID `0x001D`.

Linux was temporarily configured with `DeviceID = bluetooth:004C:0000:0000`. BlueZ reported `bluetooth:v004Cp0000d0000`, and an AirPods SDP query received vendor `0x004C` in the capture. This verifies the Linux identity change; it does not make the Moto advertise Apple identity.

A first connection attempt failed at radio paging, before any AAP negotiation. Other trials accepted the AAP handshake. One Linux-only initialized session lasted its full 30-second observation window. That is not proof of an identity-caused reliability improvement: the earlier non-emulated baseline also supported a control session when Linux already owned the link.

The owner approved re-pairing **only Linux's** exact headset bond because upstream documents identity caching. The initial pairing occurred with `Pairable: no` and did not persist a bond. That attempt was not accepted as completed pairing. The retry temporarily enabled bonding, then confirmed `Paired: yes`, `Bonded: yes`, `Trusted: yes`, a stored classic link key and persistence across a Bluetooth restart. Pairability was returned to its original off value. The old exact-device pairing record was retained privately; no key contents were printed or copied into the public tree.

## Ordered retention result

The final trial used the persistently re-paired Apple-identity Linux host and the unmodified Moto. It used the core upstream initialization order without proximity-key requests.

- Linux handshake acknowledged at trial time 21.953 s.
- Only after that channel survived another three seconds did the driver initiate Moto connect at 24.983 s.
- The phone's API request returned successful initiation at 25.648 s.
- Linux's AAP channel ended with peer reset at 26.693 s, approximately 1.045 s after the phone request returned.

Those are diagnostic timestamps, not audible switching latency. This trial did not demonstrate concurrent audio endpoints or seamless audible switching. Earlier overlapping-connect trials and reported host counts were not treated as multipoint acceptance. One decoded report listed both phone and Linux before Linux's flags changed and its connection reset; the exact semantics of those status bytes remain unverified.

## Android constraint

The inspected [LibrePods source](https://github.com/librepods-org/librepods/tree/89356c08cea3db0b6b54792d9329d09a87cc85fa) injects into Android's Bluetooth process and hooks `BTA_DmSetLocalDiRecord`, setting vendor `0x004C` and vendor-ID source `0x0001`. Its maintainer explains that both participating Android hosts need the Apple-device setting and root in the [multipoint discussion](https://github.com/librepods-org/librepods/discussions/400). Android's newer ordinary-app L2CAP/profile-connect support does not itself supply this identity hook.

No supported non-root method to change the Moto's actual Device ID record was established. A standalone app or shell/Shizuku process loading its own native library is not equivalent to executing a hook inside the Bluetooth process. No rooting, bootloader unlock, APEX write/remount, injection, key extraction or protection bypass was attempted.

This result rules out claiming success for the tested Linux-only emulation path. It does not prove that every possible rootless interoperability method is impossible. Further work must respect the owner's no-root constraint and must not quietly revive the rejected LAN architecture.

## Cleanup and retained tools

The exact original Linux configuration was restored byte for byte. Pairing keys changed only for the approved Linux headset bond; the laptop bond was refreshed under the original identity to clear the emulation cache. Persistent bonding and a normal Linux A2DP endpoint were verified afterward; audio ownership was then returned to the Moto. The official same-key OpenPodsMax 0.2.3 APK was restored on the Moto without uninstalling or clearing data. The abandoned LAN test services were stopped, its token revoked, and its unused app-companion association removed without removing Moto Bluetooth pairing. Its unfinished source remains isolated in its prior managed worktree.

[Tools and guarded rollback](../tools/apple-identity/README.md). Raw HCI/device traces and exact hardware identifiers remain private under ignored state directories. No claim of audible automatic handoff acceptance is made.

## Shizuku assessment on this Moto

A read-only probe ran under the Moto's ADB shell UID 2000, the same identity used by Shizuku's non-root backend. It inspected the installed `IBluetooth`, `BluetoothAdapter` and `BluetoothDevice` methods. Their SDP operations exposed lookup/search, not a local Device ID setter. `SdpDipRecord` exists as a result/value class with vendor getters; no `IBluetoothSdp` interface was available in this probe. Vendor-specific HCI command methods exist, but they are not an established host SDP Device ID setter and no raw controller command was sent.

This supports using Shizuku for operations that shell already has authority to perform, such as supported profile control and some Android Bluetooth metadata writes. It does not establish a Shizuku route for changing the Moto's on-wire host identity. A UserService can execute Java/JNI as shell, but that is a different process from Android's Bluetooth process. It does not by itself install LibrePods' native hook there.

See [Shizuku's documented ADB/root privilege differences](https://github.com/RikkaApps/Shizuku-API#differents-of-the-privilege-betweent-adb-and-root). The [non-root startup instructions](https://shizuku.rikka.app/guide/setup/) also require restarting Shizuku after reboot. Shizuku itself was not started or added to the released app in this experiment; the equivalent privilege level was tested directly over USB.

The retained `IdentitySurfaceProbe.java` only lists relevant class/method availability and its UID. It does not invoke the setters, search nearby devices, change properties, or attempt injection. Build it against Platform 36, convert with d8, and run as a temporary `app_process` shell helper following the existing development-helper pattern. Remove the temporary device JAR after inspection.

## 2026-11-05 follow-up: temporary root and native handoff

The owner later authorized a temporary-root attempt and replacing only this AirPods bond on both hosts. Official GhostLock CI run `41533197137` matched inspected source `b88b70c77918f7226d899f33976533fa639b264f` and the phone's exact kernel profile. One invocation obtained temporary KernelSU root; independent `su -c id` verified UID 0. SELinux was Enforcing afterward, the bootloader remained locked and Verified Boot remained green. No boot image was installed or partition flashed. This is device/build-specific evidence, not a universal root procedure.

Overriding the old APEX `bt_did.conf` in the root mount view did not change the Bluetooth process's view or its on-wire DID. That trial mount was removed. The effective input on this build was the existing non-persistent `bluetooth.device_id.vendor_id` property. Changing it to `0x004C` and cycling Bluetooth changed an independent external SDP query from vendor `0x001D` to `0x004C`, with vendor-ID source `1` and other returned DID fields unchanged. No Xposed hook was needed.

After fresh bonds under both Apple identities, Moto A2DP/HFP and Linux A2DP/AAP retained their links. The owner heard Linux audio, then Moto music after a phone playback action. OwnsConnection=true alone failed to take audio back from the playing phone. The complete MediaInformation/Hijackv2 request, forwarded through the headphones, and a phone OwnsConnection=false response plus pausing the current headset-routed stream completed the reverse transfer.

The app now handles that known forwarded release request on a separate worker, guarded by the same approved bond, current AAP generation, selected headset media route and normal audio mode. It clears pending app-owned wear resume so rewear does not undo a handoff. The initial integrated candidate replied without pausing music and failed the audible test; the corrected same-key investigation APK added guarded PAUSE and passed the owner's final **both directions, ending on Moto music** check. The separate diagnostic phone receiver was stopped and removed, and the app's foreground wear service was independently verified active.

[Linux diagnostic adapter and limitations](../tools/native-handoff/README.md). In this session it was deployed as a running user service without login autostart. Phone temporary root and the runtime property do not survive a full reboot. Call priority, suspend/resume, boot recovery and long-duration stability are **not hardware-accepted**. This is an experimental installed test, not a new public release or a rootless solution. Raw kernel, bond and device traces remain private outside the public tree.

## Follow-up acceptance

The integrated Linux daemon replaces the diagnostic preload adapter. During a
subsequent full Moto reboot, the existing bonds remained intact, temporary root
was gone, and external SDP reported the original vendor 0x001D. The owner still
heard both directions of native handoff without re-pairing or reapplying the
identity. This observation supersedes the assumption that root must be reapplied
after every reboot for these existing bonds. It does not establish rootless
initial setup or behavior after re-pairing. See [Linux acceptance limits](../linux/README.md#acceptance-limits).
