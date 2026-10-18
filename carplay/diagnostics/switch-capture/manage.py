"""Temporary private vehicle capture. start/status/collect/remove; no OEM UI control."""
from pathlib import Path
import subprocess,os,sys,re,json,time,datetime
HERE=Path(__file__).resolve().parent
ADB=os.environ.get('ADB','adb')
r=subprocess.run([ADB,'devices'],capture_output=True,text=True,check=True)
ids=[l.split()[0] for l in r.stdout.splitlines()[1:] if len(l.split())==2 and l.split()[1]=='device']
if len(ids)!=1:raise SystemExit('Unique vehicle ADB required')
os.environ['ADB']=ADB;os.environ['EV6_ADB_SERIAL']=ids[0];sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import device
STAGE='/data/local/tmp/ev6-bt-lifecycle-capture-20261018'
action=sys.argv[1] if len(sys.argv)==2 else ''
def running():
 if not device.exists(STAGE+'/pid'):return False
 pid=device.shell('cat '+STAGE+'/pid').strip()
 if not re.fullmatch('[1-9][0-9]*',pid):raise RuntimeError('Invalid sampler PID')
 return device.exists('/proc/'+pid) and not device.exists(STAGE+'/done')
def stop():
 device.shell('touch '+STAGE+'/stop')
 for _ in range(25):
  if device.exists(STAGE+'/done'):return
  time.sleep(1)
 raise RuntimeError('Sampler not finished; no files removed')
if action=='start':
 if device.exists(STAGE):raise RuntimeError('Existing capture retained; collect/remove first')
 device.shell('umask 077\nmkdir '+STAGE+'\nchmod 700 '+STAGE)
 device.push(HERE/'capture.sh',STAGE+'/capture.sh','700')
 device.push(HERE/'timeline.jar',STAGE+'/timeline.jar','600')
 try:device.shell("(trap '' HUP; /system/bin/sh "+STAGE+'/capture.sh '+STAGE+' >'+STAGE+'/samples.log 2>&1 </dev/null) &',timeout=8)
 except subprocess.TimeoutExpired:pass
 time.sleep(1)
 if not running():raise RuntimeError('Sampler not confirmed running')
 if not device.exists(STAGE+'/timeline.tsv'):raise RuntimeError('Timeline not ready; inspect retained stage')
 print('CAPTURE_RUNNING=true; TIMELINE_READY=true; limit=20 minutes; rotating timeline approximately 5MiB')
elif action=='status':
 print('CAPTURE_RUNNING='+str(running()).lower())
 print('TIMELINE_PRESENT='+str(device.exists(STAGE+'/timeline.tsv')).lower())
 print('TIMELINE_ENDED='+str(device.exists(STAGE+'/timeline.done')).lower())
 if device.exists(STAGE+'/samples.log'):
  lines='\n'.join(device.shell('cat '+STAGE+'/samples.log').splitlines()[-65:])
  print('\n'.join(l for l in lines.splitlines() if re.match(r'^(SAMPLE=|CAPTURE_|OBSERVER_|UNCERTAINTY_|RECOVERY_|TRANSPORT_|TRANSPORTS_|FAILED_WIRED_|ACTUAL_SESSION_|OBSERVED_SESSION_|REQUEST_ACTUAL_|CONTROLLER_STATUS=|NATIVE_ACTIVE=|UI_PROGRESS_ACTIVE=)',l)))
elif action=='collect':
 if not device.exists(STAGE):raise RuntimeError('Capture absent')
 if running():stop()
 out=HERE/'private-captures'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');out.mkdir(parents=True,mode=0o700)
 names=device.shell('ls '+STAGE).splitlines()
 for n in names:
  if n in ['samples.log','logcat.log','done','timeline.tsv','timeline.stderr','timeline.done'] or re.fullmatch(r'(?:logcat.log|timeline.tsv)\.[1-4]',n):device.pull(STAGE+'/'+n,out/n)
 for n in ['events.log','events.previous.log','bluetooth-work','bluetooth-cleanup-result','command-outcome','status']:
  source='/data/local/ev6-carplay/state/'+n
  if device.exists(source):device.pull(source,out/('controller-'+n))
 for p in out.iterdir():p.chmod(0o600)
 (out/'NOTICE.txt').write_text('Private diagnostic capture. May contain phone identifiers, network metadata and unrelated system events. Never publish or upload to GitHub. Start uptime in samples.log separates historical logcat tail.\n')
 print('COLLECTED='+str(out))
elif action=='remove':
 if device.exists(STAGE):
  if running():stop()
  device.shell('rm -r '+STAGE+'\nrm -f /data/dalvik-cache/data@local@tmp@ev6-bt-lifecycle-capture-20261018@timeline.jar@classes.dex')
 print('VEHICLE_CAPTURE_REMOVED=true; local private exports retained')
else:raise SystemExit('Usage: manage.py start|status|collect|remove')
