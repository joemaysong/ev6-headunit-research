# Rebuild final without previous candidates or diagnostic captures

## Frozen runtime and original inputs

The installed release is identified by private final manifest. Copying those exact
assets is the bit-exact restoration path. Pristine inputs remain carplay/original;
retain the existing local signing key for backend package compatibility. Never
create a new key to replace an already installed backend.

## Controller

Use carplay/build.py with JAVA, ECJ_JAR, ANDROID_JAR, R8_JAR and
SMALI_CLASSPATH configured. It compiles only frozen carplay/src Java and the
frozen provider; it never installs, modifies original files, or runs tests.
The output goes to a fresh directory supplied by the operator. APK/native/ODEX
assets stay the frozen manifest copies; do not rebuild historical experiments.

## DiPO observation ODEX

carplay/diagnostics/AttemptObservation.java and EV6OemAttemptBridge.java are the V4
adapter sources. owner-supplied private instrumented-smali directory is the exact privately retained complete
instrumented app input from the accepted prototype. It includes the project's
observation wrappers with original OEM business bodies preserved. Assemble it
with smali API19, then load the resulting DEX in an isolated app_process VM on
this exact firmware to let Dalvik generate its own optimized cache. Do not edit
ODEX dependency headers/register maps. Pair the unmodified original stripped
APK with that cache in an isolated loader first. The existing final ODEX is the
known accepted runtime input; a regenerated cache must be separately reviewed.

Diagnostic runtime reconstruction uses a separate root/system-UID fixture, never
an extra native control sender. No receiver restart or CarPlay UI action is a
prerequisite to passive observation. All build and deployment operations require
fresh inactive/session/association checks; actual vehicle UI is user-operated.

## Other retained components

carplay/smali-backend and carplay/src/Backend*.java contain the OEM9 project
wrapper retained by final. Its original iAP2 classes are owner-supplied inputs.
carplay/src/audio holds the exact state-drain patch generator/assembly. It needs
pyelftools, capstone, an ARM compiler/linker and the exact original library.
carplay/src/button and final runtime UI helpers preserve the accepted UI.
No direct-HTTP or custom native disconnect fallback is part of this final.

Before installing generated output compare its scope and original hashes, save
pristine recovery, stop through original UI, check inactive native/backend/PP
association and APoff, replace only scoped known files, remount system read-only,
then verify real session ownership. Never treat an outer timeout as completion.
