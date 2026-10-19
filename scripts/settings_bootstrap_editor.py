#!/usr/bin/env python3
"""Prepare a Moto Settings provider registration OFFLINE from device-specific Key 25.

Preserves unknown metadata bytes/order. Never contacts ADB or mutates a device.
The subsequent Shell bootstrap requires an exact bonded address, an exact
SHA-256 preimage and a verified rollback snapshot; no fixed P0 hash/index.
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys

FIELDS = (
    "DEVICE_SETTINGS_CONFIG_PACKAGE_NAME",
    "DEVICE_SETTINGS_CONFIG_CLASS",
    "DEVICE_SETTINGS_CONFIG_ACTION",
)
ALLOWED = {
    "package": "dev.airpodsnative.companion",
    "class": "dev.airpodsnative.companion.AirPodsSettingsService",
    "action": "dev.airpodsnative.companion.BIND_SETTINGS",
}
KEY_PATTERN = re.compile(rb"<([A-Z][A-Z_0-9]*)>(.*?)</\1>", re.DOTALL)
MAX_METADATA_BYTES = 2048

def sha(blob):
    return hashlib.sha256(blob).hexdigest()

def update(blob: bytes) -> bytes:
    if not blob or len(blob) > MAX_METADATA_BYTES:
        raise ValueError("Missing or oversized device metadata; refusing unsafe edit")
    try:
        blob.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ValueError("Metadata is not valid UTF-8") from exc
    matches = list(KEY_PATTERN.finditer(blob))
    if not matches:
        raise ValueError("Metadata contains no recognized fields")
    names = [m.group(1).decode("ascii") for m in matches]
    for name in FIELDS:
        if names.count(name) != 1:
            raise ValueError("Expected exactly one field: " + name)
    # Only operate on the exact Motorola Settings key-25 XML fragment format.
    leftovers = KEY_PATTERN.sub(b"", blob)
    if leftovers.strip():
        raise ValueError("Unexpected binary/XML envelope; refusing edit")
    replacements = dict(zip(FIELDS, (
        ALLOWED["package"], ALLOWED["class"], ALLOWED["action"]
    )))
    result = KEY_PATTERN.sub(
        lambda m: (b"<" + m.group(1) + b">" +
                   replacements.get(m.group(1).decode("ascii"),
                                    m.group(2).decode("utf-8")).encode("utf-8") +
                   b"</" + m.group(1) + b">"),
        blob,
    )
    if len(result) > MAX_METADATA_BYTES:
        raise ValueError("Replacement metadata exceeds maximum length")
    verify = {m.group(1).decode(): m.group(2).decode() for m in KEY_PATTERN.finditer(result)}
    for name, val in replacements.items():
        if verify[name] != val:
            raise ValueError("Roundtrip verification failed")
    return result

def prepare(source: pathlib.Path, directory: pathlib.Path):
    original = source.read_bytes()
    updated = update(original)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    original_dest = directory / "key25.original"
    updated_dest = directory / "key25.proposed"
    manifest_dest = directory / "manifest.json"
    for path, value in ((original_dest, original), (updated_dest, updated)):
        path.write_bytes(value)
        path.chmod(0o600)
    manifest = {
        "key": 25, "revision": 1, "schema": "airpods-native/moto-key25",
        "expected_original_sha256": sha(original),
        "expected_proposed_sha256": sha(updated),
        "expected_original_length": len(original),
        "expected_proposed_length": len(updated),
        "package": ALLOWED["package"], "class": ALLOWED["class"],
        "action": ALLOWED["action"],
        "requires_exact_bond_selection": True,
        "requires_shell_privileged_permission": True,
    }
    manifest_dest.write_text(json.dumps(manifest, indent=2) + "\n")
    manifest_dest.chmod(0o600)
    return manifest

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=pathlib.Path, required=True)
    ap.add_argument("--destination", type=pathlib.Path, required=True)
    args = ap.parse_args()
    try:
        result = prepare(args.source, args.destination)
    except (ValueError, OSError) as exc:
        print("BLOCKED:", exc, file=sys.stderr)
        sys.exit(2)
    print(json.dumps(result, sort_keys=True))
