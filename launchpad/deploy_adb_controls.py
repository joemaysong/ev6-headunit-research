import os
"""APK-only Launchpad update; preserves startup, preferences and ADB transports."""
from pathlib import Path
import subprocess,hashlib,json,time,zipfile
R=Path(__file__).resolve().parent;D=R/'deployments/20261106-adb-controls';D.mkdir(exist_ok=True)
ADB=os.environ.get('ADB','adb');SERIAL=os.environ['HEADUNIT_SERIAL']
def adb(*a):return subprocess.run([ADB,'-s',SERIAL,*map(str,a)],check=True,capture_output=True,timeout=60).stdout.decode().replace('\r','')
def sh(code):
 out=adb('shell','( set -e\n'+code+'\n)\necho LAUNCHPAD_RC=$?')
 if not out.rstrip().endswith('LAUNCHPAD_RC=0'):raise RuntimeError(out)
 return out.rsplit('LAUNCHPAD_RC=',1)[0].strip()
def state():return sh('getprop sys.usb.config; getprop sys.usb.state; getprop service.adb.tcp.port; settings get global adb_enabled; md5 /system/etc/install-recovery.sh')
old=sh('pm path org.ev6.launchpad').removeprefix('package:');assert old in ['/data/app/org.ev6.launchpad-1.apk','/data/app/org.ev6.launchpad-2.apk']
backup=D/'before.apk';assert not backup.exists(),'Existing deployment: inspect before retry'
adb('pull',old,backup);before=state();(D/'before-state.txt').write_text(before)
# APK signer continuity.
for apk,name in [(backup,'before'),(R/'build/EV6-Launchpad.apk','after')]:
 with zipfile.ZipFile(apk) as z:
  rsa=[n for n in z.namelist() if n.endswith('.RSA')];assert len(rsa)==1;(D/(name+'.rsa')).write_bytes(z.read(rsa[0]))
 p=subprocess.run(['/usr/bin/openssl','pkcs7','-inform','DER','-in',str(D/(name+'.rsa')),'-print_certs'],capture_output=True,check=True);(D/(name+'.cert')).write_bytes(p.stdout)
assert (D/'before.cert').read_bytes()==(D/'after.cert').read_bytes(),'Signer changed'
# Read-only isolated class loading before installation.
tmp='/data/local/tmp/ev6-launchpad-adb-check.apk';adb('push',R/'build/EV6-Launchpad.apk',tmp)
try:
 reply=sh('CLASSPATH='+tmp+' app_process /system/bin org.ev6.launchpad.RootTool adb-status');assert 'RESULT_OK' in reply;(D/'isolated-status.txt').write_text(reply)
finally:sh('rm '+tmp+'; rm -f /data/dalvik-cache/data@local@tmp@ev6-launchpad-adb-check.apk@classes.dex')
assert state()==before,'Preflight changed ADB state'
sh('touch /data/local/ev6-launchpad/bridge-stop');time.sleep(6)
try:
 out=adb('install','-r',R/'build/EV6-Launchpad.apk');assert 'Success' in out,out
finally:
 sh('rm -f /data/local/ev6-launchpad/bridge-stop')
 sh('trap "" HUP; /system/bin/sh /data/local/ev6-launchpad/start.sh </dev/null >/dev/null 2>&1 &\nsleep 1')
new=sh('pm path org.ev6.launchpad').removeprefix('package:');digest=hashlib.md5((R/'build/EV6-Launchpad.apk').read_bytes()).hexdigest();assert sh('md5 '+new).split()[0]==digest
assert state()==before,'ADB/startup state changed'
reply=sh('CLASSPATH='+new+' app_process /system/bin org.ev6.launchpad.RootTool adb-status');assert 'RESULT_OK' in reply
(D/'result.json').write_text(json.dumps(dict(installed=True,version='0.2.14',adb_state_unchanged=True,startup_unchanged=True,rebooted=False,apk_sha256=hashlib.sha256((R/'build/EV6-Launchpad.apk').read_bytes()).hexdigest(),status=reply),indent=2)+'\n')
print(reply);print('LAUNCHPAD_ADB_CONTROLS_INSTALLED')
