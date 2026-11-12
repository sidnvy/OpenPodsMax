"""Production config transaction with isolated fake mount namespaces; no radio/root required."""
import json
import os
from pathlib import Path
import subprocess
import unittest
from test_apple_identity_transaction import IdentityTransactionTests, FAKE, SCRIPT, VENDOR, SOURCE

EXT = r'''
if name=='nsenter':
 assert a[:1]==['-t'] and a[2:4]==['-m','--'], a
 pid=a[1];cmd=a[4:]; views=d.setdefault('views',{}); target=d['target']; current=views.get(pid,target)
 if pid not in d.get('alive',['100','999']): sys.exit(1)
 if cmd[0]=='ls' and cmd[1]=='-Z': print('u:object_r:system_file:s0 '+cmd[2]);sys.exit(0)
 if cmd[0]=='mount':
  assert cmd[1]=='--bind' and cmd[3]==target
  if fail('fail_mount_'+pid): sys.exit(1)
  views[pid]=cmd[2];d['events'].append(['mount',pid]);save();sys.exit(0)
 if cmd[0]=='umount':
  assert cmd[1]==target
  if fail('fail_umount_'+pid): sys.exit(1)
  views[pid]=target;d['events'].append(['umount',pid]);save();sys.exit(0)
 cmd=[current if x==target else x for x in cmd]
 sys.exit(subprocess.run(['/usr/bin/'+cmd[0],*cmd[1:]]).returncode)
if name=='cat':
 if a[0].startswith('/proc/') and a[0].endswith('/cmdline'): print('com.android.bluetooth');sys.exit(0)
 sys.exit(subprocess.run(['/usr/bin/cat',*a]).returncode)
if name=='kill':
 assert a[0]=='-TERM' and a[1]==d['alive'][-1]
 if fail('fail_signal'):sys.exit(1)
 d['dead_bt_pid']=True;d['events'].append(['signal',a[1]]);save();sys.exit(0)
if name=='readlink':
 pid=a[0].split('/')[2]
 if pid not in d.get('alive',['100','999']): sys.exit(1)
 print('mnt:['+pid+']');sys.exit(0)
if name=='ls' and a[0]=='-Z': print('u:object_r:system_file:s0 '+a[1]);sys.exit(0)
if name=='chcon': sys.exit(1 if fail('fail_chcon') else 0)
'''

