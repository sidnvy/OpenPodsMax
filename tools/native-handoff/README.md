# Native AirPods handoff experiment

This is a **diagnostic adapter**, not an installed Linux companion or a release-ready multipoint implementation. It augments a temporary EarPort process with the MediaInformation and Hijackv2 sequence from [LibrePods 89356c08](https://github.com/librepods-org/librepods/blob/89356c08cea3db0b6b54792d9329d09a87cc85fa/android/app/src/main/java/me/kavishdevar/librepods/bluetooth/AACPManager.kt). It does not relay audio or use LAN coordination.

Tested with EarPort revision `1d55f5cf51fa44f3a139e1df4ca2b5a2a6ace436`. Load only in that temporary daemon, with `OPENPODS_HANDOFF_PEER_MAC` set to the exact participating phone address and `OPENPODS_HANDOFF_HEADSET_MAC` set to the exact headset address. No address is stored in source. Its `send` adapter only matches OwnsConnection=true on the explicitly configured, already paired AirPods target. It sends no key requests or persistent headphone settings. Do not run competing Linux AirPods daemons.

```sh
cc -shared -fPIC -Wall -Wextra -Werror -Wno-misleading-indentation \
  -o /tmp/openpodsmax-takeover.so tools/native-handoff/takeover_shim.c \
  -ldl -lpthread -lbluetooth
```

Use isolated EarPort configuration: handoff enabled, automatic wear connect and removal disconnect disabled, no saved headset profile. Do not enable as an autostart service. Unknown call/activity states, suspend/resume, initial playback baselines, reconnection and long-term lifecycle have not been accepted for this adapter. The Moto's app independently rejects yielding during call mode or an unrelated selected output, but this does not constitute end-to-end call acceptance.

The Android app now recognizes the known forwarded `audioRoutingSetOwnershipToFalse=true` response. It rechecks the selected trusted bond, live AAP generation, current media route and normal audio mode, then sends OwnsConnection=false on a separate worker. It clears any app-owned wear-resume intent so rewear does not undo a handoff. It also pauses active phone media only while still routed to that exact headset and in normal audio mode; it never arms automatic resume for this pause. It does not infer a successful transfer from sending the command; require headset AudioSource and human audible confirmation.

Hardware prerequisites in this investigation: a temporary-rooted Moto exposing Apple vendor ID through the non-persistent `bluetooth.device_id.vendor_id` property, Linux BlueZ Apple DeviceID, and freshly paired bonds under those identities. This is not a universal or rootless solution. The experiment's phone root/identity is lost on full reboot; no boot-image installation was performed.

Initial hardware evidence: both media paths retained; owner confirmed Linux→Moto after phone playback, and Moto→Linux after the complete request and diagnostic phone response. The first OwnsConnection-only reverse attempt failed. The first app-integrated candidate replied without pausing phone media and failed. The corrected same-key test APK added route/call-guarded PAUSE; the owner then confirmed both directions, ending on Moto music. Its WearPauseService remained foreground and the temporary phone receiver was stopped/removed. This is limited hardware acceptance for this setup, not long-term or call acceptance.

For this investigation the Linux daemon was deployed as `openpodsmax-native-handoff.service`, started in the user manager, with **login autostart disabled**. Its private environment file holds the exact phone/headset addresses, and its isolated EarPort config retains the settings above. The service has no network listener. `systemctl --user stop openpodsmax-native-handoff.service` stops it; starting it again requires the same reviewed binary/adapter and current identity prerequisites. No timed test tone/player is left running.
