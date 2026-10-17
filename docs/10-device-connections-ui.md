# Device Connections buttons and progress

## Three controls per device row

The OEM page originally replaced the phone/music controls with a CarPlay
control when projection became active. The patch retains three adjacent
controls: phone, music, then CarPlay. Offline CarPlay uses the same gray visual
language as the other offline profiles. A genuinely active session uses the
active icon and normal button background.

The implementation modifies the optimized code at a verified original hash.
It reuses OEM resources and does not re-sign or replace the original settings
APK. The public repository supplies patch generators and authored bridge code;
the original ODEX/APK are private local inputs.

## Correct device identity for every row

An early row patch accidentally used an incremented display counter rather
than the actual device index. The correction uses the real row index for
listeners and active-device comparisons. A click carries that row's actual
BluetoothDevice into the permission-protected command bridge. The controller
does not guess the device from a name, the first row, or the most recent UI
coordinate.

Connecting a different device while a session is active is an explicit selector
action. It queues the chosen target and waits for genuine finalization. A
disconnect callback is scoped to the current device, so another row cannot
pause the selected phone.

## Progress and quiet failure behavior

The UI reuses the original 30 dp animated ProgressBar. The controller publishes
a target and monotonic expiry through transient system properties. Only the
matching row shows progress. Successful completion, normal disconnect,
failure, or expiry clears it. This is an operation indicator, not evidence that
CarPlay has already connected.

The optional Chinese backend-not-ready toast was removed at the owner's
request. When the command bridge is not ready it quietly ignores that click.
Existing OEM Bluetooth error dialogs were not globally disabled to conceal
other failures.

Commands use a monotonic nonce and an acknowledgement. A real kernel reboot
resets the monotonic clock; retaining the prior boot's high-water acknowledgement
would discard valid clicks. The controller retires the old command, backup,
and acknowledgement on a new kernel boot before accepting new UI commands.

## Disconnect semantics

The UI requests the OEM projection manager to disconnect the selected address.
It waits for the actual receiver and native session publication to finalize,
then restores Wi-Fi client mode and records manual pause. It does not
immediately reconnect Bluetooth profiles as a side effect of stopping CarPlay.
An explicit next CarPlay click resumes through the verified peer/control path
and, when necessary, a genuine Bluetooth qualification fallback.

Final restores the original native control/disconnect implementation. The old
custom clear-session fallback was withdrawn after a recorded native crash.
The active final source tree does not contain that fallback.
