#!/usr/bin/env python3
"""Exercise device bootstrap orchestration against FAKE ADB only.

Never calls the real ADB binary. Simulates success and failure after partial
mutation, and checks exact-hash rollback.
"""
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ORIGINAL = (
    b"<DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>com.google.android.gms</DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>"
    b"<DEVICE_SETTINGS_CONFIG_CLASS>com.google.android.gms.Provider</DEVICE_SETTINGS_CONFIG_CLASS>"
    b"<DEVICE_SETTINGS_CONFIG_ACTION>com.google.android.gms.CONFIG</DEVICE_SETTINGS_CONFIG_ACTION>"
)
FAKE = r'''#!/usr/bin/env python3
import hashlib, os, pathlib, shutil, sys
root = pathlib.Path(os.environ["FAKE_ROOT"])
args=sys.argv[1:]
def die(s): print(s,file=sys.stderr);sys.exit(7)
if args[:2]!=["-s","mock-device"]:die("invalid target")
args=args[2:]
if args==["get-state"]:print("device");sys.exit(0)
if args[:3]==["shell","getprop","ro.product.manufacturer"]:print("motorola");sys.exit(0)
if args[:3]==["shell","getprop","ro.product.model"]:print(os.environ.get("FAKE_MODEL","motorola edge 70"));sys.exit(0)
if args[:3]==["shell","getprop","ro.build.version.sdk"]:print("37");sys.exit(0)
if args[:4]==["shell","pm","path","dev.airpodsnative.companion"]:print("package:/data/app/companion/base.apk");sys.exit(0)
if args[0]=="shell" and args[1].startswith("mkdir -p /data/local/tmp/airpods-native-p0/bootstrap-"):
    print("PRIVATE_REMOTE_TRANSACTION_DIRECTORY_READY");sys.exit(0)
if args[:2]==["shell","dumpsys"]:print("android.permission.BLUETOOTH_PRIVILEGED: granted=true");sys.exit(0)
if args[0]=="push":
    src=pathlib.Path(args[1]);target=pathlib.Path(args[2]).name
    (root/target).write_bytes(src.read_bytes());print("pushed");sys.exit(0)
if args[0]=="pull":
    target=pathlib.Path(args[1]).name
    pathlib.Path(args[2]).write_bytes((root/target).read_bytes());print("pulled");sys.exit(0)
if args[0]=="shell":
    command=args[1].split()
    try:index=command.index("dev.airpodsnative.bootstrap.MetadataProbe")
    except ValueError:die("probe command not found")
    command=command[index+1:]
    name=command[0]
    content=root.joinpath("current").read_bytes()
    if name=="backup-target":
        (root/pathlib.Path(command[2]).name).write_bytes(content)
        print("BACKUP_OK=true");print("SHA256="+hashlib.sha256(content).hexdigest());sys.exit(0)
    if name=="inspect-target":
        print("PRESENT=true");print("SHA256="+hashlib.sha256(content).hexdigest());sys.exit(0)
    if name=="swap-target":
        expected=(root/pathlib.Path(command[2]).name).read_bytes()
        updated=(root/pathlib.Path(command[3]).name).read_bytes()
        if content!=expected or hashlib.sha256(expected).hexdigest()!=command[4]:
            die("expected-hash precondition mismatch")
        (root/"current").write_bytes(updated)
        if os.environ.get("FAKE_FAIL_AFTER_WRITE")=="1" and not (root/"failed_once").exists():
            (root/"failed_once").touch()
            die("synthetic failure just after write")
        print("WRITE_RESULT=true");print("READBACK_MATCH=true")
        print("AFTER_SHA256="+hashlib.sha256(updated).hexdigest())
        sys.exit(0)
die("unknown call")
'''

with tempfile.TemporaryDirectory() as temp:
    base=pathlib.Path(temp)
    bindir=base/"bin";bindir.mkdir()
    file=bindir/"adb";file.write_text(FAKE);file.chmod(0o700)
    script=ROOT/"scripts/bootstrap_settings_for_bond.sh"
    for scenario in ["success","failure","unsupported-oem"]:
        fail = scenario == "failure"
        folder=base/scenario;folder.mkdir()
        (folder/"current").write_bytes(ORIGINAL)
        env=os.environ.copy()
        env["FAKE_ROOT"]=str(folder)
        env["PATH"]=str(bindir)+":"+env["PATH"]
        if fail:env["FAKE_FAIL_AFTER_WRITE"]="1"
        if scenario=="unsupported-oem":env["FAKE_MODEL"]="Pixel 10 Pro XL"
        result=subprocess.run(
            ["bash",str(script),"mock-device","01:23:45:67:89:AB","--confirmed-max2"],
            cwd=ROOT,env=env,text=True,capture_output=True,timeout=90
        )
        current=(folder/"current").read_bytes()
        if scenario=="unsupported-oem":
            assert result.returncode!=0 and "unknown OEM Settings Binder format" in result.stderr
            assert current==ORIGINAL, "unknown OEM must not receive a write"
            print("PASS unknown OEM/OS adapter rejected without write")
        elif fail:
            assert result.returncode!=0,(result.stdout,result.stderr)
            assert current==ORIGINAL,"failure must restore original metadata"
            assert "Rollback verified" in result.stderr,(result.stdout,result.stderr)
            print("PASS simulated post-write failure -> original restored")
        else:
            assert result.returncode==0,(result.stdout,result.stderr)
            assert current!=ORIGINAL,"success must update metadata"
            assert b"dev.airpodsnative.companion" in current
            assert "SETTINGS_BOOTSTRAP_OK=true" in result.stdout
            print("PASS simulated device-specific first bootstrap -> verified replacement")
print("PASS bootstrap wrapper: exact bond selection, private snapshot, guarded write, verified rollback")
