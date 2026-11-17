"""Minimal AA-only method overlay on the private candidate101 GPM DEX."""
from pathlib import Path
import subprocess,tempfile,json,hashlib,sys,re
import argparse,os
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-dex',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
j=os.environ['JAVA']; smali_classpath=os.environ['SMALI_CLASSPATH']
o=args.output.resolve()
if o==root or root in o.parents:raise RuntimeError('Output must be outside the public repository')
if hashlib.sha256(args.base_dex.read_bytes()).hexdigest()!='c3df6fa0025b103c57069fc13f806408dba91264d33e33db1a23dd097e79e16d':raise RuntimeError('Expected private candidate101 DEX')
o.mkdir(exist_ok=False,parents=True)
sys.path.insert(0,str(root/'carplay'));from patch_oem import contract,canonical,METHOD
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,*args):
 p=subprocess.run(list(map(str,[j,*args])),capture_output=True,timeout=180);(o/(n+'.log')).write_bytes(p.stdout+p.stderr);p.check_returncode()
def methods(s):return {m.group().splitlines()[0].split()[-1]:m.group() for m in METHOD.finditer(s)}
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp);d=t/'dex';d.mkdir();base=args.base_dex.resolve()
 run('base','-cp',smali_classpath,'org.jf.baksmali.Main','d','-a','19',base,'-o',t/'base')
 before=contract(t/'base')
 name='com/google/android/projection/service/GalReceiverService.smali'
 signature='ev6AaObservedOriginal_onStartCommand(Landroid/content/Intent;II)I'
 changes={name:[signature]};p=t/'base'/name;s=p.read_text();old=methods(s)[signature]
 field='Lcom/google/android/projection/service/GalReceiverService;->mWifiTransport:Lcom/google/android/projection/protocol/WifiTransport;'
 assert '.field private static mWifiTransport:' in s
 bad='iget-object v2, p0, '+field;good='sget-object v2, '+field
 assert old.count(bad)==1
 changed=old.replace(bad,good);p.write_text(s.replace(old,changed))
 assert changed.replace(good,bad)==old
 # Verify every direct transport read in this method against the actual field modifiers.
 declared={}
 for line in s.splitlines():
  if line.startswith('.field '):
   tokens=line.split();key=next(x for x in tokens if ':' in x);declared[key]='static' in tokens
 for op,key in re.findall(r'\b([is]get-object) [^\n]*GalReceiverService;->(m(?:Wifi|Usb)Transport:[^\s]+)',changed):
  assert (op=='sget-object')==declared[key],(op,key)
 after=contract(t/'base');assert set(before)==set(after);assert {n for n in before if before[n]!=after[n]}==set(changes)
 run('assemble','-cp',smali_classpath,'org.jf.smali.Main','a','-a','19','-j','1',t/'base','-o',o/'HKMC_GPM.dex')
 run('roundtrip','-cp',smali_classpath,'org.jf.baksmali.Main','d','-a','19',o/'HKMC_GPM.dex','-o',t/'roundtrip')
 roundtrip=contract(t/'roundtrip')
 assert all(roundtrip[n]==after[n] for n in after if n not in changes)
 for name in changes:
  left=(t/'base'/name).read_text();right=(t/'roundtrip'/name).read_text()
  if {k:canonical(v) for k,v in methods(left).items()}!={k:canonical(v) for k,v in methods(right).items()}:
   import difflib
   (o/'roundtrip-diff.txt').write_text('\n'.join(difflib.unified_diff(canonical(left).splitlines(),canonical(right).splitlines())))
   raise AssertionError(name)
  assert sorted(canonical(METHOD.sub('',left)).splitlines())==sorted(canonical(METHOD.sub('',right)).splitlines()),name
 proof=dict(base_sha256=sha(base),payload_sha256={'HKMC_GPM.dex':sha(o/'HKMC_GPM.dex')},changed_methods=changes,unchanged_classes=len(before)-len(changes),other_methods_preserved=True,cp_and_usb_methods_preserved=True,installed=False)
 (o/'verification.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof,indent=2))
