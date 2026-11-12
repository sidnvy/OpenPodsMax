"""Runs the production root shell against a fake Android, never ADB or a real radio."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/apple_identity.sh'
BOOT = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
VENDOR = 'bluetooth.device_id.vendor_id'
SOURCE = 'bluetooth.device_id.vendor_id_source'
FAKE = r'''#!/usr/bin/env python3
import json, os, pathlib, signal, subprocess, sys
p=pathlib.Path(os.environ['FAKE_ANDROID_STATE']); d=json.loads(p.read_text()); name=pathlib.Path(sys.argv[0]).name; a=sys.argv[1:]
def save(): p.write_text(json.dumps(d))
def fail(flag):
 if d.get(flag): d[flag]=False; save(); return True
 return False
if name=='id': print('0' if d.get('root',True) else '2000')
elif name=='pidof': print('999')
elif name=='awk': print('/apex/test/libbluetooth_jni.so')
elif name=='grep' and '/apex/test/libbluetooth_jni.so' in a: sys.exit(0 if d.get('backend',True) else 1)
elif name=='grep': sys.exit(subprocess.run(['/usr/bin/grep',*a]).returncode)
elif name=='getprop': print(d['props'].get(a[0],''))
elif name=='setprop':
 d['events'].append(['set',*a]); save()
 flag='fail_vendor' if a[0].endswith('.vendor_id') else 'fail_source'
 if fail(flag): sys.exit(1)
 if d.get('fail_restore') and a[0].endswith('.vendor_id') and a[1]=='0x001D': sys.exit(1)
 d['props'][a[0]]=a[1]; save()
elif name=='dumpsys':
 if a==['bluetooth_manager']: print('  State: '+d['bt'])
 elif a==['audio']: print('Actual mode = '+('MODE_IN_CALL' if d.get('call') else 'MODE_NORMAL'))
 else: sys.exit(1)
elif name=='cmd':
 assert len(a)==2 and a[0]=='bluetooth_manager' and a[1] in ('disable','enable'), a
 op=a[1]; d['events'].append(['cmd',op]); save()
 if fail('fail_'+op): sys.exit(1)
 d['bt']='OFF' if op=='disable' else 'ON'
 if op=='disable' and d.get('shutdown_conflict'): d['props']['bluetooth.device_id.vendor_id']='0x1234'
 save()
elif name=='sleep':
 if fail('terminate_at_sleep'): os.kill(os.getppid(),signal.SIGTERM)
else: raise RuntimeError(name)
'''

class IdentityTransactionTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.base=Path(self.tmp.name); self.bin=self.base/'bin'; self.bin.mkdir()
        self.state=self.base/'state.json'; self.lock=self.base/'apple-identity.lock'
        self.data={'props':{VENDOR:'0x001D',SOURCE:'1'},'bt':'ON','events':[]}
        self.fake=self.bin/'fake'; self.fake.write_text(FAKE); self.fake.chmod(0o700)
        for name in ['id','pidof','awk','grep','getprop','setprop','dumpsys','cmd','sleep']:(self.bin/name).symlink_to(self.fake)
    def tearDown(self):self.tmp.cleanup()
    def invoke(self,*args):
        self.state.write_text(json.dumps(self.data))
        env={**os.environ,'PATH':str(self.bin)+':/usr/bin:/bin','FAKE_ANDROID_STATE':str(self.state)}
        r=subprocess.run(['/bin/sh',str(SCRIPT),*args,str(self.lock)],env=env,capture_output=True,text=True,timeout=8)
        self.data=json.loads(self.state.read_text()); return r
    def change(self,expected_v='0x001D',expected_s='1',desired_v='0x004C',desired_s='1',boot=BOOT):
        return self.invoke('change',expected_v,expected_s,desired_v,desired_s,boot)
    def unchanged(self):
        self.assertEqual(self.data['props'],{VENDOR:'0x001D',SOURCE:'1'});self.assertEqual(self.data['bt'],'ON')
    def test_active_transaction_blocks_writes(self):
        self.lock.mkdir();(self.lock/'owner').write_text(BOOT+' '+str(os.getpid())+'\n')
        self.assertIn('BUSY=1',self.invoke('probe').stdout)
        self.assertEqual(self.change().returncode,21);self.assertEqual(self.data['events'],[])
    def test_abandoned_transaction_can_recover(self):
        self.lock.mkdir();(self.lock/'owner').write_text('00000000-0000-0000-0000-000000000000 999999999\n')
        self.assertIn('BUSY=0',self.invoke('probe').stdout)
        self.assertEqual(self.change().returncode,0);self.assertFalse(self.lock.exists())
    def test_unknown_lock_contents_are_preserved(self):
        self.lock.mkdir();(self.lock/'keep').write_text('unrecognized')
        self.assertEqual(self.change().returncode,21);self.assertEqual((self.lock/'keep').read_text(),'unrecognized')
        self.assertEqual(self.data['events'],[])
    def test_probe_is_read_only(self):
        r=self.invoke('probe');self.assertEqual(r.returncode,0);self.assertIn('BACKEND=1',r.stdout);self.assertEqual(self.data['events'],[])
    def test_enable_and_exact_restore(self):
        r=self.change();self.assertEqual(r.returncode,0,r.stderr);self.assertIn('RESULT=CHANGED',r.stdout)
        self.assertEqual(self.data['props'],{VENDOR:'0x004C',SOURCE:'1'});self.assertEqual(self.data['bt'],'ON')
        r=self.change('0x004C','1','0x001D','1');self.assertEqual(r.returncode,0,r.stderr);self.unchanged()
        self.assertEqual([e for e in self.data['events'] if e[0]=='cmd'],[['cmd','disable'],['cmd','enable']]*2)
    def test_unset_original_values_restore_as_unset(self):
        self.data['props']={VENDOR:'',SOURCE:''};self.assertEqual(self.change('','').returncode,0)
        self.assertEqual(self.change('0x004C','1','','').returncode,0);self.assertEqual(self.data['props'],{VENDOR:'',SOURCE:''})
    def test_source_namespace_is_changed_and_restored(self):
        self.data['props'][SOURCE]='2';self.assertEqual(self.change(expected_s='2').returncode,0)
        self.assertEqual(self.change('0x004C','1','0x001D','2').returncode,0);self.assertEqual(self.data['props'][SOURCE],'2')
    def test_noop_never_cycles_bluetooth(self):
        r=self.change(desired_v='0x001D');self.assertEqual(r.returncode,0);self.assertEqual(self.data['events'],[])
    def test_no_root_cannot_write(self):
        self.data['root']=False;self.assertEqual(self.change().returncode,10);self.assertEqual(self.data['events'],[])
    def test_malformed_and_injected_values_cannot_write(self):
        for value in ['76; echo injected','0x','0x10000','65536','-1','$(id)','1\n2']:
            with self.subTest(value=value):self.assertEqual(self.change(desired_v=value).returncode,11);self.assertEqual(self.data['events'],[])
    def test_changed_boot_cannot_write(self):
        self.assertEqual(self.change(boot='00000000-0000-0000-0000-000000000000').returncode,18);self.assertEqual(self.data['events'],[])
    def test_unsupported_stack_cannot_write(self):
        self.data['backend']=False;self.assertEqual(self.change().returncode,19);self.assertEqual(self.data['events'],[])
    def test_preimage_conflict_cannot_write(self):
        self.assertEqual(self.change(expected_v='0x1234').returncode,20);self.assertEqual(self.data['events'],[])
    def test_bluetooth_off_is_preserved(self):
        self.data['bt']='OFF';self.assertEqual(self.change().returncode,14);self.assertEqual(self.data['events'],[]);self.assertEqual(self.data['bt'],'OFF')
    def test_call_blocks_changes(self):
        self.data['call']=True;self.assertEqual(self.change().returncode,13);self.assertEqual(self.data['events'],[])
    def test_vendor_write_failure_restores(self):
        self.data['fail_vendor']=True;r=self.change();self.assertNotEqual(r.returncode,0);self.unchanged()
        self.assertEqual([e for e in self.data['events'] if e[0]=='cmd'],[])
    def test_partial_source_write_failure_restores_vendor(self):
        self.data['fail_source']=True;r=self.change();self.assertNotEqual(r.returncode,0);self.unchanged()
    def test_disable_failure_restores_and_ensures_bluetooth_on(self):
        self.data['fail_disable']=True;r=self.change();self.assertEqual(r.returncode,15);self.unchanged()
    def test_enable_failure_restores_and_reenables(self):
        self.data['fail_enable']=True;r=self.change();self.assertEqual(r.returncode,16);self.unchanged()
        self.assertEqual([e for e in self.data['events'] if e==['cmd','enable']],[['cmd','enable']]*2)
    def test_concurrent_external_change_is_not_overwritten(self):
        self.data['shutdown_conflict']=True;r=self.change();self.assertEqual(r.returncode,20)
        self.assertEqual(self.data['props'][VENDOR],'0x1234');self.assertIn('ROLLBACK=CONFLICT',r.stdout);self.assertEqual(self.data['bt'],'ON')
    def test_termination_triggers_rollback(self):
        self.data['terminate_at_sleep']=True;r=self.change();self.assertEqual(r.returncode,30);self.unchanged()
    def test_failed_restore_does_not_claim_success(self):
        self.data['fail_enable']=True;self.data['fail_restore']=True;r=self.change()
        self.assertNotEqual(r.returncode,0);self.assertNotIn('RESULT=CHANGED',r.stdout);self.assertNotIn('ROLLBACK=RESTORED',r.stdout)
        self.assertEqual(self.data['props'][VENDOR],'0x004C');self.assertEqual(self.data['bt'],'ON')

if __name__=='__main__':unittest.main()
