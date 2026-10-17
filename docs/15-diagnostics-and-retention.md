# Recreate diagnostics; retain no capture data

Final contains an in-process, observation-only EV6ATTEMPT4 endpoint because the
AP timing repair uses its original-work acknowledgement. This is part of the
runtime repair, not a temporary recorder. It has no persistent capture worker.

## Vehicle state / original work

With the exact final installed, use the original framework classpath plus
/system/etc/ev6-carplay/controller.jar and launch ControllerProbe or
OemAttemptProbe in a separate root app_process. The latter accepts only root,
checks peerUID1000/PID/startgeneration and prints fixed counts/flags. No phone
address, credential or audio payload is printed. Use a bounded host recorder;
stop it after the case and remove only its own temporary files/ADB forwards.

The installed controller is autonomous. Do not stop it or disconnect a working
session merely to read its state. To inspect Java original resource fields,
the privately retained JDWP reader source retain
the reviewed JDWP read-only approach; require a current exact firmware hash,
attach one original process, read explicit fields, dispose then remove the
allocated ADB forward. No guessed private fields or writes.

## Phones

Use libimobiledevice USB enumeration and select a unique ProductType match:
iPhone10,6 for X, iPhone16,1 for15Pro. Keep actual identifiers only in memory.
On this Mac old binaries need libplist compatibility: create a private runtime
directory with libplist-2.0.3.dylib and libplist-2.0.4.dylib symlinks pointing to
the installed libplist-2.0.dylib, then set DYLD_LIBRARY_PATH for that child only.
Check subprocess return codes; missing dylib is not absence of connected phones.

For X root inspection use the user's existing palera1n usbmux SSH port44 through
an ephemeral iproxy local port. Read current credentials interactively or from
an ephemeral environment; never save them in source, logs or GitHub. Close only
the proxy created by the diagnostic. Keep jailbreak/SSH bootstrap intact.

For both phones use a filtered USB syslog relay: the privately retained filtered USB relay source documents
count/event sanitization. It installs no profile or on-phone recorder and stores
captures only in the chosen host output. Non-jailbroken15Pro has no equivalent
root shell. Do not infer phone-side storage merely from host relay output.

## Disposal

Stop only owned host workers/proxies; remove owned ADB forwards, temporary
reader jar/cache, PCM numeric diagnostics and host run directories. Remove any
bounded test firewall chain/jump only after exact ownership checks. Never clear
pairing, Wi-Fi credentials, user apps, system analytics or unrelated crash reports
as part of project cleanup. Operational controller state is not a capture log.
