"""Run current pure-Java ownership/ledger regressions; no ADB/device imports."""
from pathlib import Path
import os,subprocess,tempfile
here=Path(__file__).resolve().parent;carplay=here.parent
sources=[carplay/'src'/n for n in ['ConnectionPolicy.java','OemAttemptSnapshot.java','OemAttemptOwner.java']]+[carplay/'diagnostics/SessionLedger.java',here/'OemAttemptOwnerTest.java',here/'SessionLedgerTest.java']
with tempfile.TemporaryDirectory(prefix='ev6-v16-host-') as work:
    java=os.environ.get('JAVA','java')
    subprocess.run([java,'-jar',os.environ['ECJ_JAR'],'-source','1.7','-target','1.7','-bootclasspath',os.environ['ANDROID_JAR'],'-d',work,*map(str,sources)],check=True)
    for name in ['OemAttemptOwnerTest','SessionLedgerTest']:subprocess.run([java,'-cp',work,name],check=True)
