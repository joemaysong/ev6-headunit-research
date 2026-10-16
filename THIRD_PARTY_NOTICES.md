# Source and dependency boundaries

This repository publishes research notes, authored integration code, and patch
generators. It does not distribute OEM firmware, decompiled OEM applications,
Apple receiver libraries, MFi material, boot images, signing identities, or
toolchain executables.

The Java and smali integration classes implement missing service contracts and
delegate to the owner's existing OEM components. Symbol names, API names,
addresses, short patch instructions, and firmware hashes document compatibility;
they are not a replacement distribution of those components.

Local builds require separately obtained Android SDK tools, Eclipse ECJ, smali,
R8/D8, Zig, OpenSSL, Capstone, pyelftools, and Unicorn. Obtain them from their
respective upstream projects and retain their notices. They are not vendored.

`carplay/src/audio/verify_pcm_status_abi.c` checks the ARM32 layout against an
owner-supplied Linux v3.18.24 UAPI header. The header is not included. Its upstream
copyright and licensing information must be retained in a local copy:
[Linux v3.18.24 asound.h](https://github.com/gregkh/linux/blob/v3.18.24/include/uapi/sound/asound.h).

No repository-wide redistribution license has been selected by the owner in
this publication. Public visibility is not a claim that OEM or third-party
software is relicensed. Dependency licenses remain with their upstream projects.
