# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import importlib.util, tempfile, unittest.mock, sys, contextlib, io, json
source=Path(__file__).resolve().parents[1]/'install.py'
spec=importlib.util.spec_from_file_location('installer',source);mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
with tempfile.TemporaryDirectory() as td:
 home=Path(td);shell=home/'shell';panel=shell/'modules/ii/sidebarRight/SidebarRightContent.qml';panel.parent.mkdir(parents=True)
 original=b'ColumnLayout {\n            CenterWidgetGroup {\n            }\n            BottomWidgetGroup {\n            }\n}\n'
 panel.write_bytes(original);peer=home/'peer.env';peer.write_text('OPENPODS_HANDOFF_HEADSET_MAC=00:11:22:33:44:55\nOPENPODS_HANDOFF_PEER_MAC=66:77:88:99:AA:BB\n')
 def invoke(args):
  with unittest.mock.patch.object(Path,'home',return_value=home),unittest.mock.patch.object(sys,'argv',['install.py']+args),contextlib.redirect_stdout(io.StringIO()):mod.main()
 invoke(['--shell',str(shell),'--peer-env',str(peer)])
 initial=panel.read_bytes()
 first=next((home/'.local/share/openpodsmax-linux/backups').iterdir())
 invoke(['--shell',str(shell),'--peer-env',str(peer)])
 assert panel.read_bytes()==initial,'repeat installation changed sidebar'
 assert panel.read_text().count('visible: !airPodsControls.expanded')==2
 backups=sorted((home/'.local/share/openpodsmax-linux/backups').iterdir())
 second=backups[-1]
 # Refuse rollback after an independent user change, without partial writes.
 panel.write_bytes(initial+b'// user change\n')
 try:invoke(['--rollback',str(second)])
 except SystemExit:pass
 else:raise AssertionError('rollback accepted changed file')
 assert panel.read_bytes().endswith(b'// user change\n')
 panel.write_bytes(initial)
 invoke(['--rollback',str(second)])
 invoke(['--rollback',str(first)])
 assert panel.read_bytes()==original,'original sidebar not restored exactly'
 assert not (shell/'services/OpenPodsMax.qml').exists()
 print('PASS install twice, guarded rollback refusal, exact two-step restoration')
