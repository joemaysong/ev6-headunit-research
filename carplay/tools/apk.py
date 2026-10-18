"""Project-only AXML and v1 APK signatures; no key generation."""
import base64,hashlib,struct,subprocess,zipfile
from pathlib import Path
ATTRS={'label':0x01010001,'name':0x01010003,'permission':0x01010006,'exported':0x01010010,'minSdkVersion':0x0101020c,'versionCode':0x0101021b,'versionName':0x0101021c,'targetSdkVersion':0x01010270,'allowBackup':0x01010280}
def manifest(component):
    backend=component=='backend';strings=list(sorted(ATTRS,key=ATTRS.get));nodes=[]
    u32=lambda *v:struct.pack('<'+'I'*len(v),*v)
    chunk=lambda t,h,p:struct.pack('<HHI',t,h,8+len(p))+p
    def idx(s):
        if s not in strings:strings.append(s)
        return strings.index(s)
    uri='http://schemas.android.com/apk/res/android';idx(uri);idx('android')
    def start(name,values=()):
        vals=[]
        for ns,n,value,typ in values:
            nid=idx(n);nsid=idx(uri) if ns else 0xffffffff
            raw=idx(str(value)) if typ==3 else 0xffffffff;data=raw if typ==3 else value
            vals.append((ATTRS.get(n,0) if ns else 0,u32(nsid,nid,raw)+struct.pack('<HBBI',8,0,typ,data)))
        vals.sort(key=lambda x:x[0]);ext=u32(0xffffffff,idx(name))+struct.pack('<HHHHHH',20,20,len(vals),0,0,0)
        nodes.append(chunk(0x102,16,u32(1,0xffffffff)+ext+b''.join(v for _,v in vals)))
    def end(name):nodes.append(chunk(0x103,16,u32(1,0xffffffff,0xffffffff,idx(name))))
    a=lambda n,v,t=3:(True,n,v,t)
    start('manifest',[(False,'package','org.ev6.wirelesscarplay' if backend else 'org.ev6.carplaybutton',3),a('versionCode',2026101709 if backend else 1,0x10),a('versionName','2026.10.17-oem9' if backend else '0.1')])
    start('uses-sdk',[a('minSdkVersion',19,0x10),a('targetSdkVersion',19,0x10)]);end('uses-sdk')
    for p in ['BLUETOOTH','INTERNET','ACCESS_WIFI_STATE','ACCESS_NETWORK_STATE']:
        start('uses-permission',[a('name','android.permission.'+p)]);end('uses-permission')
    start('application',[a('label','EV6 Wireless CarPlay' if backend else 'EV6 CarPlay Button'),a('allowBackup',0,0x12)])
    start('uses-library',[a('name','com.tcc.iap2')]);end('uses-library')
    start('service',[a('name','org.ev6.research.ProbeService' if backend else 'org.ev6.research.CpCommand'),a('permission','android.permission.WRITE_SECURE_SETTINGS'),a('exported',1,0x12)])
    start('intent-filter');start('action',[a('name','com.remote.service.IAP2BTSERVICE' if backend else 'com.daudio.app.phoneprojection.command.launch')]);end('action');end('intent-filter');end('service');end('application');end('manifest')
    raw=b'';offsets=[]
    for s in strings:
        b=s.encode();assert len(b)<128
        offsets.append(len(raw));raw+=bytes([len(s),len(b)])+b+b'\0'
    raw+=b'\0'*(-len(raw)%4)
    pool=chunk(1,28,u32(len(strings),0,0x100,28+len(strings)*4,0)+u32(*offsets)+raw)
    resource=chunk(0x180,8,u32(*(ATTRS.get(s,0) for s in strings[:len(ATTRS)])))
    ns=lambda t:chunk(t,16,u32(1,0xffffffff,idx('android'),idx(uri)))
    body=pool+resource+ns(0x100)+b''.join(nodes)+ns(0x101)
    return struct.pack('<HHI',3,8,len(body)+8)+body

def package(component,dex,output,cert=None,key=None,installed=None,first_install=False,openssl='openssl'):
    files={'AndroidManifest.xml':manifest(component),'classes.dex':Path(dex).read_bytes()}
    if bool(cert)!=bool(key):raise ValueError('Supply both retained cert and retained key')
    if cert:
        cert,key=Path(cert).resolve(),Path(key).resolve()
        if installed is None and not first_install:raise ValueError('Signing requires --installed-apk or explicit --first-install')
        def run(*args):return subprocess.run([openssl,*map(str,args)],check=True,capture_output=True).stdout
        if run('x509','-in',cert,'-pubkey','-noout')!=run('pkey','-in',key,'-pubout'):raise ValueError('Retained certificate/key do not match')
        if installed:
            with zipfile.ZipFile(installed) as old:
                signers=[n for n in old.namelist() if n.upper().endswith('.RSA')]
                if len(signers)!=1:raise ValueError('Expected one existing RSA signer')
                signature=output.parent/'previous-signature.der';signature.write_bytes(old.read(signers[0]))
            pem=output.parent/'previous-signer.pem';pem.write_bytes(run('pkcs7','-inform','DER','-in',signature,'-print_certs'))
            if run('x509','-in',pem,'-outform','DER')!=run('x509','-in',cert,'-outform','DER'):raise ValueError('Existing APK signer differs; retain the existing key')
            signature.unlink();pem.unlink()
        digest=lambda b:base64.b64encode(hashlib.sha256(b).digest()).decode()
        sections=[f'Name: {n}\r\nSHA-256-Digest: {digest(b)}\r\n\r\n'.encode() for n,b in files.items()]
        mf=b'Manifest-Version: 1.0\r\nCreated-By: EV6 Research\r\n\r\n'+b''.join(sections)
        sf=f'Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: {digest(mf)}\r\n\r\n'.encode()+b''.join(f'Name: {n}\r\nSHA-256-Digest: {digest(s)}\r\n\r\n'.encode() for n,s in zip(files,sections))
        sf_path,rsa_path=output.parent/'EV6.SF',output.parent/'EV6.RSA';sf_path.write_bytes(sf)
        run('smime','-sign','-binary','-in',sf_path,'-signer',cert,'-inkey',key,'-outform','DER','-out',rsa_path,'-md','sha256')
        run('smime','-verify','-binary','-inform','DER','-in',rsa_path,'-content',sf_path,'-noverify','-out','/dev/null')
        files.update({'META-INF/MANIFEST.MF':mf,'META-INF/EV6.SF':sf,'META-INF/EV6.RSA':rsa_path.read_bytes()});sf_path.unlink();rsa_path.unlink()
    with zipfile.ZipFile(output,'x',zipfile.ZIP_DEFLATED) as apk:
        for name,data in files.items():apk.writestr(name,data)
