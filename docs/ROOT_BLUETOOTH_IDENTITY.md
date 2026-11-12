# Apple Bluetooth identity on rooted phones

Development version 0.2.9 / code 12 generalizes the Moto identity experiment. It keeps the existing native AAP handoff response and combines it with optional per-headset SBC. It is not a universal multipoint compatibility claim.

## Switch and setup

Open **About → Phone Bluetooth · Root → Apple Bluetooth identity** after approving the paired AirPods. The switch is off by default. Without an executable root client it is disabled, with a root requirement shown. Grant access through the installed root manager when requested; the app does not provide root or install a root framework.

Both enable and restore ask before restarting Bluetooth. The identity is global to this phone and the restart briefly disconnects all Bluetooth devices. Pause playback and end calls first. The app checks Android audio mode and refuses the transaction during playback, a call, or an unknown/non-on Bluetooth state. It never removes bonds, initiates pairing, changes volume, flashes a partition, installs a module, disables SELinux, or reboots the phone.

The runtime backend detects the Device ID property names in libraries loaded by the current Bluetooth process. Phones that do not expose this backend are refused without changing their properties. This removes the fixed Moto model/kernel gate while retaining a real implementation check. Root alone is insufficient, and finding these property names is not proof of the on-wire DID or successful handoff.

For supported stacks the requested identity is Bluetooth SIG Vendor ID `0x004C` and vendor-ID source `1`. Product fields and the Bluetooth MAC address are unchanged. These are non-persistent runtime properties. While the switch remains enabled the app checks/reapplies the change on a subsequent app foreground, subject to the same playback and call checks. It does not restart Bluetooth from a boot receiver. The control service may recover after boot, but that is separate from property reapplication.

Compatible peers may cache identity when pairing. The app will not clear or replace an existing bond to force refresh. The prior Moto/Linux result depended on setup and retained bonds; cached warm-reboot success did not guarantee cold reconnection. Phone-to-phone switching and the generalized Fold identity change have not been hardware-accepted in this implementation.

## Turning it off and recovery

Before the first write the app synchronously saves the exact original vendor/source strings and this boot's identity in private app preferences. An initially unset value is restored as empty, preserving Android's default lookup rather than guessing an OEM vendor number. The root transaction rechecks that preimage and boot before writing. Both fields are restored on normal transaction failure; the original on Bluetooth state is recovered where possible. A per-app root transaction lock prevents overlapping writes across process restarts. Unknown or still-active lock contents are not forcibly removed.

Turning the switch off cancels future application immediately and attempts to restore only the values saved by this switch. If access is denied, a call/playback is active, the operation times out, or another tool changed the identity, the recovery information is retained and the screen offers **Restore original identity**. An off preference with restoration pending must not be represented as verified original on-wire identity. Regrant root or pause playback, then use that action. A new boot invalidates the old runtime snapshot, so it is not replayed onto a different boot or phone.

If another tool had already configured the Apple vendor/source pair, the app does not claim ownership of that tool's original values. It explains that turning this switch off leaves the external setting unchanged. The old Moto-only opt-in is retained in app data but does not silently opt users into this new global setting; enable the new switch deliberately.

The playback notification's Stop action stops its root handoff control opt-in and future property application. It does not run a root Bluetooth restart from the notification. If the switch previously made a change, open the app and restore the saved identity. Existing AirPods bonds may retain cached identity even after a local restore; restoring property strings is not proof of clearing headset-side caches.

## Verification and limits

The production shell is exercised against a fake Android in 22 offline tests: successful enable/restore, unset originals, source namespace restore, no-op, root denial, invalid input, boot/preimage conflicts, unsupported stack, Bluetooth off, call guard, partial property writes, failed disable/enable, external concurrent changes, termination, failed rollback, active/abandoned transaction locks and preservation of unknown lock contents. These tests never call ADB or control a real radio. Existing native handoff, playback, SBC and recovery regressions remain required.

A local success means the property values and Bluetooth ON state were read back. Confirm the advertised DID independently from another device and test audible handoff in both directions before recording hardware acceptance. Native handoff and its limitations were previously tested on the specific Moto/Linux setup; the generalized switch does not certify every rooted phone, firmware, headset, call scenario, suspend/resume or cold reconnect. It does not implement Apple Account/iCloud coordination.

No device addresses, bonds, keys or raw root output are uploaded. Root result files are temporary private cache files and are deleted after parsing; only a numeric transaction result is logged. Existing automatic SBC is independent of this setting and requires its existing per-headset opt-in.
