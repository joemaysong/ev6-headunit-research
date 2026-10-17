from pathlib import Path
import struct,zipfile,hashlib,base64,subprocess,os
SOURCE=Path(__file__).resolve().parent
RELEASE=SOURCE.parents[1]
R=RELEASE/'build/button'
R.mkdir(parents=True,exist_ok=True)
u32=lambda *v:struct.pack('<'+'I'*len(v),*v)
chunk=lambda t,h,p:struct.pack('<HHI',t,h,8+len(p))+p
attrs={'label':0x01010001,'name':0x01010003,'permission':0x01010006,'exported':0x01010010,'minSdkVersion':0x0101020c,'versionCode':0x0101021b,'versionName':0x0101021c,'targetSdkVersion':0x01010270,'allowBackup':0x01010280}
strings=list(sorted(attrs,key=attrs.get))
def idx(s):
 if s not in strings:strings.append(s)
 return strings.index(s)
uri='http://schemas.android.com/apk/res/android';idx(uri);idx('android'); nodes=[]
def start(name,values=[]):
 vals=[]
 for ns,n,value,typ in values:
  nid=idx(n);nsid=idx(uri) if ns else 0xffffffff
  raw=idx(str(value)) if typ==3 else 0xffffffff
  data=raw if typ==3 else value
  vals.append((attrs.get(n,0) if ns else 0,u32(nsid,nid,raw)+struct.pack('<HBBI',8,0,typ,data)))
 vals.sort(key=lambda x:x[0]); ext=u32(0xffffffff,idx(name))+struct.pack('<HHHHHH',20,20,len(vals),0,0,0)
 nodes.append(chunk(0x102,16,u32(1,0xffffffff)+ext+b''.join(x[1] for x in vals)))
def end(name):nodes.append(chunk(0x103,16,u32(1,0xffffffff,0xffffffff,idx(name))))
a=lambda n,v,t=3:(True,n,v,t)
start('manifest',[(False,'package','org.ev6.carplaybutton',3),a('versionCode',1,0x10),a('versionName','0.1')])
start('uses-sdk',[a('minSdkVersion',19,0x10),a('targetSdkVersion',19,0x10)]);end('uses-sdk')
for p in ['BLUETOOTH','INTERNET','ACCESS_WIFI_STATE','ACCESS_NETWORK_STATE']:
 start('uses-permission',[a('name','android.permission.'+p)]);end('uses-permission')
start('application',[a('label','EV6 CarPlay Button'),a('allowBackup',0,0x12)])
start('uses-library',[a('name','com.tcc.iap2')]);end('uses-library')
start('service',[a('name','org.ev6.research.CpCommand'),a('exported',1,0x12),a('permission','android.permission.WRITE_SECURE_SETTINGS')]);start('intent-filter');start('action',[a('name','com.daudio.app.phoneprojection.command.launch')]);end('action');end('intent-filter');end('service')
end('application');end('manifest')
# UTF-8 string pool; this minimal manifest only uses short ASCII strings.
raw=b''; offsets=[]
for s in strings:
 b=s.encode();assert len(b)<128
 offsets.append(len(raw));raw+=bytes([len(s),len(b)])+b+b'\0'
raw+=b'\0'*((-len(raw))%4)
pool=chunk(1,28,u32(len(strings),0,0x100,28+len(strings)*4,0)+u32(*offsets)+raw)
resource=chunk(0x180,8,u32(*(attrs.get(s,0) for s in strings[:len(attrs)])))
nsstart=chunk(0x100,16,u32(1,0xffffffff,idx('android'),idx(uri)))
nsend=chunk(0x101,16,u32(1,0xffffffff,idx('android'),idx(uri)))
body=pool+resource+nsstart+b''.join(nodes)+nsend
manifest=struct.pack('<HHI',3,8,len(body)+8)+body
java=os.environ.get('JAVA', 'java')
subprocess.run([java,'-cp',str(RELEASE.parents[1]/'localization/tools/*'),'org.jf.smali.Main','assemble',str(SOURCE/'smali'),'-o',str(R/'prototype.dex')],check=True)
files={'AndroidManifest.xml':manifest,'classes.dex':(R/'prototype.dex').read_bytes()}
digest=lambda b:base64.b64encode(hashlib.sha256(b).digest()).decode()
header=b'Manifest-Version: 1.0\r\nCreated-By: EV6 Research\r\n\r\n'
sections=[f'Name: {n}\r\nSHA-256-Digest: {digest(b)}\r\n\r\n'.encode() for n,b in files.items()]
mf=header+b''.join(sections)
sf=f'Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: {digest(mf)}\r\n\r\n'.encode()+b''.join(f'Name: {n}\r\nSHA-256-Digest: {digest(sec)}\r\n\r\n'.encode() for n,sec in zip(files,sections))
# Generated test-only identity. Never uses a vehicle or user signing key.
key=RELEASE/'signing/button/key.pem';cert=RELEASE/'signing/button/cert.pem'
if not key.exists():
 subprocess.run(['/usr/bin/openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','14','-subj','/CN=EV6 Research Prototype'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 os.chmod(key,0o600)
(R/'PROBE.SF').write_bytes(sf)
subprocess.run(['/usr/bin/openssl','smime','-sign','-binary','-in',str(R/'PROBE.SF'),'-signer',str(cert),'-inkey',str(key),'-outform','DER','-out',str(R/'PROBE.RSA'),'-md','sha256'],check=True)
with zipfile.ZipFile(R/'prototype.apk','w',zipfile.ZIP_DEFLATED) as z:
 for n,b in files.items():z.writestr(n,b)
 z.writestr('META-INF/MANIFEST.MF',mf);z.writestr('META-INF/PROBE.SF',sf);z.writestr('META-INF/PROBE.RSA',(R/'PROBE.RSA').read_bytes())
print('Prototype APK built', (R/'prototype.apk').stat().st_size,'bytes')
