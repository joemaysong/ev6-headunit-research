from pathlib import Path
import struct,zipfile,hashlib,base64,subprocess,os,json,shutil
R=Path(__file__).resolve().parent;ROOT=R.parent;B=R/'build';B.mkdir(exist_ok=True);shutil.rmtree(B/'classes',ignore_errors=True);(B/'classes').mkdir(exist_ok=True)
JAVA=ROOT/'research/v16-leafia-upgrade-20261027/host-tools/jre17/jdk-17.0.21+9-jre/Contents/Home/bin/java';ANDROID=ROOT/'research/v16-leafia-upgrade-20261027/host-tools/android-19.jar';R8=ROOT/'research/leafia-port-assessment-20261026/host-tools/r8-8.3.37.jar'
def run(a):subprocess.run(list(map(str,a)),check=True)
run([JAVA,'-jar',ROOT/'controller/tools/ecj.jar','-source','1.7','-target','1.7','-bootclasspath',ANDROID,'-d',B/'classes',*sorted((R/'src').rglob('*.java'))])
run([JAVA,'-cp',R8,'com.android.tools.r8.D8','--min-api','19','--lib',ANDROID,'--output',B,*sorted((B/'classes').rglob('*.class'))])
u32=lambda *v:struct.pack('<'+'I'*len(v),*v)
chunk=lambda t,h,p:struct.pack('<HHI',t,h,8+len(p))+p
attrs={'theme':0x01010000,'launchMode':0x0101001d,'screenOrientation':0x0101001e,'configChanges':0x0101001f,'excludeFromRecents':0x01010017,'label':0x01010001,'icon':0x01010002,'name':0x01010003,'permission':0x01010006,'exported':0x01010010,'authorities':0x01010018,'process':0x01010011,'minSdkVersion':0x0101020c,'versionCode':0x0101021b,'versionName':0x0101021c,'targetSdkVersion':0x01010270,'allowBackup':0x01010280}
strings=list(sorted(attrs,key=attrs.get))
def idx(s):
 if s not in strings:strings.append(s)
 return strings.index(s)
uri='http://schemas.android.com/apk/res/android';idx(uri);idx('android');nodes=[]
def start(name,values=[]):
 vals=[]
 for ns,n,value,typ in values:
  nid=idx(n);nsid=idx(uri) if ns else 0xffffffff;raw=idx(str(value)) if typ==3 else 0xffffffff;data=raw if typ==3 else value
  vals.append((attrs.get(n,0) if ns else 0,u32(nsid,nid,raw)+struct.pack('<HBBI',8,0,typ,data)))
 vals.sort(key=lambda x:x[0]);ext=u32(0xffffffff,idx(name))+struct.pack('<HHHHHH',20,20,len(vals),0,0,0);nodes.append(chunk(0x102,16,u32(1,0xffffffff)+ext+b''.join(x[1] for x in vals)))
def end(name):nodes.append(chunk(0x103,16,u32(1,0xffffffff,0xffffffff,idx(name))))
a=lambda n,v,t=3:(True,n,v,t)
start('manifest',[(False,'package','org.ev6.launchpad',3),a('versionCode',24,0x10),a('versionName','0.2.16')]);start('uses-sdk',[a('minSdkVersion',19,0x10),a('targetSdkVersion',19,0x10)]);end('uses-sdk')
start('uses-permission',[a('name','android.permission.RECEIVE_BOOT_COMPLETED')]);end('uses-permission')
# Required by OEM activity-management callbacks when opening or finishing a screen.
start('uses-permission',[a('name','android.permission.GET_TASKS')]);end('uses-permission')
start('application',[a('label','Launchpad'),a('theme',0x01030006,1),a('icon',0x01080024,1),a('allowBackup',0,0x12)])
start('activity',[a('name','.MainActivity'),a('exported',1,0x12),a('launchMode',2,0x10),a('screenOrientation',0,0x10),a('configChanges',0x4a0,0x11),a('excludeFromRecents',1,0x12)])
start('intent-filter');start('action',[a('name','android.intent.action.MAIN')]);end('action');start('category',[a('name','android.intent.category.LAUNCHER')]);end('category');end('intent-filter');end('activity')
start('receiver',[a('name','.BootReceiver'),a('exported',0,0x12)]);start('intent-filter');start('action',[a('name','android.intent.action.BOOT_COMPLETED')]);end('action');end('intent-filter');end('receiver')
start('service',[a('name','.KnobService'),a('exported',0,0x12)]);end('service');end('application');end('manifest')
raw=b'';offsets=[]
for s in strings:
 b=s.encode();assert len(b)<128;offsets.append(len(raw));raw+=bytes([len(s),len(b)])+b+b'\0'
raw+=b'\0'*((-len(raw))%4);pool=chunk(1,28,u32(len(strings),0,0x100,28+len(strings)*4,0)+u32(*offsets)+raw);resource=chunk(0x180,8,u32(*(attrs.get(s,0) for s in strings[:len(attrs)])));nsstart=chunk(0x100,16,u32(1,0xffffffff,idx('android'),idx(uri)));nsend=chunk(0x101,16,u32(1,0xffffffff,idx('android'),idx(uri)));body=pool+resource+nsstart+b''.join(nodes)+nsend
manifest=struct.pack('<HHI',3,8,len(body)+8)+body
files={'AndroidManifest.xml':manifest,'classes.dex':(B/'classes.dex').read_bytes(),**{'assets/'+p.name:p.read_bytes() for p in (R/'assets').iterdir()}};digest=lambda b:base64.b64encode(hashlib.sha256(b).digest()).decode();sections=[f'Name: {n}\r\nSHA-256-Digest: {digest(b)}\r\n\r\n'.encode() for n,b in files.items()];mf=b'Manifest-Version: 1.0\r\nCreated-By: EV6 Launchpad Local\r\n\r\n'+b''.join(sections);sf=f'Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: {digest(mf)}\r\n\r\n'.encode()+b''.join(f'Name: {n}\r\nSHA-256-Digest: {digest(sec)}\r\n\r\n'.encode() for n,sec in zip(files,sections))
K=R/'signing';K.mkdir(exist_ok=True);key=K/'key.pem';cert=K/'cert.pem'
if not key.exists():
 run(['/usr/bin/openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','3650','-subj','/CN=EV6 Launchpad Local Local']);os.chmod(key,0o600)
(B/'APP.SF').write_bytes(sf);run(['/usr/bin/openssl','smime','-sign','-binary','-in',B/'APP.SF','-signer',cert,'-inkey',key,'-outform','DER','-out',B/'APP.RSA','-md','sha256'])
with zipfile.ZipFile(B/'EV6-Launchpad.apk','w',zipfile.ZIP_DEFLATED) as z:
 for n,b in files.items():z.writestr(n,b)
 z.writestr('META-INF/MANIFEST.MF',mf);z.writestr('META-INF/APP.SF',sf);z.writestr('META-INF/APP.RSA',(B/'APP.RSA').read_bytes())
record={'apk_sha256':hashlib.sha256((B/'EV6-Launchpad.apk').read_bytes()).hexdigest(),'bytes':(B/'EV6-Launchpad.apk').stat().st_size,'source_sha256':{str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (R/'src').rglob('*.java')}};(B/'manifest.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
