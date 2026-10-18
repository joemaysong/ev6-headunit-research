"""Read-only reference input/dependency check. Never select or contact a vehicle."""
import argparse,hashlib,json,struct
from pathlib import Path

def dependencies(path):
    data=path.read_bytes()
    if not data.startswith(b'dey\n036\0'):raise ValueError('Expected original ODEX36: '+path.name)
    offset,size=struct.unpack_from('<II',data,16);block=data[offset:offset+size]
    if len(block)!=size or size<16:raise ValueError('Truncated dependency block')
    count=struct.unpack_from('<I',block,12)[0];cursor=16;result=[]
    for _ in range(count):
        length=struct.unpack_from('<I',block,cursor)[0];cursor+=4
        if length<2 or length>4096 or cursor+length+20>len(block):raise ValueError('Invalid dependency entry')
        name=block[cursor:cursor+length].rstrip(b'\0').decode('ascii');cursor+=length
        signature=block[cursor:cursor+20].hex();cursor+=20
        result.append((Path(name).name,signature))
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--originals',required=True,type=Path);p.add_argument('--framework',required=True,type=Path);a=p.parse_args()
    root=Path(__file__).resolve().parents[2];profile=json.loads((root/'profiles/v16-release.json').read_text())
    missing=[];different=[];matched=[];required={}
    for name,expected in profile['originals'].items():
        path=a.originals/name
        if not path.is_file():missing.append(name);continue
        if hashlib.sha256(path.read_bytes()).hexdigest()!=expected['sha256']:different.append(name);continue
        matched.append(name)
    for name in ['Bluetooth.odex','HKMC_DiPO.odex']:
        if name not in matched:continue
        for dependency,signature in dependencies(a.originals/name):
            if dependency in required and required[dependency]!=signature:raise ValueError('Original dependency contracts disagree')
            required[dependency]=signature
    framework={}
    for name,signature in required.items():
        path=a.framework/name
        if not path.is_file():missing.append('framework/'+name);continue
        data=path.read_bytes()
        if not data.startswith(b'dey\n036\0'):different.append('framework/'+name);continue
        offset=struct.unpack_from('<I',data,8)[0]
        if data[offset+12:offset+32].hex()!=signature:different.append('framework/'+name);continue
        framework[name]={'sha256':hashlib.sha256(data).hexdigest(),'declared_dex_signature_matches':True}
    result={'reference_profile_matches':not missing and not different,'originals_matched':matched,'missing':missing,'different':different,'framework':framework,'vehicle_contacted':False,'signing_material_read':False}
    print(json.dumps(result,indent=2))
    if not result['reference_profile_matches']:raise SystemExit(1)
if __name__=='__main__':main()
