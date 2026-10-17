/* Diagnostic MainHighAudio/media drain. Linux 3.18 nonblocking DRAIN may
 * return EAGAIN even when complete: issue it ONCE and inspect PCM STATUS.
 * ARM32 ABI is compile-asserted in verify_pcm_status_abi.c. Stop-reason
 * classification is unavailable at this hook: retain a bounded wait and
 * original focus/mute/resource callbacks, exiting on other PCM states.
 */
.global capability_media_drain
.type capability_media_drain,%function
capability_media_drain:
 push.w {r0,r1,r2,r3,r5,r7,r8,lr}
 sub sp,#128
 ldr r0,[r4,#0x4c]
 cmp r0,#102
 bne drain_passthrough
 ldr r0,[r4,#0x50]
 cmp r0,#2
 bne drain_passthrough
 ldr r0,[r4,#0x58]
 movw r1,#44100
 cmp r0,r1
 beq drain_rate_valid
 movw r1,#48000
 cmp r0,r1
 bne drain_passthrough
drain_rate_valid:
 ldr r2,[r4,#0x5c]
 cmp r2,#0
 beq drain_passthrough
 cmp.w r2,#4096
 bhi drain_passthrough
 ldr r1,[r4,#0x60]
 cmp r1,#0
 beq drain_passthrough
 cmp r1,#64
 bhi drain_passthrough
 adds r3,r1,#1
 str r3,[sp,#12]
 mul r3,r1,r2
 ldr r1,[r6,#0x10]
 cmp r3,r1
 bne drain_passthrough
 cmp.w r3,#8192
 bhi drain_passthrough
 movw r1,#0x4240
 movt r1,#0x000f
 muls r2,r1,r2
 add r2,r0
 subs r2,#1
 udiv r2,r2,r0
 movw r1,#1000
 muls r2,r1,r2
 str r2,[sp,#4]
 movs r0,#0
 str r0,[sp]
 str r0,[sp,#44]
 str r0,[sp,#124]
 movs r0,#255
 str r0,[sp,#16]
 ldr r5,[r6]
 cmp r5,#0
 blt drain_passthrough
 mov r0,r5
 movs r1,#3
 movs r7,#55
 svc #0
 cmp r0,#0
 blt drain_error_unset
 mov r8,r0
 orr r2,r0,#0x800
 mov r0,r5
 movs r1,#4
 movs r7,#55
 svc #0
 cmp r0,#0
 blt drain_error_unset
 /* Check first: Linux snd_pcm_drain can wait for power even with NONBLOCK.
  * Avoid entering it for suspended/disconnected/paused/error states. */
 bl drain_read_status
 cmp r0,#0
 bne drain_error
 ldr r0,[sp,#16]
 cmp r0,#1
 beq drain_success
 subs r0,#2
 cmp r0,#1
 bhi drain_state_abort
 mov r0,r5
 movw r1,#0x4144
 movs r2,#0
 movs r7,#54
 svc #0
 cmp r0,#0
 beq drain_status
 cmn r0,#11
 bne drain_error
drain_status:
 bl drain_read_status
 cmp r0,#0
 bne drain_error
 ldr r0,[sp,#16]
 cmp r0,#1
 beq drain_success
 cmp r0,#5
 bne drain_state_abort
 ldr r0,[sp,#12]
 subs r0,#1
 str r0,[sp,#12]
 bmi drain_timeout
 mov r0,sp
 movs r1,#0
 movs r7,#162
 svc #0
 cmp r0,#0
 bne drain_error
 b drain_status
drain_success:
 ldr r0,[sp,#44]
 cmp r0,#0
 bne drain_state_abort
 movs r0,#0
 b drain_restore_flags
drain_timeout:
 movs r0,#3
 b drain_restore_flags
drain_state_abort:
 movs r0,#4
 b drain_restore_flags
drain_error:
 str r0,[sp,#124]
 movs r0,#2
drain_restore_flags:
 str r0,[sp,#8]
 mov r0,r5
 movs r1,#4
 mov r2,r8
 movs r7,#55
 svc #0
 cmp r0,#0
 blt drain_error_unset
 ldr r2,[sp,#8]
 b drain_record
drain_error_unset:
 str r0,[sp,#124]
 movs r2,#2
drain_record:
 ldr r0,[sp,#124]
 movs r3,#5
 bl capability_record
 ldr r0,[sp,#44]
 ldr r2,[sp,#16]
 movs r3,#3
 bl capability_record
drain_passthrough:
 add sp,#128
 pop.w {r0,r1,r2,r3,r5,r7,r8,lr}
 /* Replay the two original PC-relative literals at 0x5a9e/0x5aa0. */
 movw r0,#0xf0ab
 movt r0,#0xffff
 movw r7,#0x4618
 b.w original_playback_close_continue
drain_read_status:
 mov r3,lr
 mov r0,r5
 movw r1,#0x4120
 movt r1,#0x806c
 add r2,sp,#16
 movs r7,#54
 svc #0
 bx r3
.balign 4
