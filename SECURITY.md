# Publication and device-data boundaries

Do not attach boot images, partition dumps, eMMC chunks, MFi material, private
keys, signing certificates, Wi-Fi configurations, pairing databases, personal
device identifiers, or raw device logs to issues or pull requests.

Use a minimal synthetic reproducer, firmware version, public symbol name,
redacted state transition, and numeric error code when reporting an issue. Do
not paste credentials into a shell command, issue body, or discussion.

The tools in this repository create private outputs only when explicitly run.
Those outputs belong outside Git. `.gitignore` is a convenience; the publication
check also examines the actual file inventory, UTF-8 contents, binary magic,
private-data patterns, local paths, and tracked history.

The initial publication was built from an explicit source allowlist in a new
directory. The original workspace and its history were not pushed. No image or
credential is made safe merely by deleting it in a later commit.

The firmware-specific root modification retains a privileged ADB daemon. This
changes the security boundary of that head unit. Keep debugging access under the
owner's control and do not expose TCP ADB to untrusted networks. This project
does not disable authentication or alter the vehicle's other ECUs.
