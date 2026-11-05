#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""One bounded A2DP transport restart, only after the daemon validates handoff.
No Bluetooth sockets, profile/volume changes, pairing or identity operations.
"""
import re
import os
import json
import signal
import subprocess
import sys
import time

def rearm_streams(sink, sink_index, run, delay):
    """Reconnect active streams already on this output through a silent sink.
    Implements the stream-graph restart used by current upstream EarPort.
    Other outputs and corked streams are never moved.
    """
    result = run(["pactl", "-f", "json", "list", "sink-inputs"],
                 capture_output=True, text=True, timeout=0.8, check=True)
    streams = [str(x["index"]) for x in json.loads(result.stdout)
               if str(x.get("sink")) == sink_index and x.get("corked") is False]
    if not streams: return
    if len(streams) > 16: raise RuntimeError("Too many active streams for bounded recovery")
    name = "openpodsmax_handoff_" + str(os.getpid())
    module = None
    moved = []
    try:
        result = run(["pactl", "load-module", "module-null-sink", "sink_name=" + name],
                     capture_output=True, text=True, timeout=0.8, check=True)
        module = result.stdout.strip()
        if not module.isdecimal(): raise RuntimeError("No recovery sink was created")
        for stream in streams:
            moved.append(stream) # also restore if the server applied a timed-out request
            run(["pactl", "move-sink-input", stream, name], timeout=0.8, check=True)
        delay(0.3)
    finally:
        # Cancellation must not strand streams or leave a null sink behind.
        # The main daemon cancels on a real disconnect/call/new remote takeover.
        for stream in moved:
            for _ in range(2):
                try:
                    run(["pactl", "move-sink-input", stream, sink], timeout=0.8, check=True)
                    break
                except (subprocess.SubprocessError, OSError): pass
        if module and module.isdecimal():
            run(["pactl", "unload-module", module], timeout=0.8, check=True)


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
    indexes = {}
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1].lower().startswith(prefix):
            # PipeWire A2DP output names end in a numeric endpoint or a2dp-sink.
            # HFP/HSP/headset profiles are never eligible for a restart.
            suffix = fields[1][len(prefix):].lower()
            if re.fullmatch(r"(?:[0-9]+|a2dp[-_]sink(?:[._-].*)?)", suffix):
                sinks.append(fields[1])
                indexes[fields[1]] = fields[0]
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
    rearm_streams(sink, indexes[sink], run, delay)

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
