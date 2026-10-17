# Audio capability negotiation and PCM drain

## Decoder availability and negotiation were different problems

The firmware already contained an AAC-LC decoder. A local synthetic comparison
decoded raw/ADTS fixtures to the same 23 frames / 23,552 PCM frames and matching
output checksum. Adding another decoder was therefore not the first repair.

The wireless media capability branch lacked the AAC-LC media row/format bits.
The phone reported a format-parameter error (`-6705`), exposed no useful CarPlay
audio route, and played on its own speaker. Receiver MainAudio disable preceded
a return to Nature Sound by about 40 ms. That sequence did not establish Nature
Sound as the initiating fault.

The hash-locked patch reuses the OEM format construction path for the wireless
media entry. It preserves the other capability rows and wired behavior. Local
capability checks covered 108 cases, including 72 unchanged non-wireless cases.
After correcting the format table, the same test sound played through the car.

## Validated media format

[Apple's WWDC 2016 system guidance](https://developer.apple.com/videos/play/wwdc2016/722/)
distinguishes LPCM on wired CarPlay from compressed wireless audio: AAC-LC for
media and OPUS or AAC-ELD for other audio. The actual negotiated values below
come from this vehicle's tests.

| Layer | Observed or advertised value | Interpretation |
|---|---|---|
| Wireless media codec | AAC-LC | Compressed transport, decoded by the existing implementation |
| Sample rate | 48,000 Hz | Validated alternate capability selection |
| Channels | 2 | Stereo media |
| AAC frame duration | 1,024 samples | Codec frame size in this negotiated path |
| Hardware PCM | S16_LE, 48,000 Hz, 2 channels | Uncompressed receiver output |
| ALSA period/buffer | 1,024 / 4,096 frames | Observed hardware parameters |
| Encoder configuration | 192 kb/s advertised setting | Not a measurement of instantaneous compressed bitrate |

The earlier 44.1 kHz media path was also tested. The correction selects the
matching rate bit (`0x800000` for this 48 kHz branch, versus `0x400000` for the
tested 44.1 kHz branch); it does not arbitrarily relabel 44.1 kHz PCM as 48 kHz.

Siri, calls, alerts, and other stream types have their own capabilities. A claim
that every audio stream uses stereo 48 kHz AAC-LC would be incorrect. Wired
CarPlay also ultimately feeds PCM into the receiver's audio hardware; that does
not imply the compressed wireless transport and wired transport are identical.

## The clipped final syllable

The remaining short-sound defect was at close. A sample showed 3,264 queued
frames, roughly 74 ms at 44.1 kHz, while close could complete within 50.973 ms.
The TinyALSA close/stop path dropped pending data instead of waiting for playback
completion. Increasing the advertised network/audio latency did not prove a
fix and was not retained as the solution.

The initial idea of repeatedly issuing nonblocking DRAIN until return zero was
incorrect for the tested Linux 3.18 behavior. DRAIN can start draining with
`-EAGAIN`; reissuing it is not the completion predicate.

The final patch issues DRAIN once, then observes the ARM32 PCM STATUS ABI:
108-byte structure, state offset 0, delay offset 28, ioctl `0x806c4120`.
Completion is SETUP (`1`) with delay zero. Waiting is bounded using the actual
period/buffer parameters, about 106.67 ms in the tested 48 kHz case before
scheduling/syscall overhead. Error, XRUN, unavailable-status, and bounded-exit
paths are covered in 39 local ARM emulation cases.

The user confirmed normal sound and a complete final syllable with the same
short test asset, first at 48 kHz and again after the drain refinement. Hardware
status independently reached SETUP/delay zero. This is stronger than hearing a
long music track without noticing its ending.

## Scope and remaining validation

The patch is specific to the original library hash and ARM ABI. It does not
change system-wide ALSA policy. End-of-file, explicit user cancel, phone/Siri
priority, and every interruption path have not all been independently classified.
Those transitions, longer load runs, and cross-vehicle audio routing remain in
the validation matrix.

The video capability record advertised 1920 x 720 with a 60 fps maximum.
This is a capability ceiling, not a measured sustained rendered frame rate or
a universal highest-quality setting. Negotiated formats and actual load must
be observed separately.
