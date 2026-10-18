from pathlib import Path
import hashlib,re,subprocess,os,shutil
ADB=[os.environ.get('ADB', shutil.which('adb') or 'adb'),'-s',os.environ['EV6_ADB_SERIAL']]
def shell(command,timeout=40):
    result=subprocess.run(ADB+['shell','{\nset -e\n'+command+'\n}; _ev6_release_rc=$?; echo; echo EV6_RELEASE_RC=$_ev6_release_rc'],capture_output=True,timeout=timeout)
    output=result.stdout.decode('utf-8',errors='replace').replace('\r','')
    match=re.search(r'(?:^|\n)EV6_RELEASE_RC=([0-9]+)\s*$',output)
    if result.returncode or not match or match[1]!='0':raise RuntimeError('Vehicle operation failed; recovery files retained')
    return output[:match.start()].rstrip('\n')+'\n'
def exists(path):return 'EXISTS' in shell('if [ -e '+path+' ]; then echo EXISTS; fi')
def push(source,target,mode='644'):
    result=subprocess.run(ADB+['push',str(source),target],capture_output=True,timeout=25)
    if result.returncode:raise RuntimeError('Vehicle asset transfer failed')
    shell('chmod '+mode+' '+target)
    if hashlib.md5(Path(source).read_bytes()).hexdigest() not in shell('md5 '+target):raise RuntimeError('Vehicle asset checksum mismatch')
def pull(source,target):
    result=subprocess.run(ADB+['pull',source,str(target)],capture_output=True,timeout=25)
    if result.returncode:raise RuntimeError('Vehicle backup transfer failed')
