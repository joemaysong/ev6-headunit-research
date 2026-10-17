/* ARM EABI assertions against the saved upstream Linux v3.18.24 UAPI. */
#include <stddef.h>
#include <linux/time.h>
#include <asm/ioctl.h>
#define __bitwise
#define __force
#define __user
#include "linux-3.18.24-asound.h"

_Static_assert(sizeof(void *) == 4, "ARM32 target required");
_Static_assert(sizeof(struct timespec) == 8, "old ARM32 timespec required");
_Static_assert(sizeof(struct snd_pcm_status) == 108, "status buffer size");
_Static_assert(__builtin_offsetof(struct snd_pcm_status, state) == 0, "state offset");
_Static_assert(__builtin_offsetof(struct snd_pcm_status, delay) == 28, "delay offset");
_Static_assert(SNDRV_PCM_IOCTL_STATUS == 0x806c4120U, "status ioctl number");
_Static_assert(SNDRV_PCM_STATE_SETUP == 1 && SNDRV_PCM_STATE_DRAINING == 5,
               "PCM completion states");
