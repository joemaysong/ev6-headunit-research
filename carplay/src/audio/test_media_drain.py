"""Execute the patched ARM bytes against Linux 3.18 nonblocking PCM semantics.

No vehicle or host audio is opened. DRAIN always-EAGAIN followed by STATUS
completion is the principal regression; syscalls alone are emulated.
"""
import json
import struct
import sys
from pathlib import Path
from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM, UC_MODE_THUMB, UC_HOOK_INTR
from unicorn.arm_const import *
from metadata import decode_capability_records

if len(sys.argv)!=2:raise SystemExit('Usage: test_media_drain.py /private-work/audio-build')
HERE = Path(sys.argv[1]).resolve()
MANIFEST = json.loads((HERE/'capability-candidate-manifest.json').read_text())


def run(stream=102, kind=2, rate=48000, period=1024, count=4, buffer=4096,
        fd=17, drain_return=-11, statuses=((3,4096),(5,2048),(5,0),(1,0)),
        get_flags=2, set_flags=(0,0), sleep_return=0, metadata_open=18):
    uc = Uc(UC_ARCH_ARM, UC_MODE_THUMB)
    uc.mem_map(0, 0x80000)
    with (HERE/'libCarPlayAudio-wireless-candidate.so').open('rb') as f:
        for segment in ELFFile(f).iter_segments():
            if segment['p_type']=='PT_LOAD':
                uc.mem_write(segment['p_vaddr'], segment.data())
    uc.mem_map(0x100000, 0x10000)
    state, pcm, sp = 0x11000, 0x12000, 0x10fff0
    for offset, value in [(0x4c,stream),(0x50,kind),(0x58,rate),(0x5c,period),(0x60,count)]:
        uc.mem_write(state+offset, struct.pack('<I',value))
    uc.mem_write(pcm, struct.pack('<I',fd & 0xffffffff))
    uc.mem_write(pcm+16, struct.pack('<I',buffer))
    registers = [UC_ARM_REG_R0,UC_ARM_REG_R1,UC_ARM_REG_R2,UC_ARM_REG_R3,
                 UC_ARM_REG_R4,UC_ARM_REG_R5,UC_ARM_REG_R6,UC_ARM_REG_R7,
                 UC_ARM_REG_R8,UC_ARM_REG_R9,UC_ARM_REG_R10,UC_ARM_REG_R11,
                 UC_ARM_REG_R12,UC_ARM_REG_LR,UC_ARM_REG_SP]
    saved = dict(zip(registers,[123,456,789,987,state,654,pcm,321,222,333,444,555,666,0x7777,sp]))
    for reg,value in saved.items():
        uc.reg_write(reg,value)
    # Poison status memory so uninitialised/overlapping reads cannot pass.
    uc.mem_write(sp-224,b'\xa5'*224)
    uc.mem_write(sp,b'\x5a'*16)
    calls, records, waits = [], [], []
    drains = status_calls = sets = 0

    def interrupt(engine, number, _):
        nonlocal drains,status_calls,sets
        syscall = engine.reg_read(UC_ARM_REG_R7)
        r = [engine.reg_read(reg) for reg in registers[:3]]
        calls.append((syscall,*r))
        result = 0
        if syscall==55:
            assert r[0]==fd and r[1] in (3,4)
            if r[1]==3:
                result=get_flags
            else:
                assert r[2]==(get_flags|0x800 if sets==0 else get_flags)
                result=set_flags[min(sets,len(set_flags)-1)]
                sets+=1
        elif syscall==54:
            assert r[0]==fd
            if r[1]==0x4144:
                drains+=1
                assert drains==1, 'DRAIN must never be reissued to discover completion'
                result=drain_return
            else:
                assert r[1]==0x806c4120 and r[2]==sp-160+16
                value=statuses[min(status_calls,len(statuses)-1)]
                status_calls+=1
                if isinstance(value,int):
                    result=value
                else:
                    status,delay=value
                    output=bytearray(108)
                    struct.pack_into('<I',output,0,status)
                    struct.pack_into('<i',output,28,delay)
                    engine.mem_write(r[2],bytes(output))
        elif syscall==162:
            secs,nsecs=struct.unpack('<II',engine.mem_read(r[0],8))
            assert secs==0 and nsecs==((period*1000000+rate-1)//rate)*1000
            assert r[1]==0
            waits.append(nsecs)
            result=sleep_return
        elif syscall==5:
            path=bytes(engine.mem_read(r[0],64)).split(b'\0',1)[0]
            assert path==b'/data/local/tmp/ev6-pcm' and r[1:]==[1025,0]
            result=metadata_open
        elif syscall==4:
            assert r[0]==18 and r[2]==16
            records.append(bytes(engine.mem_read(r[1],16)))
            result=16
        elif syscall==6:
            assert r[0]==18
        else:
            raise AssertionError(syscall)
        engine.reg_write(UC_ARM_REG_R0,result & 0xffffffff)

    uc.hook_add(UC_HOOK_INTR,interrupt)
    uc.emu_start(MANIFEST['functions']['capability_media_drain']|1,0x5aa2,count=20000)
    assert uc.reg_read(UC_ARM_REG_PC)==0x5aa2
    for reg,value in saved.items():
        if reg not in (UC_ARM_REG_R0,UC_ARM_REG_R7):
            assert uc.reg_read(reg)==value,(reg,uc.reg_read(reg),value)
    assert uc.reg_read(UC_ARM_REG_R0)==0xfffff0ab
    assert uc.reg_read(UC_ARM_REG_R7)==0x4618
    assert bytes(uc.mem_read(sp-224,32))==b'\xa5'*32
    assert bytes(uc.mem_read(sp,16))==b'\x5a'*16
    assert len(waits)<=count+1
    return dict(calls=calls,records=decode_capability_records(b''.join(records)),
                drains=drains,waits=waits,sets=sets,status_calls=status_calls)


def verify(name, expected, pcm_state, delay, errno=0, drains=1, **kwargs):
    result=run(**kwargs)
    assert result['records']==[
        dict(direction=5,result=expected,syscall_result=errno,
             classification={0:'completed',2:'syscall_error',3:'wait_limit',4:'state_abort'}[expected]),
        dict(direction=3,pcm_state=pcm_state,delay_frames=delay)],(name,result)
    assert result['drains']==drains,(name,result)
    if result['sets']==2:
        assert [call for call in result['calls'] if call[0]==55][-1][3]==kwargs.get('get_flags',2)
    return name


cases=[]
for rate in (44100,48000):
    cases.append(verify(f'{rate}-EAGAIN-to-SETUP',0,1,0,rate=rate))
cases.append(verify('already-SETUP',0,1,0,drains=0,statuses=((1,0),)))
cases.append(verify('prepared-immediate',0,1,0,statuses=((2,4096),(1,0))))
cases.append(verify('DRAIN-zero-still-check-state',0,1,0,drain_return=0))
cases.append(verify('actual-wait-limit',3,5,624,statuses=((3,4096),(5,624))))
for state in (0,4,6,7,8):
    cases.append(verify(f'pre-state-{state}',4,state,0,drains=0,statuses=((state,0),)))
for state in (0,2,3,4,6,7,8):
    cases.append(verify(f'post-state-{state}',4,state,0,statuses=((3,4096),(state,0))))
cases.append(verify('SETUP-nonzero-delay-not-success',4,1,64,statuses=((3,4096),(1,64))))
cases.append(verify('get-flags-error',2,255,0,errno=-9,drains=0,get_flags=-9))
cases.append(verify('set-nonblock-error',2,255,0,errno=-1,drains=0,set_flags=(-1,)))
cases.append(verify('pre-status-error',2,255,0,errno=-5,drains=0,statuses=(-5,)))
cases.append(verify('post-status-error',2,3,4096,errno=-5,statuses=((3,4096),-5)))
cases.append(verify('DRAIN-error',2,3,4096,errno=-5,drain_return=-5))
cases.append(verify('sleep-interrupted',2,5,2048,errno=-4,sleep_return=-4))
cases.append(verify('restore-flags-error',2,1,0,errno=-1,set_flags=(0,-1)))
cases.append(verify('already-nonblocking',0,1,0,get_flags=2050))
cases.append(verify('largest-period',3,5,624,rate=44100,period=4096,count=2,
                    buffer=8192,statuses=((3,8192),(5,624))))
assert run(metadata_open=-2)['records']==[]
for args in [dict(stream=100),dict(kind=3),dict(rate=96000),dict(buffer=2048),
             dict(count=0),dict(count=65),dict(period=0),dict(period=4097),
             dict(period=4096,count=3,buffer=12288),dict(fd=-1)]:
    result=run(**args)
    assert result['calls']==[] and result['records']==[],args
    cases.append('passthrough-'+str(args))
# Binary parser must reject unknown directions, out-of-contract fields and
# truncated records, and retain CR/LF/non-ASCII bytes within numeric values.
assert decode_capability_records(struct.pack('<4I',0x41435645,3,5,2573))[0]['delay_frames']==2573
for record in [b'x',struct.pack('<4I',0,5,0,0),struct.pack('<4I',0x41435645,5,1,0),
               struct.pack('<4I',0x41435645,3,9,0),struct.pack('<4I',0x41435645,5,2,1)]:
    try:
        decode_capability_records(record)
    except RuntimeError:
        pass
    else:
        raise AssertionError('Invalid record accepted')
print(json.dumps(dict(verified=len(cases)+1,cases=cases,
                     wait_budget_48k_ms=5*21334/1000,
                     result='SINGLE_DRAIN_STATUS_COMPLETION_ABORT_ERRORS_ABI_REGISTERS_STACK_VERIFIED'),indent=2))
