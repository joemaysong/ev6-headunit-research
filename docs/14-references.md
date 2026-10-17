# Public technical references

These are primary references. They explain public APIs, protocol foundations or
upstream behavior; they do not certify this research implementation. The exact
OEM binary behavior was checked locally and those binaries are not distributed.

| Reference | Used for | Boundary |
|---|---|---|
| [Apple WWDC 2017: Developing Wireless CarPlay Systems](https://developer.apple.com/videos/play/wwdc2017/717/) | Setup, authorization, reconnection, explicit device selection and coexistence principles | Public workflow, not complete receiver wire specification |
| [Apple WWDC 2017 presentation PDF](https://devstreaming-cdn.apple.com/videos/wwdc/2017/717tmz5rl6c2t2k/717/717_developing_wireless_carplay_systems.pdf?dl=1) | Reconnection flow at slide 169 and disconnect policy at slide 186, using PDF one-based pages | No fixed local timeout or retry count inferred |
| [Apple WWDC 2016: Developing CarPlay Systems, Part 1](https://developer.apple.com/videos/play/wwdc2016/722/) | Wired LPCM, wireless compressed media, display and audio integration | Advertised formats and design guidance are distinct from measured throughput |
| [Apple MFi program](https://mfi.apple.com/en/how-it-works.html) | Access to CarPlay/iAP2 technical specifications and certification process | This project has not obtained certification |
| [AOSP legacy boot header](https://android.googlesource.com/platform/system/core/+/android-4.4.2_r1/mkbootimg/bootimg.h) | Header layout and page-aligned payload packing | The owner's actual image/hash still determines compatibility |
| [AOSP Android 4.4 adbd](https://android.googlesource.com/platform/system/core/+/android-4.4_r1/adb/adb.c) | Privilege-drop context | OEM instruction sites were separately verified |
| [AOSP Android 4.4.2 WifiApEnabler](https://android.googlesource.com/platform/packages/apps/Settings/+/android-4.4.2_r1/src/com/android/settings/wifi/WifiApEnabler.java) | Save client preference, disable STA, enable AP and restore STA | This head unit uses additional OEM projection-AP APIs |
| [AOSP Android 4.4.2 am](https://android.googlesource.com/platform/frameworks/base/+/android-4.4.2_r1/cmds/am/src/com/android/commands/am/Am.java) | Platform service-start contract | Root app_process is not automatically a registered application context |
| [Linux v3.18.24 PCM native implementation](https://github.com/gregkh/linux/blob/v3.18.24/sound/core/pcm_native.c) | Nonblocking DRAIN and state transitions | Upstream evidence, not a claim to possess the TCC kernel source |
| [Linux v3.18.24 sound UAPI](https://github.com/gregkh/linux/blob/v3.18.24/include/uapi/sound/asound.h) | ARM32 PCM STATUS layout and ioctl | Compile-checked for the actual 32-bit ABI |
| [RFC 6762: Multicast DNS](https://www.rfc-editor.org/rfc/rfc6762.html) | Multicast discovery, live scoped records and TTL behavior | Observed CarPlay TXT identity is a local finding |
| [RFC 6763: DNS-Based Service Discovery](https://www.rfc-editor.org/rfc/rfc6763.html) | SRV/TXT service ownership and discovery | A resolved endpoint still needs selected-device association |
| [RFC 4861: IPv6 Neighbor Discovery](https://www.rfc-editor.org/rfc/rfc4861.html) | Map the live scoped source to an associated Wi-Fi station | Neighbor success alone is not authorization |

Third-party receivers and AAC examples were exploratory references. Their retry
timers, platforms, dependencies and licenses were not adopted as Apple rules.
The deployed path keeps the owner's original authentication, codec and receiver.
See [third-party boundaries](../THIRD_PARTY_NOTICES.md).
