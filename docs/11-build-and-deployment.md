# Build V16 from project sources and owner inputs

All commands below run from the repository root. Outputs and OEM inputs belong
in a private work directory outside Git. Every output directory must be new.
Builders do not install anything, create a key, or establish live acceptance.

## Host tools

Use Python3, a host Java runtime/JDK, ECJ, an Android API19 android.jar, D8/R8,
smali/baksmali 2.5.2 and their dependencies. The environment variables are:

```sh
export JAVA=/path/to/java
export ECJ_JAR=/path/to/ecj.jar
export ANDROID_JAR=/path/to/android-19/android.jar
export R8_JAR=/path/to/r8.jar
export SMALI_CLASSPATH='/path/to/smali-jars/*'
export EV6_PRIVATE=/path/to/owner-private-work
python3 -m pip install -r requirements-dev.txt
```

Obtain tools from their official upstream distributions and record versions and
SHA-256 locally. `JAVA` is an executable, not a Java-home directory. Keep the
wildcard in SMALI_CLASSPATH quoted. For deodex helper compilation also provide
`JAVAC` from a host JDK. The review used Zig0.13.0 for exact audio reconstruction.

## Controller, backend and button

```sh
python3 carplay/rebuild.py controller "$EV6_PRIVATE/controller-build"
python3 carplay/rebuild.py backend "$EV6_PRIVATE/backend-build"
python3 carplay/rebuild.py button "$EV6_PRIVATE/button-build"
python3 carplay/tests/run_host.py
```

Backend/button default to **unsigned** APKs. Their manifests are authored in
`carplay/tools/apk.py`; no proprietary APK template is needed. The backend loads
stock `/system/app/Iap2ServerApp.apk` on the vehicle and uses the existing
`com.tcc.iap2` framework; these are runtime prerequisites, never uploads.

For an update use the existing package's own retained certificate/key and a
private copy of the installed APK. The tool verifies certificate/key agreement,
compares the existing APK signer, signs with the same identity and verifies its
PKCS7 signature. Example:

```sh
python3 carplay/rebuild.py backend "$EV6_PRIVATE/backend-signed" \
  --signing-cert "$EV6_PRIVATE/signing/backend/cert.pem" \
  --signing-key "$EV6_PRIVATE/signing/backend/key.pem" \
  --installed-apk "$EV6_PRIVATE/installed/backend.apk"
```

Do the same for `button` using its own signer. On a genuinely new installation,
prepare a new owner-controlled identity privately and pass `--first-install`
instead of `--installed-apk`. Never use that option to bypass an update mismatch.
Retain the identity beyond the build. No builder silently generates keys.

## Original DiPO/Bluetooth and their authored hooks

First prepare pristine correctly resolved original smali as described in
[the reproduction workflow](17-reproduction-and-porting.md). The recipes verify
the entire original class inventory and canonical instruction hashes; arbitrary
smali or a previously instrumented tree will be refused.

```sh
python3 carplay/rebuild.py observer "$EV6_PRIVATE/observer-build" \
  --oem-smali "$EV6_PRIVATE/pristine-dipo-smali"
python3 carplay/rebuild.py bluetooth "$EV6_PRIVATE/bluetooth-build" \
  --oem-smali "$EV6_PRIVATE/pristine-bluetooth-smali"
python3 carplay/rebuild.py builder "$EV6_PRIVATE/builder-build"
```

These apply the authored recipes, compile the bridges, verify internal method
references, then assemble API19 DEX. Native ODEX36 generation is a later isolated
on-target build step. It requires the exact matching firmware, not an ART VM.
Use a fresh root-private stage, copy builder.jar and one DEX, then run in a
separate app_process VM with the original framework classpath:

```sh
CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:/data/local/tmp/OWNER_REVIEWED_STAGE/builder.jar \
  app_process /system/bin BuildOdex /data/local/tmp/OWNER_REVIEWED_STAGE/INPUT.dex /data/local/tmp/OWNER_REVIEWED_STAGE/OUTPUT.odex
```

The uppercase stage/filenames are placeholders to replace in a reviewed plan.
Require NATIVE_CACHE_BUILT, ODEX36 magic, input/output hashes and unchanged
installed-system hashes. Pull the output privately. Remove only that stage and
its own Dalvik cache after successful export. Never edit ODEX headers or load a
candidate in the live OEM process. Native cache generation has not been rerun
by this repository review.

## UI, feature configuration and audio

```sh
python3 carplay/patch_oem.py btsettings "$EV6_PRIVATE/original/HKMC_BtSettings.odex" "$EV6_PRIVATE/HKMC_BtSettings.odex"
python3 carplay/patch_oem.py feature "$EV6_PRIVATE/original/featurexml.cfg" "$EV6_PRIVATE/featurexml.cfg"
export ZIG=/path/to/zig
python3 carplay/src/audio/build_audio_candidate.py \
  --original "$EV6_PRIVATE/original/libCarPlayAudio.so" --output "$EV6_PRIVATE/audio-build" \
  --metadata --main-high --media-latency --drain-media --media-48k
python3 carplay/src/audio/test_media_drain.py "$EV6_PRIVATE/audio-build"
python3 carplay/tools/build_native.py "$EV6_PRIVATE/native-build"
```

The audio command above is the retained final combination. Its flags are part of
the recipe. Reconstructed audio, BtSettings and feature configuration must match
`profiles/v16-release.json` exactly on the reference profile. The native builder
uses NDK_CC (an ARM/API19 compiler executable) or ZIG (static ARM Linux). The
published station-reader source reconstructs the fixed PTY query method; it is
newly compiled and needs target validation before replacing the private accepted
helper. It does not write driver settings. Scripts in `carplay/assets` are source
inputs, including the required AP reply firewall.

## Deployment boundary

Create a file-by-file target plan from the map in
[the reproduction workflow](17-reproduction-and-porting.md), using generated
hashes rather than pretending every rebuild is the frozen private binary.
Preserve the original and current accepted artifacts/signers. Verify actual idle
native/backend/OEM association/AP/Bluetooth work, use original UI stop, atomically
replace only reviewed files with original ownership/modes, sync and restore
/system read-only. The owner performs a full reboot; then check actual process
generations, loaded revisions/protocol, every mirrored copy, package signer,
hashes, permission grants and read-only mounts. No live deployment was run during
this source review. Follow [rollback](13-rollback-and-porting.md) for failures.
