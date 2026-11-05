# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util
import json
from pathlib import Path
import subprocess
from types import SimpleNamespace
spec = importlib.util.spec_from_file_location("rearm", Path(__file__).resolve().parents[1]/"transport_rearm.py")
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
ADDRESS="00:11:22:33:44:55"
SINK="bluez_output.00_11_22_33_44_55.1"
def exercise(profile="a2dp-sink-sbc", sink=SINK, pause_fail=False, resume_fail=False, interrupted=False):
    commands=[]
    def run(argv, **kwargs):
        assert kwargs["timeout"] <= 0.8
        commands.append(argv)
        if argv[1:]==["-f","json","list","cards"]:
            return SimpleNamespace(stdout=json.dumps([{"name":"bluez_card.00_11_22_33_44_55","active_profile":profile}]))
        if argv[1:]==["list","sinks","short"]:
            return SimpleNamespace(stdout="7\t"+sink+"\tPipeWire\n")
        assert argv[:3]==["pactl","suspend-sink",SINK],argv
        if argv[-1]=="1" and pause_fail: raise subprocess.TimeoutExpired(argv,0.8)
        if argv[-1]=="0" and resume_fail: raise subprocess.CalledProcessError(1,argv)
        return SimpleNamespace(stdout="")
    def delay(seconds):
        assert seconds==0.4
        if interrupted: raise InterruptedError("cancelled")
    error=None
    try:module.rearm(ADDRESS,run,delay)
    except (RuntimeError,subprocess.SubprocessError,InterruptedError) as e:error=e
    return commands,error
commands,error=exercise();assert error is None
assert [c[-1] for c in commands if c[1]=="suspend-sink"]==["1","0"]
for profile in ("headset-head-unit","handsfree_head_unit"):
    commands,error=exercise(profile=profile);assert error and not any(c[1]=="suspend-sink" for c in commands)
commands,error=exercise(sink="bluez_output.00_11_22_33_44_99.1");assert error and not any(c[1]=="suspend-sink" for c in commands)
for kwargs in ({"pause_fail":True},{"interrupted":True}):
    commands,error=exercise(**kwargs);assert error
    assert commands[-1]==["pactl","suspend-sink",SINK,"0"]
commands,error=exercise(resume_fail=True);assert error
assert sum(c==["pactl","suspend-sink",SINK,"0"] for c in commands)==2
print("PASS A2DP-only selection, exact target, bounded operations, timeout/cancel restoration and resume retry")
