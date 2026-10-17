import struct

def decode_capability_records(data):
    if len(data)%16:raise RuntimeError('Incomplete capability metadata')
    rows=[]
    for magic,direction,transport,stream in struct.iter_unpack('<4I',data):
        if magic!=0x41435645:raise RuntimeError('Invalid capability metadata magic')
        if direction in (0,1,2) and transport in range(4) and stream in (0,100,101,102):
            rows.append(dict(direction=direction,transport=transport,stream=stream))
        elif direction==3 and transport in (*range(9),255):
            delay=struct.unpack('<i',struct.pack('<I',stream))[0]
            if not -1048576<=delay<=1048576:raise RuntimeError('Invalid PCM delay')
            rows.append(dict(direction=direction,pcm_state=transport,delay_frames=delay))
        elif direction==5 and transport in (0,2,3,4):
            result=struct.unpack('<i',struct.pack('<I',stream))[0]
            if not -4095<=result<=0:raise RuntimeError('Invalid PCM syscall result')
            rows.append(dict(direction=direction,result=transport,syscall_result=result,
                             classification={0:'completed',2:'syscall_error',3:'wait_limit',4:'state_abort'}[transport]))
        else:
            raise RuntimeError('Invalid capability metadata')
    return rows

