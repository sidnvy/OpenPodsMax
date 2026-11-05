# Linux backend provenance

EarPort handoff fork, GPL-3.0-or-later:
https://github.com/pika-02-03/earport-handoff
Pinned source: 1d55f5cf51fa44f3a139e1df4ca2b5a2a6ace436.
The vendored backend retains its license and upstream credits.

Smart routing packet layout derives from LibrePods (GPL-3.0):
https://github.com/kavishdevar/librepods
AACPManager.kt at 89356c08cea3db0b6b54792d9329d09a87cc85fa.

OpenPodsMax changes: direct bounded smart-routing packets, exact target selection,
reported audio source and setting validity, readback-driven Quick Toggle controls.
The EarPort D-Bus namespace is retained for compatibility; run only one backend.

The silent-sink stream recovery follows EarPort's upstream audio-route approach
at 68f73ebce6dc4f6ce78e4907baeff9080273c9d3 (`daemon/src/audio_route.c`),
with explicit A2DP profile and selected-stream checks and bounded cleanup.
