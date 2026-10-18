"""Build button with explicit output/signing options; never create a key."""
import runpy,sys
from pathlib import Path
sys.argv.insert(1,'button')
runpy.run_path(str(Path(__file__).resolve().parents[2]/'rebuild.py'),run_name='__main__')
