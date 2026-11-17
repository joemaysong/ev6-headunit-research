# V17 integration source

V17.0.1 promotes candidate102-aarestart with scoped owner validation; see [release notes](../docs/21-v17-maintenance-release.md). The historical V16 build remains in carplay; these author-owned Java sources describe the V17 integration delta and require the matching private OEM dependencies and reviewed patch/build contracts. They are not a standalone flashable distribution.

The controller preserves active-session exclusivity, explicit device switching, manual-stop suppression of reconnect, bounded same-device recovery, on-demand AP and restoration of the prior Wi-Fi preference. USB attached during an active wireless session is denied; after an explicit manual stop, the same still-present attachment can be replayed to the OEM receiver only after matching DONE and fresh physical/owner checks. Wireless switching uses a new request and retains the exclusion hold.

Do not replace V16 runtime files by merely compiling this directory. Firmware-specific integration, ODEX dependencies, signing identity, native completion evidence and private deployment manifest remain required. No OEM binaries, keys or captures are published.

## Candidate102 field-access patch

The authored overlay requires the exact private candidate101 GPM DEX (SHA-256
locked), JAVA and SMALI_CLASSPATH for smali/baksmali 2.5.2. It changes one field
read in the idle AA restart gate, verifies class/method scope and roundtrips the
DEX. It does not reconstruct candidate101 from stock or generate native ODEX.

```sh
python3 v17/build_aa_restart102.py --base-dex "$EV6_PRIVATE/candidate101/HKMC_GPM.dex" --output "$EV6_PRIVATE/candidate102-rebuild"
```

The Java files are authored integration snapshots; framework dependencies and
hook assembly remain private. Do not compile them as a complete standalone app.
