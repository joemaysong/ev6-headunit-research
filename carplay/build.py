"""Rebuild the frozen final controller into a fresh output; never install."""
from pathlib import Path
import argparse, os, subprocess, zipfile

ROOT=Path(__file__).resolve().parent

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path);args=parser.parse_args()
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
    java=os.environ.get('JAVA','java')
    ecj=os.environ['ECJ_JAR'];android=os.environ['ANDROID_JAR']
    r8=os.environ['R8_JAR'];smali=os.environ['SMALI_CLASSPATH']
    classes=out/'classes';classes.mkdir();dex=out/'dex';dex.mkdir()
    def run(args):subprocess.run([str(x) for x in args],check=True)
    run([java,'-jar',ecj,'-source','1.7','-target','1.7','-bootclasspath',android,
        '-d',classes,*sorted((ROOT/'src').glob('*.java'))])
    run([java,'-cp',smali,'org.jf.smali.Main','assemble','--api','19',
        ROOT/'smali-provider','-o',out/'provider.dex'])
    own=[p for p in classes.rglob('*.class') if 'org/ev6/research' not in str(p)]
    run([java,'-cp',r8,'com.android.tools.r8.D8','--min-api','19','--lib',android,
        '--output',dex,out/'provider.dex',*own])
    with zipfile.ZipFile(out/'controller.jar','w',zipfile.ZIP_STORED) as jar:
        jar.writestr('classes.dex',(dex/'classes.dex').read_bytes())
    print('Final controller rebuilt locally; not installed or runtime-accepted')

if __name__=='__main__':main()
