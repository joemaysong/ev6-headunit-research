"""Hash-locked, wireless-only capability candidate. Produces a local copy only."""
from pathlib import Path
import hashlib
import json
import struct
import subprocess
import os
import re
import sys
sys.path.insert(0, '/tmp/ev6-native-analysis')
from elftools.elf.elffile import ELFFile
from capstone import Cs, CS_ARCH_ARM, CS_MODE_THUMB

HERE=Path(__file__).resolve().parent
EXPECTED='77aad93fe8ec7ec93974102a07f1e5fdc3a0259e501cde251c6128ac6fa0790c'
START=0x89e0
LIMIT=0x8d84

def branch(source,target):
    delta=target-source-4
    assert delta%2==0 and -(1<<24)<=delta<(1<<24)
    value=delta&((1<<25)-1)
    s=(value>>24)&1;i1=(value>>23)&1;i2=(value>>22)&1
    return struct.pack('<HH',0xf000|(s<<10)|((value>>12)&1023),0x9000|((1^i1^s)<<13)|((1^i2^s)<<11)|((value>>1)&2047))

def main():
    metadata='--metadata' in sys.argv
    main_high='--main-high' in sys.argv
    media_latency='--media-latency' in sys.argv
    drain_media='--drain-media' in sys.argv
    media_48k='--media-48k' in sys.argv
    if drain_media and not (main_high and metadata):raise ValueError('Drain trial requires main-high and metadata')
    if media_latency and not main_high:raise ValueError('Media latency requires main-high formats')
    if media_48k and not (main_high and media_latency):raise ValueError('48k trial requires matching main-high formats and latency row')
    original=HERE/'libCarPlayAudio.so'
    data=bytearray(original.read_bytes())
    assert hashlib.sha256(data).hexdigest()==EXPECTED
    assert not any(data[START:LIMIT])
    asm='''.syntax unified
.thumb
.section .text,"ax",%progbits
.global capability_input, capability_output
.type capability_input,%function
capability_input:
 cmp r2,#1
 beq input_wireless
 push {r4,r5,r6,r7,lr}
 movw r3,#0x6592
 b.w original_input_continue
input_wireless:
 movs r3,#0
 b shared
.type capability_output,%function
capability_output:
 cmp r2,#1
 beq output_wireless
 push {r4,r5,r6,r7,lr}
 movw r3,#0x64ca
 b.w original_output_continue
output_wireless:
 movs r3,#1
shared:
 push {r4,r5,r6,r7,lr}
 sub sp,#4
 mov r4,r0
 mov r5,r1
 mov r6,r3
 adr.w r12,capability_anchor
 movw r7,#:lower16:capability_anchor
 sub.w r7,r12,r7
 cmp r5,#0
 beq zero
 cmp r4,#100
 beq identify
 cmp r4,#101
 bne zero
identify:
'''
    for address,label in [(0x73f4,'compatibility'),(0x740c,'media'),(0x741c,'telephony'),(0x7430,'speech'),(0x744c,'alert'),(0x745c,'default_type')]:
        asm+=f' mov r0,r5\n movw r1,#{address}\n add r1,r7\n blx original_cf_equal\n cmp r0,#0\n bne {label}\n'
    asm+=''' b zero
compatibility:
 cmp r4,#100
 bne alt_default
 cmp r6,#0
 beq eld_main
 movw r0,#0
 movt r0,#0x0c40
 b done
media:
 cmp r6,#1
 bne zero
 cmp r4,#100
 bne zero
 movw r0,#0
 movt r0,#0x0040
 b done
telephony:
 cmp r4,#100
 bne zero
 b eld_main
speech:
 cmp r4,#100
 bne zero
 movw r0,#0
 movt r0,#0x0800
 b done
alert:
 cmp r4,#100
 bne zero
 b eld_alt
default_type:
 cmp r4,#100
 bne alt_default
eld_main:
 movw r0,#0
 movt r0,#0x0c00
 b done
alt_default:
 cmp r4,#101
 bne zero
eld_alt:
 cmp r6,#1
 bne zero
 movw r0,#0
 movt r0,#0x8000
 b done
zero:
 movs r0,#0
done:
 movs r1,#0
 add sp,#4
 pop {r4,r5,r6,r7,pc}
.balign 4
capability_anchor:
 nop
'''
    if main_high:
        asm=asm.replace(' cmp r4,#101\n bne zero\nidentify:', ' cmp r4,#101\n beq identify\n cmp r4,#102\n bne zero\nidentify:')
        asm=asm.replace('media:\n cmp r6,#1\n bne zero\n cmp r4,#100\n bne zero', 'media:\n cmp r6,#1\n bne zero\n cmp r4,#100\n beq media_format\n cmp r4,#102\n bne zero\nmedia_format:')
        asm+='''
.global capability_formats
.type capability_formats,%function
capability_formats:
 push {r4,r5,r6,lr}
 sub sp,#8
 mov r4,r0
 mov r5,r1
 bl capability_original_formats
 mov r6,r0
 cmp r4,#1
 bne formats_done
 cmp r6,#0
 beq formats_done
 adr.w r12,capability_anchor
 movw r3,#:lower16:capability_anchor
 sub.w r12,r12,r3
 movw r1,#0x740c
 add r1,r12
 movs r0,#102
 movs r2,#1
 mov r3,sp
 bl original_create_formats
 mov r4,r0
 ldr r0,[sp]
 cmp r0,#0
 bne formats_failed
 cmp r4,#0
 beq formats_done
 mov r0,r6
 mov r1,r4
 blx original_array_append
 mov r0,r4
 blx original_cf_release
 b formats_done
formats_failed:
 cmp r5,#0
 it ne
 strne r0,[r5]
 cmp r4,#0
 beq formats_release_array
 mov r0,r4
 blx original_cf_release
formats_release_array:
 mov r0,r6
 blx original_cf_release
 movs r6,#0
formats_done:
 mov r0,r6
 add sp,#8
 pop {r4,r5,r6,pc}
capability_original_formats:
 push {r4,r5,r6,r7,lr}
 movw r3,#0x6222
 b.w original_formats_continue
.balign 4
'''
    if media_latency:
        asm+='''
.global capability_latencies
.type capability_latencies,%function
capability_latencies:
 push {r4,r5,r6,r7,lr}
 sub sp,#20
 mov r4,r0
 mov r5,r1
 bl capability_original_latencies
 mov r6,r0
 cmp r4,#1
 bne latencies_done
 cmp r6,#0
 beq latencies_done
 movs r0,#2
 str r0,[sp]
 movs r0,#102
 movs r1,#2
 movw r2,#44100
 movs r3,#16
 bl original_default_latency
 mov r7,r0
 adr.w r12,capability_anchor
 movw r3,#:lower16:capability_anchor
 sub.w r12,r12,r3
 movw r1,#0x740c
 add r1,r12
 movs r0,#2
 str r0,[sp]
 movs r0,#0
 str r0,[sp,#4]
 str r7,[sp,#8]
 add r0,sp,#16
 str r0,[sp,#12]
 movs r0,#102
 movw r2,#44100
 movs r3,#16
 bl original_create_latency
 mov r4,r0
 ldr r0,[sp,#16]
 cmp r0,#0
 bne latencies_failed
 cmp r4,#0
 beq latencies_done
 mov r0,r6
 mov r1,r4
 blx original_array_append
 mov r0,r4
 blx original_cf_release
 b latencies_done
latencies_failed:
 cmp r5,#0
 it ne
 strne r0,[r5]
 cmp r4,#0
 beq latencies_release_array
 mov r0,r4
 blx original_cf_release
latencies_release_array:
 mov r0,r6
 blx original_cf_release
 movs r6,#0
latencies_done:
 mov r0,r6
 add sp,#20
 pop {r4,r5,r6,r7,pc}
capability_original_latencies:
 push.w {r4,r5,r6,r7,r8,r9,lr}
 b.w original_latencies_continue
.balign 4
'''
    if media_48k:
        # Controlled comparison: keep the previous 44.1k candidate separately.
        # Advertise only 48k for media so the live trial cannot silently select
        # 44.1k. Other audio types and all wired paths keep their original logic.
        media_mask=' movt r0,#0x0040\n b done\n'
        assert asm.count(media_mask)==1
        asm=asm.replace(media_mask,' movt r0,#0x0080\n b done\n')
        assert asm.count(' movw r2,#44100\n')==2
        asm=asm.replace(' movw r2,#44100\n',' movw r2,#48000\n')
    if metadata:
        for name,direction in ([] if drain_media else [('capability_input',0),('capability_output',1)]):
            asm=asm.replace(name+':\n',name+':\n push {r0,r1,r2,r3,lr}\n sub sp,#4\n movs r3,#'+str(direction)+'\n bl capability_record\n add sp,#4\n pop {r0,r1,r2,r3,lr}\n',1)
        asm+='''
.balign 4
capability_record:
 push {r4,r7,lr}
 sub sp,#20
 cmp r0,#100
 blo record_unknown_stream
 cmp r0,#102
 bls record_stream_ready
record_unknown_stream:
 movs r0,#0
record_stream_ready:
 str r0,[sp,#12]
 cmp r2,#2
 it hi
 movhi r2,#3
 str r2,[sp,#8]
 str r3,[sp,#4]
 movw r1,#0x5645
 movt r1,#0x4143
 str r1,[sp]
 adr.w r0,capability_record_path
 movw r1,#1025
 movs r2,#0
 movs r7,#5
 svc #0
 cmp r0,#0
 blt record_done
 mov r4,r0
 mov r1,sp
 movs r2,#16
 movs r7,#4
 svc #0
 mov r0,r4
 movs r7,#6
 svc #0
record_done:
 add sp,#20
 pop {r4,r7,pc}
.balign 4
capability_record_path:
 .asciz "/data/local/tmp/ev6-audio-capability-events/events"
.balign 4
'''
    if drain_media:
        # Drain records use a closed numeric schema: state/delay/errno must
        # survive unchanged rather than being clamped as capability IDs.
        begin=asm.index(' cmp r0,#100\n',asm.index('capability_record:'))
        end=asm.index(' str r2,[sp,#8]\n',begin)
        asm=asm[:begin]+' str r0,[sp,#12]\n'+asm[end:]
        asm=asm.replace('/data/local/tmp/ev6-audio-capability-events/events',
                        '/data/local/tmp/ev6-pcm')
        # Share CFEqual setup without changing any matched type or result.
        for address,label in [(0x73f4,'compatibility'),(0x740c,'media'),(0x741c,'telephony'),(0x7430,'speech'),(0x744c,'alert'),(0x745c,'default_type')]:
            old=f' mov r0,r5\n movw r1,#{address}\n add r1,r7\n blx original_cf_equal\n cmp r0,#0\n bne {label}\n'
            assert asm.count(old)==1
            asm=asm.replace(old,f' movs r1,#{address-0x73f4}\n bl capability_match_type\n bne {label}\n')
        asm+='''
capability_match_type:
 push {r3,lr}
 mov r0,r5
 movw r3,#0x73f4
 add r1,r3
 add r1,r7
 blx original_cf_equal
 cmp r0,#0
 pop {r3,pc}
.balign 4
'''
        # Equivalent Thumb constants retain the existing executable cave.
        def compact_mask(match):
            mask=int(match[1],16)<<16
            shift=(mask & -mask).bit_length()-1
            immediate=mask>>shift
            assert 0<immediate<256
            return f' movs r0,#{immediate}\n lsls r0,r0,#{shift}\n'
        asm=re.sub(r' movw r0,#0\n movt r0,#0x([0-9a-fA-F]+)\n',compact_mask,asm)
        asm+=(HERE/'media-drain.S').read_text()
    (HERE/'capability-candidate.S').write_text(asm)
    (HERE/'capability-candidate.ld').write_text('original_playback_close_continue = 0x5aa2; original_input_continue = 0x3ab0; original_output_continue = 0x3b78; original_formats_continue = 0x3e20; original_create_formats = 0x3cd0; original_latencies_continue = 0x30d8; original_default_latency = 0x4a2c; original_create_latency = 0x2f48; original_array_append = 0x26d4; original_cf_release = 0x26e0; original_cf_equal = 0x26ec; SECTIONS { . = 0x89e0; .text : { *(.text*) } /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.note*) } }')
    env=dict(os.environ,ZIG_GLOBAL_CACHE_DIR='/tmp/ev6-audio-zig-cache',ZIG_LOCAL_CACHE_DIR='/tmp/ev6-audio-zig-local')
    subprocess.run(['/tmp/ev6-audio-toolchain/ziglang/zig','cc','-target','arm-linux-gnueabi','-mcpu=cortex_a15','-nostdlib','-no-pie','-Wl,-T,'+str(HERE/'capability-candidate.ld'),'-Wl,-e,capability_input',str(HERE/'capability-candidate.S'),'-o',str(HERE/'capability-candidate.elf')],env=env,check=True,capture_output=True)
    with (HERE/'capability-candidate.elf').open('rb') as f:
        elf=ELFFile(f);section=elf.get_section_by_name('.text');code=section.data()
        symbols={s.name:s['st_value']&~1 for s in elf.get_section_by_name('.symtab').iter_symbols()}
        assert section['sh_addr']==START and START+len(code)<=LIMIT, (len(code),LIMIT-START)
    data[START:START+len(code)]=code
    patches=[(0x3aac,'capability_input'),(0x3b74,'capability_output')]
    if main_high:patches.append((0x3e1c,'capability_formats'))
    if media_latency:patches.append((0x30d4,'capability_latencies'))
    if drain_media:patches.append((0x5a9e,'capability_media_drain'))
    for address,name in patches:
        patch=branch(address,symbols[name]);data[address:address+4]=patch
        decoded=list(Cs(CS_ARCH_ARM,CS_MODE_THUMB).disasm(patch,address))
        assert len(decoded)==1 and decoded[0].mnemonic=='b.w' and int(decoded[0].op_str[1:],16)==symbols[name]
    # Extend only the existing executable PT_LOAD into its unused zero padding.
    phoff=struct.unpack_from('<I',data,28)[0];phnum=struct.unpack_from('<H',data,44)[0]
    for i in range(phnum):
        offset=phoff+i*32;p=struct.unpack_from('<8I',data,offset)
        if p[0]==1 and p[1]==0 and p[2]==0:
            assert p[4]==START and p[5]==START and p[6]==5
            struct.pack_into('<II',data,offset+16,START+len(code),START+len(code));break
    else:raise RuntimeError('Expected executable segment not found')
    candidate=HERE/'libCarPlayAudio-wireless-candidate.so';candidate.write_bytes(data)
    result={'original_sha256':EXPECTED,'candidate_sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'patch_code_start':START,'patch_code_end':START+len(code),'functions':{k:v for k,v in symbols.items() if k.startswith('capability_')},'wireless_transport_argument':1,'wired_original_path_retained':True,'metadata_only_diagnostic':metadata,'main_high_media_row':main_high,'deployed':False,'note':'Candidate only. Must pass offline matrix and controlled live trial; no runtime authentication or media changes.'}
    result['media_latency_row']=media_latency
    result['media_pcm_drain']=drain_media
    if drain_media:
        result['drain_completion']='single_nonblocking_drain_then_arm32_pcm_status_setup'
        result['pcm_status_abi']={'bytes':108,'ioctl':0x806c4120,'state_offset':0,'delay_offset':28}
        result['drain_metadata_file']='/data/local/tmp/ev6-pcm'
    result['media_sample_rate']=48000 if media_48k else 44100
    (HERE/'capability-candidate-manifest.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))

if __name__=='__main__':main()
