#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install the ii Quick Toggle integration; retain exact backups for rollback.
Build first. This script never enables or starts a service, changes Bluetooth
identity, or edits pairing. Stop other EarPort/AAP daemons before starting it.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import time

START = "            // OpenPodsMax: begin managed controls"
END = "            // OpenPodsMax: end managed controls"
BLOCK = START + "\n            AirPodsQuickControls { id: airPodsControls; Layout.fillWidth: true; controlsMaxHeight: sidebarRightBackground.height * 0.45 }\n" + END + "\n\n"
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None

def atomic(path, data, mode=0o644):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".openpodsmax-tmp")
    tmp.write_bytes(data)
    tmp.chmod(mode)
    tmp.replace(path)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shell", type=Path, default=Path.home()/".config/quickshell/ii")
    parser.add_argument("--peer-env", type=Path)
    parser.add_argument("--rollback", type=Path)
    args = parser.parse_args()
    if args.rollback:
        manifest = json.loads((args.rollback/"manifest.json").read_text())
        for entry in manifest:
            if digest(Path(entry["path"])) != entry["installed_sha256"]:
                raise SystemExit("Rollback refused: a managed file changed since installation")
        for i, entry in enumerate(manifest):
            path = Path(entry["path"])
            if entry["before_sha256"] is None:
                path.unlink()
            else:
                atomic(path, (args.rollback/str(i)).read_bytes(), entry["mode"])
        for entry in manifest:
            target = Path(entry["path"])
            if target.name == "SidebarRightContent.qml":
                root_shell = target.parents[3]/"shell.qml"
                if root_shell.exists(): os.utime(root_shell, None)
        print("Restored installation preimages; run systemctl --user daemon-reload")
        return
    if not args.peer_env or not args.peer_env.is_file():
        parser.error("--peer-env must reference the existing private headset/peer configuration")
    # Restrict EnvironmentFile syntax to two exact settings; never source a shell file.
    fields = {}
    import re
    for line in args.peer_env.read_text().splitlines():
        if not line.strip() or line.startswith("#"): continue
        key, separator, value = line.partition("=")
        if not separator or key not in {"OPENPODS_HANDOFF_HEADSET_MAC", "OPENPODS_HANDOFF_PEER_MAC"} or not re.fullmatch(r"(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}", value):
            parser.error("peer configuration must contain only the two plain Bluetooth address settings")
        fields[key] = value
    if len(fields) != 2: parser.error("both private address settings are required")
    source = Path(__file__).resolve().parent
    binary = source/"build/earport-daemon"
    if not binary.is_file(): parser.error("run linux/build.sh first")
    sidebar = args.shell/"modules/ii/sidebarRight/SidebarRightContent.qml"
    text = sidebar.read_text()
    anchor = "            CenterWidgetGroup {"
    if START in text:
        start = text.index(START)
        stop = text.index(END, start) + len(END)
        text = text[:start] + text[stop:].lstrip("\n")
    if text.count(anchor) != 1: parser.error("unsupported ii sidebar layout; no files changed")
    # Normalize the previous injection before applying it, including ancillary
    # bindings/spacer outside the managed header. Leave unrelated content intact.
    text = text.replace("                visible: !airPodsControls.expanded\n", "")
    text = text.replace("            Item { visible: airPodsControls.expanded; Layout.fillHeight: true; Layout.fillWidth: true }\n", "")
    text = text.replace(anchor, BLOCK + anchor)
    text = text.replace("            CenterWidgetGroup {\n", "            CenterWidgetGroup {\n                visible: !airPodsControls.expanded\n")
    text = text.replace("            BottomWidgetGroup {\n", "            BottomWidgetGroup {\n                visible: !airPodsControls.expanded\n")
    spacer = "            Item { visible: airPodsControls.expanded; Layout.fillHeight: true; Layout.fillWidth: true }\n"
    text = text.replace("            BottomWidgetGroup {", spacer + "            BottomWidgetGroup {")
    config = Path.home()/".config/openpodsmax-linux"
    data = Path.home()/".local/share/openpodsmax-linux"
    unit = ("[Unit]\nDescription=OpenPodsMax headphone controls and native handoff\nAfter=bluetooth.target\n\n"
            "[Service]\nType=simple\nEnvironment=XDG_CONFIG_HOME=%h/.config/openpodsmax-linux/runtime\n"
            "EnvironmentFile=%h/.config/openpodsmax-linux/peer.env\n"
            "Environment=OPENPODSMAX_REARM_HELPER=%h/.local/share/openpodsmax-linux/transport_rearm.py\n"
            "ExecStart=%h/.local/share/openpodsmax-linux/openpodsmax-daemon\n"
            "Restart=on-failure\nRestartSec=3\nNoNewPrivileges=true\nUMask=0077\n\n"
            "[Install]\nWantedBy=default.target\n")
    files = [
        (sidebar, text.encode(), 0o644),
        (args.shell/"services/OpenPodsMax.qml", (source/"quickshell/OpenPodsMax.qml").read_bytes(), 0o644),
        (args.shell/"modules/ii/sidebarRight/AirPodsQuickControls.qml", (source/"quickshell/AirPodsQuickControls.qml").read_bytes(), 0o644),
        (args.shell/"assets/openpodsmax/headphones.png", (source.parent/"app/src/main/res/drawable-nodpi/airpods_max_midnight.png").read_bytes(), 0o644),
        (Path.home()/".local/bin/openpodsmaxctl", (source/"openpodsmaxctl").read_bytes(), 0o755),
        (data/"openpodsmax-daemon", binary.read_bytes(), 0o755),
        (data/"transport_rearm.py", (source/"transport_rearm.py").read_bytes(), 0o644),
        (data/"LICENSE", (source/"backend/LICENSE").read_bytes(), 0o644),
        (config/"peer.env", args.peer_env.read_bytes(), 0o600),
        (Path.home()/".config/systemd/user/openpodsmax-linux.service", unit.encode(), 0o644),
    ]
    runtime = config/"runtime/earport/daemon.conf"
    if not runtime.exists():
        files.append((runtime, b"[Settings]\near_pause_mode=0\nhandoff_enabled=true\nauto_connect_on_wear=false\ndisconnect_on_removal=false\n", 0o600))
    backup = data/"backups"/(str(time.time_ns()))
    backup.mkdir(parents=True, mode=0o700)
    manifest = []
    # Save every preimage before changing anything, then write manifest before mutation.
    for i, (path, payload, mode) in enumerate(files):
        before = digest(path)
        old_mode = path.stat().st_mode & 0o777 if path.exists() else mode
        if before is not None: shutil.copy2(path, backup/str(i))
        manifest.append({"path": str(path), "before_sha256": before,
                         "installed_sha256": hashlib.sha256(payload).hexdigest(), "mode": old_mode})
    (backup/"manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    for path, payload, mode in files: atomic(path, payload, mode)
    # Replacing QML files atomically can leave a hot-reload watcher on the old
    # inode. Touch the unmodified root so Quickshell reloads all imported code.
    if (args.shell/"shell.qml").exists(): os.utime(args.shell/"shell.qml", None)
    print("Installed. Backup and guarded rollback manifest: " + str(backup))
    print("No service was started or enabled. Stop the old AAP backend, reload systemd, then start openpodsmax-linux.service.")
if __name__ == "__main__": main()
