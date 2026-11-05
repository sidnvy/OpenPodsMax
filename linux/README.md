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

The startup configuration keeps Linux wear-pause disabled to preserve the
existing test setup; select Either earcup in the panel to enable it. Linux-only
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
Unavailable settings remain unknown/disabled. `take` requests ownership without
autoplay. Calls, unclassified sources, an unconfigured remote peer, missing wear
state and disconnected links cannot be claimed.

The retained EarPort D-Bus name prevents two backend instances from owning the
same desktop interface. Do not run this backend alongside another AAP client.
Upstream licensing/provenance is recorded in [UPSTREAM.md](UPSTREAM.md).

## Acceptance limits

These are investigation builds, not a public release. Native dual-host switching
currently depends on the previously tested Apple identities and Android handoff
candidate. The Moto identity uses temporary root and does not survive a reboot.
This installer does not supply root, change identity or re-pair anything.

Noise cancellation, transparency and adaptive mode have been confirmed by Max 2
readback. Conversation awareness is exposed with pending/timeout handling but its
write readback remains unconfirmed in this session. Do not mark it hardware PASS.
The first test-player SIGSTOP experiment was invalid for audio resume. Further
real testing found that after the phone yields, Linux may keep a running stream
without issuing a new A2DP start. The daemon now performs one bounded restart of
the exact selected A2DP output after an ownership request reports idle, with fresh
wear/playback checks and a six-second claim window. It never restarts a call
profile. The helper restores the output even after timeout/cancellation.

With that recovery installed, the owner confirmed audible Moto → Linux and
Linux → Moto switching, with final playback on Moto. A separate manual recovery
check confirmed Digital Crown volume remained functional. These results cover
this Max 2/Moto/laptop session, not all media players or lifecycle conditions.
Calls, suspend/resume, reboot recovery and broad Linux player compatibility still
need acceptance testing. Spatial audio/head tracking and microphone upgrades are
outside this control panel implementation.
