#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""One bounded A2DP transport restart, only after the daemon validates handoff.
No Bluetooth sockets, profile/volume changes, pairing or identity operations.
"""
import re
import json
import signal
import subprocess
import sys
import time

def rearm(address, run=subprocess.run, delay=time.sleep):
    if not re.fullmatch(r"(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}", address):
        raise ValueError("Invalid headphone address")
    cards = run(["pactl", "-f", "json", "list", "cards"], capture_output=True,
                text=True, timeout=0.8, check=True)
    name = "bluez_card." + address.replace(":", "_").lower()
    matches = [card for card in json.loads(cards.stdout) if card.get("name", "").lower() == name]
    if len(matches) != 1:
        raise RuntimeError("Selected Bluetooth card is unavailable")
    profile = matches[0].get("active_profile", "")
    if isinstance(profile, dict): profile = profile.get("name", "")
    if not profile.startswith("a2dp-sink"):
        raise RuntimeError("Headphone output is not in A2DP mode")
    result = run(["pactl", "list", "sinks", "short"], capture_output=True,
                 text=True, timeout=0.8, check=True)
    prefix = "bluez_output." + address.replace(":", "_").lower() + "."
    sinks = []
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1].lower().startswith(prefix):
            # PipeWire A2DP output names end in a numeric endpoint or a2dp-sink.
            # HFP/HSP/headset profiles are never eligible for a restart.
            suffix = fields[1][len(prefix):].lower()
            if re.fullmatch(r"(?:[0-9]+|a2dp[-_]sink(?:[._-].*)?)", suffix):
                sinks.append(fields[1])
    if len(sinks) != 1:
        raise RuntimeError("One unambiguous headphone A2DP output is required")
    sink = sinks[0]
    try:
        run(["pactl", "suspend-sink", sink, "1"], timeout=0.8, check=True)
        delay(0.4)
    finally:
        # Even a timeout/termination may follow a successful server-side pause.
        # Always restore that exact output, without inheriting a cancellation.
        restored = False
        for _ in range(2):
            try:
                run(["pactl", "suspend-sink", sink, "0"], timeout=0.8, check=True)
                restored = True
                break
            except (subprocess.SubprocessError, OSError):
                pass
        if not restored:
            raise RuntimeError("Could not restore headphone output; retry playback")

def interrupted(*_):
    # Only the first termination interrupts work; cleanup must finish.
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    raise InterruptedError("Handoff cancelled")

if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        if len(sys.argv) != 2: raise ValueError("Headphone address required")
        rearm(sys.argv[1])
    except (ValueError, RuntimeError, InterruptedError, subprocess.SubprocessError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
