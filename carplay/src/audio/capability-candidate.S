.syntax unified
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
 beq identify
 cmp r4,#102
 bne zero
identify:
 movs r1,#0
 bl capability_match_type
 bne compatibility
 movs r1,#24
 bl capability_match_type
 bne media
 movs r1,#40
 bl capability_match_type
 bne telephony
 movs r1,#60
 bl capability_match_type
 bne speech
 movs r1,#88
 bl capability_match_type
 bne alert
 movs r1,#104
 bl capability_match_type
 bne default_type
 b zero
compatibility:
 cmp r4,#100
 bne alt_default
 cmp r6,#0
 beq eld_main
 movs r0,#49
 lsls r0,r0,#22
 b done
media:
 cmp r6,#1
 bne zero
 cmp r4,#100
 beq media_format
 cmp r4,#102
 bne zero
media_format:
 movs r0,#1
 lsls r0,r0,#23
 b done
telephony:
 cmp r4,#100
 bne zero
 b eld_main
speech:
 cmp r4,#100
 bne zero
 movs r0,#1
 lsls r0,r0,#27
 b done
alert:
 cmp r4,#100
 bne zero
 b eld_alt
default_type:
 cmp r4,#100
 bne alt_default
eld_main:
 movs r0,#3
 lsls r0,r0,#26
 b done
alt_default:
 cmp r4,#101
 bne zero
eld_alt:
 cmp r6,#1
 bne zero
 movs r0,#1
 lsls r0,r0,#31
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
 movw r2,#48000
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
 movw r2,#48000
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

.balign 4
capability_record:
 push {r4,r7,lr}
 sub sp,#20
 str r0,[sp,#12]
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
 .asciz "/data/local/tmp/ev6-pcm"
.balign 4

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
