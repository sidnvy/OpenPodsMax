# Linux Quick Toggle integration (experimental)

Controls live inside the existing ii Quickshell right sidebar. There is no
standalone application window. A single EarPort-derived AAP backend provides
battery, observed listening mode, conversation awareness, local earcup policy
and native ownership switching. The frontend does not open Bluetooth sockets.

## Build and install

Dependencies: C11 compiler, Meson/Ninja, GLib/GIO development files, libbluetooth,
Python 3 with PyGObject, and an existing ii Quickshell desktop.

```sh
bash linux/build.sh
python3 linux/tests/test_install.py
python3 linux/tests/test_transport_rearm.py
python3 linux/install.py --shell "$XDG_CONFIG_HOME/quickshell/ii" --peer-env /path/to/private-peer.env
systemctl --user daemon-reload
# Stop the previously running EarPort/handoff backend before this command.
systemctl --user start openpodsmax-linux.service
```

Omit `--shell` to use the usual per-user ii configuration directory. The private
environment file contains only `OPENPODS_HANDOFF_HEADSET_MAC` and
`OPENPODS_HANDOFF_PEER_MAC`, each an unquoted Bluetooth address. No keys are stored
in source. The installer validates it, backs up all overwritten files and prints
the rollback manifest directory. It does not start or enable a service, alter
Bluetooth identity, or change bonds. Repeated installation preserves the same
sidebar injection. Other desktop customizations are retained.

New installations enable wear-based reconnection for the exact selected bond.
Nearby wear observations are confirmed before connecting; scanning stops while
the device is connected. Existing installations retain their explicit policy.

The startup configuration keeps Linux wear-pause disabled; select Either earcup in the panel to enable it. Linux-only
playback policy is separate from the Android setting. No listening settings are
automatically applied from a new profile.

```sh
# Stop the unified daemon before restoring, to prevent restart against removed files.
systemctl --user stop openpodsmax-linux.service
python3 linux/install.py --rollback /path/printed/by/installer
systemctl --user daemon-reload
```

Rollback refuses any managed file changed since installation, before restoring
anything. The original backend can then be started separately. Login autostart is
an explicit deployment choice; this installer leaves it disabled.

## State and controls

The compact header shows one headphone battery, current listening mode and the
reported active audio source. Expand in place for the controls. Notifications and
the lower widget pane return when collapsed. Expanded controls scroll on short
screens. Generic Bluetooth controls keep their normal behavior.

The expanded settings use explicit viewport width with 12px side insets, equal
mode columns, and one column below 320px available width. Flexible labels wrap
instead of widening the panel.

The event-driven `openpodsmaxctl watch` bridge watches the session D-Bus service;
it does not poll or carry audio. Setting requests await headphone readback and
time out honestly. A successful D-Bus method return is not hardware confirmation.
For settings that do not send an unsolicited acknowledgement, the daemon waits
for the write to settle and refreshes only the auxiliary AAP socket, keeping the
Bluetooth audio link and visible device identity. Repeated writes are rejected
while that verification is pending. Unavailable settings remain unknown/disabled.
`take` requests ownership without
autoplay. Calls, unclassified sources, an unconfigured remote peer, missing wear
state and disconnected links cannot be claimed.

The retained EarPort D-Bus name prevents two backend instances from owning the
same desktop interface. Do not run this backend alongside another AAP client.
Upstream licensing/provenance is recorded in [UPSTREAM.md](UPSTREAM.md).

## Acceptance limits

This is an investigation build. On the tested Moto Edge 70 / AirPods Max 2 /
Linux laptop, the owner confirmed both directions of audio handoff before and
again after a full phone reboot using the existing bonds. The temporary root
and runtime Apple DID were absent after reboot, independently confirmed by SDP.
This demonstrates that the existing setup can survive that reboot; it does not
establish a rootless first-time setup or a guarantee after a reset or re-pair.
The installer does not supply root, alter identity or change bonds.

Max 2 listening modes and conversation awareness have real readback. Both
earcups paused and resumed audible playback on Moto and Linux. A real Moto call
and microphone stayed active during Linux playback and a rejected manual claim.
The Linux daemon and Android playback service recovered after an abnormal process
exit without opening the app; phone boot recovery was automatic but delayed.

Real mpv output and resume were tested through PulseAudio and native PipeWire,
with an actual Chrome media session used as a second player. If a phone yields
but Linux remains silent, the daemon can restart the selected A2DP output and
reconnect its active streams through a temporary silent sink. Both operations
are bounded and scoped to the selected headphones, never a call profile.

The laptop's AAP controls recovered after actual suspend, but Moto music paused
in one suspend run. Closing just the Linux AAP socket or classic link did not
reproduce it. Automatic Moto connection after removing the headphones from the
Smart Case failed in an isolated test. Android enhanced Settings metadata was
also observed reverting to the default outside a reboot, although the restored
registration survived the tested reboot. These remain release blockers for an
unqualified seamless experience. Spatial audio/head tracking and microphone
upgrades are outside this control panel implementation.
