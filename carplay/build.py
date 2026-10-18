"""Compatibility entry for the controller-only build; no installation."""
import runpy,sys
from pathlib import Path
sys.argv.insert(1,'controller')
runpy.run_path(str(Path(__file__).with_name('rebuild.py')),run_name='__main__')
