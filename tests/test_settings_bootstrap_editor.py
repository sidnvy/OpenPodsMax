#!/usr/bin/env python3
"""Offline metadata fixtures/fault injection. Never calls adb."""
import hashlib
import pathlib
import sys
import tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"scripts"))
from settings_bootstrap_editor import prepare, update, ALLOWED, sha

def fixture(extra=b""):
    return (b"<DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>com.google.android.gms</DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>"
            b"<DEVICE_SETTINGS_CONFIG_CLASS>com.google.android.gms.Provider</DEVICE_SETTINGS_CONFIG_CLASS>"
            b"<DEVICE_SETTINGS_CONFIG_ACTION>com.google.android.gms.CONFIG</DEVICE_SETTINGS_CONFIG_ACTION>"
            +extra)

sample=fixture(b"<UNRELATED_OEM_FIELD>preserve-opaque-value-123</UNRELATED_OEM_FIELD>")
converted=update(sample)
assert b"<UNRELATED_OEM_FIELD>preserve-opaque-value-123</UNRELATED_OEM_FIELD>" in converted
assert b"<DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>dev.airpodsnative.companion</DEVICE_SETTINGS_CONFIG_PACKAGE_NAME>" in converted
assert update(converted)==converted
assert sha(sample)!=sha(converted)

for bad in (b"", b"bogus", fixture()+fixture(), b"\xff\xfe"+fixture(), fixture(b"<INVALID_BAD/>")):
    try: update(bad)
    except ValueError: continue
    raise AssertionError("Unexpectedly accepted malformed/ambiguous metadata")

with tempfile.TemporaryDirectory() as tmp:
    source=pathlib.Path(tmp)/"input";source.write_bytes(sample)
    output=pathlib.Path(tmp)/"out"
    m=prepare(source,output)
    assert m["expected_original_sha256"]==hashlib.sha256(sample).hexdigest()
    assert m["expected_proposed_sha256"]==hashlib.sha256(converted).hexdigest()
    assert (output/"key25.original").read_bytes()==sample
    assert (output/"key25.proposed").read_bytes()==converted
    assert (output/"manifest.json").exists()

print("PASS offline device-specific settings metadata editor: 11 checks, preservation, idempotence, bad input rejection, snapshot+manifest")
