"""Build V16 components into a fresh local directory; no install or key creation."""
import argparse,hashlib,json,os,shutil,subprocess,sys,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
sys.path.insert(0,str(HERE/'tools'))
from apk import package
from verify_backend_abi import verify
from patch_oem import patch_tree

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('component',choices=['controller','backend','button','observer','bluetooth','builder'])
    p.add_argument('output',type=Path);p.add_argument('--oem-smali',type=Path)
    p.add_argument('--signing-cert',type=Path);p.add_argument('--signing-key',type=Path)
    p.add_argument('--installed-apk',type=Path);p.add_argument('--first-install',action='store_true');a=p.parse_args()
    out=a.output.resolve();component=a.component
    if out==Path('/dev') or Path('/dev') in out.parents:p.error('Output must be a fresh ordinary directory')
    java=os.environ.get('JAVA','java');android=os.environ['ANDROID_JAR'];ecj=os.environ['ECJ_JAR'];r8=os.environ['R8_JAR'];smali=os.environ['SMALI_CLASSPATH']
    if component in ('observer','bluetooth') and a.oem_smali is None:p.error('--oem-smali required')
    if any([a.signing_cert,a.signing_key,a.installed_apk,a.first_install]) and component not in ('backend','button'):p.error('Signing options apply to APKs only')
    out.mkdir(parents=True,exist_ok=False);classes=out/'classes';dex=out/'dex';classes.mkdir();dex.mkdir()
    def run(*args):subprocess.run(list(map(str,args)),check=True)
    def compile_java(sources):run(java,'-jar',ecj,'-source','1.7','-target','1.7','-bootclasspath',android,'-d',classes,*sources)
    def assemble(source,destination):run(java,'-cp',smali,'org.jf.smali.Main','assemble','--api','19',source,'-o',destination)
    def d8(inputs):run(java,'-cp',r8,'com.android.tools.r8.D8','--min-api','19','--lib',android,'--output',dex,*inputs)
    artifacts=[]
    if component in ('controller','backend'):
        compile_java(sorted((HERE/'src').glob('*.java')));extra=out/'authored.dex'
        assemble(HERE/('smali-provider' if component=='controller' else 'smali-backend'),extra)
        files=[v for v in classes.rglob('*.class') if ('org/ev6/research' in v.relative_to(classes).as_posix())==(component=='backend')]
        d8([extra,*sorted(files)])
    elif component=='button':assemble(HERE/'src/button/smali',dex/'classes.dex')
    elif component=='builder':compile_java([HERE/'tools/BuildOdex.java']);d8(sorted(classes.rglob('*.class')))
    else:
        target=out/'instrumented-smali';patch_tree('dipo-v16' if component=='observer' else 'bluetooth-v14',a.oem_smali.resolve(),target)
        names=['AttemptObservation','SessionLedger','EV6OemAttemptBridge','EV6WiredControllerGate'] if component=='observer' else ['BtWorkBridge']
        compile_java([HERE/'diagnostics'/(n+'.java') for n in names]);d8(sorted(classes.rglob('*.class')))
        helper=out/'helper-smali';run(java,'-cp',smali,'org.jf.baksmali.Main','disassemble','--api','19',dex/'classes.dex','-o',helper)
        for path in helper.rglob('*.smali'):
            dest=target/path.relative_to(helper)
            if dest.exists():raise ValueError('Helper would overwrite an OEM class')
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
        (out/'ABI.json').write_text(json.dumps(verify(target),indent=2)+'\n')
        artifact=out/('HKMC_DiPO.dex' if component=='observer' else 'Bluetooth.dex');assemble(target,artifact);artifacts.append(artifact)
    if component in ('controller','builder'):
        artifact=out/('controller.jar' if component=='controller' else 'builder.jar')
        with zipfile.ZipFile(artifact,'x',zipfile.ZIP_STORED) as jar:jar.writestr('classes.dex',(dex/'classes.dex').read_bytes())
        artifacts.append(artifact)
    elif component in ('backend','button'):
        artifact=out/(component+('.apk' if a.signing_cert else '-unsigned.apk'))
        package(component,dex/'classes.dex',artifact,a.signing_cert,a.signing_key,a.installed_apk,a.first_install,os.environ.get('OPENSSL','openssl'));artifacts.append(artifact)
    report={'component':component,'api':19,'installed':False,'runtime_accepted':False,'signing_identity_generated':False,'artifacts':{v.name:{'sha256':hashlib.sha256(v.read_bytes()).hexdigest(),'bytes':v.stat().st_size} for v in artifacts}}
    (out/'BUILD.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
