#!/usr/bin/env python3
"""Source-level safety invariants; behavioral JVM/fake-ADB tests run separately."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=root/"app/src/main/java/dev/airpodsnative/companion"
controller=(app/"AapController.java").read_text()
identity=(app/"DeviceIdentityStore.java").read_text()
policy=(app/"ConnectionPolicy.java").read_text()
service=(app/"AirPodsSettingsService.java").read_text()
activity=(app/"MainActivity.kt").read_text()
probe=(root/"tests/p0-shell-metadata/MetadataProbe.java").read_text()
bootstrap=(root/"scripts/bootstrap_settings_for_bond.sh").read_text()
required={
    "no alias gate for approved bonds": 'getAlias()' not in controller and 'trustedAndBonded(app,address)' in controller,
    "explicit owner approval before control": 'DeviceIdentityStore.approveBond(context, address)' in activity,
    "renamed devices remain selectable": 'DeviceIdentityStore.trusted(context, d.address)' in activity,
    "user can inspect all paired devices": "showAllBonds" in activity,
    "headset switch safely stops prior session": 'controller.stop()' in activity and "Change AirPods Max 2" in activity,
    "Settings service guards unknown Bluetooth targets": 'DeviceIdentityStore.trustedAndBonded' in service,
    "AAP controller generation-protected readers": 'policy.current(token,address)' in controller,
    "bounded backoff and reset": '30000' in policy and 'policy.succeeded(token,address)' in controller,
    "dead socket schedules retry": 'retryIfCurrent(address,token)' in controller,
    "stale reader doesn't reset new state": 'if(policy.current(token,address))' in controller,
    "disconnect invalidates Settings state": 'addSessionListener(SESSION_RELAY)' in service and 'new ProbeSetting(OPEN_SETTINGS_ID,(mode>=0 && mode<4)?mode:-1)' in service and 'Listening mode unavailable' in service,
    "commands time out rather than leaving unbounded pending flags": 'expireSettingReadback(receipt),30000,TimeUnit.MILLISECONDS)' in controller and 'SETTING_READBACK_UNRESOLVED_ID=' in controller and 'ANC_ACK_TIMEOUT_INDEX=' in controller,
    "Shell chooses precise bond without name matching": 'targetByAddress(args[1])' in probe and 'getBondState()!=BluetoothDevice.BOND_BONDED' in probe,
    "write requires same live metadata preimage": 'Arrays.equals(current,expected)' in probe and 'digest(expected).equals(args[4])' in probe,
    "bootstrap does not use P0 fixed index/hash": 'swap-target' in bootstrap and 'swap-guarded auto' not in bootstrap,
    "OEM private Settings gated to tested target": 'MODEL,,}' in bootstrap and 'SDK' in bootstrap and 'unknown OEM Settings Binder format' in bootstrap,
    "no automatic ordinary APK privileged grant": '<uses-permission android:name="android.permission.BLUETOOTH_PRIVILEGED"' not in (root/"app/src/main/AndroidManifest.xml").read_text(),
}
for label,passed in required.items():
    print(("PASS" if passed else "FAIL"),label)
assert all(required.values()),"Portability contract violation"
