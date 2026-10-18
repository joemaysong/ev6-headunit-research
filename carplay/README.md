# V16 source and reconstruction map

The current combination is Wired gate16, controller14, Bluetooth exact-work
bridge14 and EV6ATTEMPT13. Runtime source lives in `src`, `smali-provider`,
`smali-backend`, `diagnostics` and `assets`.

| Component | Public reconstruction route | Owner-supplied input |
|---|---|---|
| Root controller/provider | `rebuild.py controller OUTPUT` | Java/ECJ/API19/D8/smali toolchain |
| Backend/button | `rebuild.py backend OUTPUT` / `button OUTPUT` | Existing private signer for updates; stock Iap2ServerApp stays on target |
| DiPO observation + Wired gate | `patches/dipo-v16.json`, `rebuild.py observer OUTPUT --oem-smali INPUT` | Correct pristine DiPO deodex tree |
| Bluetooth work bridge | `patches/bluetooth-v14.json`, `rebuild.py bluetooth OUTPUT --oem-smali INPUT` | Correct pristine Bluetooth deodex tree |
| Device Connections UI | `patch_oem.py btsettings INPUT OUTPUT` | Exact original HKMC_BtSettings ODEX |
| Wireless feature exclusion | `patch_oem.py feature INPUT OUTPUT` | Exact original featurexml.cfg |
| Audio format/drain | `src/audio/build_audio_candidate.py` | Exact original audio library + Zig |
| AP reply/subnet/startup | `assets/*.sh` | Existing OEM network services; per-car original AP state |
| Station reader | `src/native/wl-stations.c`, `tools/build_native.py` | ARM compiler; reconstructed helper requires target validation |
| Native Dalvik cache | `tools/BuildOdex.java`, `rebuild.py builder OUTPUT` | Matching vehicle firmware and isolated app_process build VM |

No OEM business body, firmware, signing identity or exact accepted binary is
redistributed. Recipes contain project wrapper code, integration contracts and
small authored instruction replacements. Source-only reproduction uses the
owner's matching originals. Exact binary restoration uses private frozen assets.

Start with [build instructions](../docs/11-build-and-deployment.md), then the
[complete reproduction/porting workflow](../docs/17-reproduction-and-porting.md).
