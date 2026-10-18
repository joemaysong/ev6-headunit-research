"""Compile the station reader for ARM32 Linux; never install."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);a=p.parse_args()
cc=os.environ.get('NDK_CC');zig=os.environ.get('ZIG')
if not cc and not zig:p.error('Set NDK_CC (Android API19 ARM) or ZIG (static ARM Linux)')
out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
source=Path(__file__).resolve().parents[1]/'src/native/wl-stations.c'
command=[cc] if cc else [zig,'cc','-target','arm-linux-musleabi','-mcpu=cortex_a15','-static']
subprocess.run([*command,'-std=c99','-Os','-fPIE','-pie','-Wall','-Wextra','-Werror',str(source),'-o',str(out/'wl-stations')],check=True)
data=(out/'wl-stations').read_bytes()
if data[:5]!=b'\x7fELF\x01' or data[18:20]!=b'\x28\x00':raise ValueError('Expected ARM32 ELF')
print(json.dumps({'sha256':hashlib.sha256(data).hexdigest(),'installed':False,'runtime_accepted':False}))
