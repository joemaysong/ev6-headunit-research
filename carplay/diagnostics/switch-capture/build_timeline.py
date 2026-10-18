from pathlib import Path
import subprocess,zipfile,hashlib,json,os
here=Path(__file__).resolve().parent;out=here/'timeline-build';out.mkdir(exist_ok=True)
java=os.environ['JAVA'];ecj=os.environ['ECJ_JAR'];android=Path(os.environ['ANDROID_JAR']);r8=os.environ['R8_JAR']
controller_src=Path(os.environ['CONTROLLER_SOURCE'])
for name in ['controller-classes','classes','dex']:(out/name).mkdir(exist_ok=True)
def run(args):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=90)
 if p.returncode:raise RuntimeError(p.stdout[-1500:]+p.stderr[-2500:])
run([java,'-jar',ecj,'-source','1.7','-target','1.7','-bootclasspath',android,'-d',out/'controller-classes',*sorted(controller_src.glob('*.java'))])
run([java,'-jar',ecj,'-source','1.7','-target','1.7','-bootclasspath',android,'-classpath',out/'controller-classes','-d',out/'classes',*sorted(here.glob('*.java'))])
run([java,'-cp',r8,'com.android.tools.r8.D8','--min-api','19','--lib',android,'--classpath',out/'controller-classes','--output',out/'dex',*list((out/'classes').glob('*.class'))])
jar=here/'timeline.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_STORED) as f:f.writestr('classes.dex',(out/'dex/classes.dex').read_bytes())
print(json.dumps({'compiled':True,'installed':False,'tests_run':False,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}))