class ConfigTransactionTests(IdentityTransactionTests):
    def setUp(self):
        super().setUp()
        self.target=self.base/'bt_did.conf';self.original='[DID1]\nprimaryRecord = true\nvendorId = 0x0075\n#vendorIdSource = 0x0001\nproductId = 0x0100\nversion = 0x0201\n[DID2]\n#vendorId = 0x000F\n'
        self.target.write_text(self.original)
        self.proc=self.base/'proc';self.proc.mkdir()
        for pid in range(999,1020):
            folder=self.proc/str(pid);folder.mkdir();(folder/'status').write_text('Uid:\t1002\t1002\t1002\t1002\n')
        self.data['props']={VENDOR:'0x0075',SOURCE:''};self.data['target']=str(self.target);self.data['alive']=['100','999']
        fake=FAKE.replace("if name=='id':",EXT+"\nif name=='id':")
        fake=fake.replace("elif name=='pidof': print('999')", "elif name=='pidof': print(('100 '+d['alive'][-1]) if 'zygote64' in a else ('150' if 'dev.airpodsnative.companion' in a and '150' in d['alive'] else ('' if d.get('dead_bt_pid') else d['alive'][-1])))")
        fake=fake.replace("elif name=='awk': print('/apex/test/libbluetooth_jni.so')", "elif name=='awk':\n if a[-1].startswith('/proc/') and a[-1].endswith('/status'): print('1002')\n elif a[-1].startswith('/proc/'): print('/apex/test/libbluetooth_jni.so')\n else: sys.exit(subprocess.run(['/usr/bin/awk',*a]).returncode)")
        fake=fake.replace("d['bt']='OFF' if op=='disable' else 'ON'", """d['bt']='OFF' if op=='disable' else 'ON'
 if op=='enable' and (d.get('restart_bt') or d.get('dead_bt_pid')):
  old=d['alive'][-1];new=str(int(old)+1);d['alive'][-1]=new
  d.setdefault('views',{}).pop(old,None);d['views'][new]=d['views'].get('100',d['target']);d['dead_bt_pid']=False
 if op=='enable' and fail('terminate_after_enable'): os.kill(os.getppid(),signal.SIGTERM)""")
        self.fake.write_text(fake)
        for name in ['nsenter','readlink','ls','chcon','cat','kill']:(self.bin/name).symlink_to(self.fake)
        helpers=SCRIPT.with_name('apple_identity_config.sh').read_text()
        # Substitute only public filesystem locations; execute the unchanged transaction logic.
        for path in ['/apex/com.android.bt/etc/bluetooth/bt_did.conf','/apex/com.android.btservices/etc/bluetooth/bt_did.conf','/system/etc/bluetooth/bt_did.conf']:
            helpers=helpers.replace(path,str(self.target))
        self.script=self.base/'transaction.sh';self.script.write_text((helpers+'\n'+SCRIPT.read_text()).replace('/system/bin/kill',str(self.bin/'kill')).replace('/proc/$pid/status',str(self.proc)+'/$pid/status'))
    def invoke(self,*args):
        self.state.write_text(json.dumps(self.data))
        env={**os.environ,'PATH':str(self.bin)+':/usr/bin:/bin','FAKE_ANDROID_STATE':str(self.state)}
        r=subprocess.run(['/bin/sh',str(self.script),*args,str(self.lock)],env=env,capture_output=True,text=True,timeout=12)
        self.data=json.loads(self.state.read_text());return r
    def enable(self):return self.invoke('enable-config','0x0075','','0x004C','1',__import__('test_apple_identity_transaction').BOOT)
    def disable(self):return self.invoke('disable-config','0x004C','1','0x0075','',__import__('test_apple_identity_transaction').BOOT)
    def test_config_enable_preserves_signed_file(self):
        r=self.enable();self.assertEqual(r.returncode,0,r.stderr);self.assertEqual(self.target.read_text(),self.original)
        self.assertEqual(self.data['props'],{VENDOR:'0x004C',SOURCE:'1'})
        for pid in self.data['alive']:
            patch=Path(self.data['views'][pid]);self.assertIn('vendorId = 0x004C',patch.read_text());self.assertIn('vendorIdSource = 0x0001',patch.read_text())
        self.assertIn('CONFIG_ACTIVE=1',self.invoke('probe').stdout)
    def test_config_restore_covers_new_bluetooth_process(self):
        self.data['restart_bt']=True;r=self.enable();self.assertEqual(r.returncode,0,r.stderr)
        self.assertNotEqual(self.data['alive'][-1],'999')
        r=self.disable();self.assertEqual(r.returncode,0,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''})
        self.assertEqual(self.target.read_text(),self.original)
        self.assertTrue(all(self.data['views'].get(p,str(self.target))==str(self.target) for p in self.data['alive']))
        self.assertFalse((self.base/'apple-identity-config').exists())
    def test_partial_mount_failure_rolls_back(self):
        self.data['fail_mount_999']=True;r=self.enable();self.assertEqual(r.returncode,22,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''})
        self.assertEqual(self.data['views'].get('100'),str(self.target));self.assertEqual(self.data['bt'],'ON')
        self.assertFalse((self.base/'apple-identity-config').exists())
    def test_config_bluetooth_enable_failure_rolls_back(self):
        self.data['restart_bt']=True;self.data['fail_enable']=True;r=self.enable();self.assertEqual(r.returncode,16,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''});self.assertEqual(self.data['bt'],'ON')
        self.assertTrue(all(self.data['views'].get(p,str(self.target))==str(self.target) for p in self.data['alive']))
    def test_restore_failure_keeps_recovery_state(self):
        self.assertEqual(self.enable().returncode,0)
        self.data['fail_umount_'+self.data['alive'][-1]]=True;r=self.disable();self.assertEqual(r.returncode,22,r.stderr)
        self.assertTrue((self.base/'apple-identity-config'/'boot').exists())
        self.assertEqual(self.disable().returncode,0);self.assertFalse((self.base/'apple-identity-config').exists())
    def test_changed_patch_cannot_be_silently_unmounted(self):
        self.assertEqual(self.enable().returncode,0)
        patch=self.base/'apple-identity-config'/'patched';patch.write_text('changed outside transaction')
        r=self.disable();self.assertEqual(r.returncode,22,r.stderr);self.assertTrue(patch.exists())
    def test_foreign_binding_is_not_unmounted(self):
        self.assertEqual(self.enable().returncode,0)
        foreign=self.base/'foreign';foreign.write_text('unrelated config');current=self.data['alive'][-1];self.data['views'][current]=str(foreign)
        r=self.disable();self.assertEqual(r.returncode,22,r.stderr)
        self.assertEqual(self.data['views'][current],str(foreign));self.assertTrue((self.base/'apple-identity-config').exists())
    def test_label_failure_has_no_property_writes(self):
        self.data['fail_chcon']=True;r=self.enable();self.assertEqual(r.returncode,22,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''});self.assertEqual(self.target.read_text(),self.original)
        self.assertFalse((self.base/'apple-identity-config').exists())
    def test_failure_after_native_enable_reloads_original_config(self):
        self.data['restart_bt']=True;self.data['terminate_after_enable']=True;r=self.enable()
        self.assertEqual(r.returncode,30,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''})
        self.assertEqual([e for e in self.data['events'] if e==['cmd','disable']],[['cmd','disable']]*2)
        self.assertEqual(self.data['views'][self.data['alive'][-1]],str(self.target))
        self.assertFalse((self.base/'apple-identity-config').exists())
    def test_failed_restore_is_pending_without_false_active_success(self):
        self.assertEqual(self.enable().returncode,0)
        self.data['fail_enable']=True;r=self.disable();self.assertEqual(r.returncode,16,r.stderr)
        report=self.invoke('probe').stdout
        self.assertIn('CONFIG_PENDING=1',report);self.assertIn('CONFIG_ACTIVE=0',report)
    def test_enable_after_restore_can_capture_original_again(self):
        self.assertEqual(self.enable().returncode,0);self.assertEqual(self.disable().returncode,0)
        self.assertEqual(self.enable().returncode,0)
        self.assertEqual((self.base/'apple-identity-config'/'original').read_text(),self.original)
    def test_restore_cleans_inherited_app_namespace(self):
        self.data['alive']=['100','150','999'];self.assertEqual(self.enable().returncode,0)
        self.data['views']['150']=self.data['views']['100']
        self.assertEqual(self.disable().returncode,0)
        self.assertEqual(self.data['views']['150'],str(self.target))
    def test_bluetooth_process_refresh_is_scoped(self):
        self.assertEqual(self.enable().returncode,0)
        signals=[e for e in self.data['events'] if e[0]=='signal']
        self.assertEqual(signals,[['signal','999']])
        self.assertNotEqual(self.data['alive'][-1],'999')
    def test_refused_process_refresh_keeps_safe_recovery(self):
        self.data['fail_signal']=True;r=self.enable();self.assertEqual(r.returncode,23,r.stderr)
        self.assertEqual(self.data['props'],{VENDOR:'0x0075',SOURCE:''});self.assertEqual(self.data['bt'],'ON')
    def test_wrong_process_uid_is_never_signalled(self):
        (self.proc/'999'/'status').write_text('Uid:\t1000\t1000\t1000\t1000\n')
        r=self.enable();self.assertEqual(r.returncode,23,r.stderr)
        self.assertFalse(any(e[0]=='signal' for e in self.data['events']))
    def test_config_source_and_product_fields_preserved(self):
        self.assertEqual(self.enable().returncode,0)
        patch=(self.base/'apple-identity-config'/'patched').read_text()
        self.assertIn('productId = 0x0100',patch);self.assertIn('version = 0x0201',patch)
        self.assertEqual(patch.count('vendorIdSource = 0x0001'),2) # one comment + one active field

# Do not rerun inherited property-only tests against the config fixture's different baseline.
for name in list(IdentityTransactionTests.__dict__):
    if name.startswith('test_') and name not in ConfigTransactionTests.__dict__:setattr(ConfigTransactionTests,name,None)
if __name__=='__main__':
    unittest.main(defaultTest='ConfigTransactionTests')
