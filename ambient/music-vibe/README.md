# Breathing lights 1.1.2

Project-authored Android 4.4 service source. This is target-specific MICOM Binder
integration, not an ordinary Android ambient-light API. It has no independent
activity or microphone. The existing Launchpad invokes its service.

## Build without a vehicle

Use a full JDK 17, Android SDK platform 35 and build-tools 35.0.0 obtained separately.
No SDK licenses are accepted automatically. Set ANDROID_JAR if another compatible
Android compile jar is required. Code uses API 19 runtime interfaces and D8 min-api
19. Preserve the package's original signing identity for an existing installation;
this builder refuses missing keys and never creates replacements.

```sh
export JAVA_HOME="/path/to/full/jdk/Contents/Home"
export ANDROID_SDK_ROOT="/path/to/android-sdk"
export MUSIC_VIBE_BUILD="/path/outside/checkout/new-empty-build-directory"
export MUSIC_VIBE_KEY="/path/to/retained/key.pem"
export MUSIC_VIBE_CERT="/path/to/retained/cert.pem"
python3 ambient/music-vibe/build.py
```

The output directory must not already exist and must be outside this public
checkout. OpenSSL creates the same v1 APK signature format used by the retained
vehicle build. The builder records APK and source SHA-256 values. Independently
verify the APK and signing certificate before a separately authorized install.
No build command accesses ADB or installs anything. A clone alone does not supply
an owner's signing identity, recovery files or a valid vehicle API contract.

## Runtime contract

Package org.ev6.musicvibe provides BootReceiver, BreathService and RestoreGuard.
Service actions START, STOP, BOOT and CONFIG require the existing secure-settings
caller permission; Launchpad uses its narrow root bridge. CONFIG accepts autoboot
(boolean), mode (clockwise, counterclockwise or random), and period_ms (2000-20000).
The default period is 8000 ms. Opening an OEM AmbientLight/CustomColor settings
activity stops control for the current boot; explicit START clears that marker.
This is the current menu-foreground detection policy, not a verified observer of
every possible external brightness adjustment. Preserve it when porting.

State lives in the Android app's private files directory. restore.json is the
pre-effect baseline; do not replace it with a current animation sample or delete
it to bypass a failure. The guard handles stale heartbeat recovery and verifies
the baseline before acknowledging restoration. Journals are bounded per process
role to one current and one rotated file (about 1 MiB each).

Vehicle.java records the known Binder transaction contracts and 9-byte light
status broadcast; Lights validates known ranges. Palette contains the observed
64 custom-color entries. These values are not proof of compatibility with other
vehicles or firmware. In this build the app writes brightness/RGB only; preserve
manual control and a tested restoration path for another integration.

## Backup and rollback

Before any authorized update, retain the installed APK and signer fingerprint,
all private app files, package state and current original-light backup outside the
public checkout. Do not force-stop or uninstall a running effect as a shortcut:
request STOP, wait for verified baseline restoration, and inspect any pending
restore.json. Restore the retained APK using its original signer and confirmed
platform downgrade procedure, retaining current data until recovery is verified.
A full data restoration must match the saved snapshot and vehicle state; never
blindly overwrite a newer unresolved restore obligation.

The vehicle accepted the original 1.1.2 build's BOOT action and seven color steps;
a fresh physical reboot is pending. This export translates diagnostics to English,
keeps control logic, and was compiled separately. It is not the exact deployed APK.
See [the dated session record](../../docs/19-candidate69-and-session-update.md).
