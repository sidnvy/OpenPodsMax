# Remove nonfunctional cycle and Crown settings

Issue: https://github.com/sidnvy/OpenPodsMax/issues/3

## Moto Edge 70 observations

The installed OpenPods Max 2 connection reports ANC listening modes and other
settings, but no 0x1A ListeningModeCycle value. The current app itself displays:
"Button cycle choices have not been reported by the headset." The backend
refuses 0x1A writes without an actual device-reported baseline.

CAPod upstream also documents that AirPods do not ordinarily push 0x1A or
0x34 as settings and maintains a best-effort last-requested mask. That is not
a verified current device configuration.

## Fix

- Remove the overview's misleading Button Cycles Between list entirely.
- Remove the Controls & Gestures page, whose only options are 0x1A cycle
  editing without a verified baseline, disabled Digital Crown direction
  placeholders, and unverified call/camera controls.
- Remove the Overview Digital Crown row that linked to that page.
- Preserve the genuinely functional listening-mode selector (ANC/Off/
  Transparency/Adaptive), verified control writes, automatic head detection
  and all other supported capabilities.
- The device's physical Digital Crown and listening-mode button are unaffected.
  No speculative Bluetooth or AAP writes are introduced.

## Acceptance

- Source has no reachable cycle or Crown configuration UI.
- Code compiles and all existing JVM/backend tests pass.
- In-place installation on Moto with the original release key retains settings
  and shows the functional ANC controls, but no misleading cycle/Crown rows.
- Any future 0x1A solution requires explicit capability detection and a
  separately reviewed, hardware-validated contract. Do not claim last-requested
  values are device-verified.

This change is independent of PR #1 and merged toggle fix PR #4.
