# Owner switching checklist

Operate the vehicle UI manually. USB ADB mode must be off when using the same
port for iPhone USB CarPlay. Change one condition at a time; avoid repeated
pairing deletion. Record results; these rows are not automatic acceptance.

| Case | Expected behavior | Current status |
|---|---|---|
| X wired -> explicitly choose 15 Pro wireless, X remains plugged | Original X session ends, then 15 Pro connects; no automatic takeover | Connection reported and lifecycle trace reviewed; latest App operability not separately confirmed |
| 15 Pro wireless -> plug X | 15 Pro continues; X should be available for explicit selection | Known inactive USB-only row omission remains |
| Explicitly select USB-only X | Original 15 Pro session ends before X starts | Incomplete when X is not listed; do not mark passed |
| Same phone wireless first, then insert USB | Existing wireless session continues without interruption | Not accepted yet |
| Same phone wired first, wireless also recognized | One device identity and one active session; no automatic interruption | Not accepted yet |
| Unplug or explicitly stop | Do not automatically reconnect solely because of the stop | Further owner verification |

Public flow reference: https://developer.apple.com/videos/play/wwdc2017/717/
The document separates explicit device selection from automatic transport
selection. It does not require a manual wired-versus-wireless switch for one phone.
