# V17 integration source

V17 promotes owner-tested candidate92-releasefast. The historical V16 build remains in carplay; these author-owned Java sources describe the V17 integration delta and require the matching private OEM dependencies and reviewed patch/build contracts. They are not a standalone flashable distribution.

The controller preserves active-session exclusivity, explicit device switching, manual-stop suppression of reconnect, bounded same-device recovery, on-demand AP and restoration of the prior Wi-Fi preference. USB attached during an active wireless session is denied; after an explicit manual stop, the same still-present attachment can be replayed to the OEM receiver only after matching DONE and fresh physical/owner checks. Wireless switching uses a new request and retains the exclusion hold.

Do not replace V16 runtime files by merely compiling this directory. Firmware-specific integration, ODEX dependencies, signing identity, native completion evidence and private deployment manifest remain required. No OEM binaries, keys or captures are published.
