# Wi-Fi client, TCP ADB, and the native Setup icon

## Enabling the existing Wi-Fi stack

The firmware reported Wi-Fi support flags as disabled, yet its native service
implemented `IWifiManager.setWifiEnabledForEngineerMode(true, false)`. In the
controlled test, that call initialized the driver, produced `wlan0`, returned
Wi-Fi state 3, and scanned both 2.4 and 5 GHz networks.

The original screen `com.hkmc.wifisettings/.WifiClientSettings` let the owner
enter a hotspot password directly on the head unit. No password was exported to
the Mac or stored in research logs. The feature flag alone was therefore not a
reliable hardware-availability test.

Later normal client/AP switching used the standard `setWifiEnabled` path and
the OEM projection-AP APIs, with event/readback confirmation. Engineer-mode
activation was useful to establish capability; it was not treated as the final
CarPlay connection policy.

## A real Wi-Fi ADB transport

The head unit and Mac joined the same owner-controlled network. USB ADB enabled
TCP mode and the host opened a separate TCP transport:

```sh
adb -s "$EV6_ADB_SERIAL" tcpip 5555
adb connect "$EV6_HEADUNIT_IP:5555"
adb -s "$EV6_HEADUNIT_IP:5555" shell id
```

The shell was explicitly selected by IP:port, so USB was not silently doing the
test. Both transports were initially visible. A later controlled test disabled
the current USB gadget and reconnected over TCP again. This established TCP ADB
operation, not persistent TCP debugging across every boot.

The IP must be discovered again after DHCP changes. No old hotspot address or
serial is embedded in the public code. `adb ... usb` returns the selected daemon
to USB mode. Disabling all USB functions through `sys.usb.config=none` stopped
adbd on this firmware, including TCP; it was not a suitable way to retain TCP
while removing only the USB debugging gadget.

## Why adding an icon was not enough

Setup already contained the original Wi-Fi title, icon, intent construction, and
settings activity. Two separate defects prevented normal use:

1. A support check hid the native menu entry.
2. The current homepage's item-click dispatch did not handle its Wi-Fi item.

The final repair changed only optimized code in `Settings_W.odex` and the
owner's menu ordering preferences. The original APK and resources were retained.
The visibility branch was bypassed; a reviewed debug-log region was reused for
an item-specific call to the existing Wi-Fi intent and `startActivity`. Existing
power and click guards remained.

`ItemsData`, `ItemsData1`, and `ItemsData2` received the native Wi-Fi entry in
their first empty slot, index 8. Other positions were preserved. A whole
preferences file from another vehicle must not be copied over the owner's
settings. Private baseline preferences remain with the vehicle backup.

| Artifact | MD5 |
|---|---|
| Original Settings_W ODEX | `9ae14ab3c2ddbfd1608abb14f44f5b0a` |
| Validated final ODEX | `feb4b09a66b9bfce819f3b03d3c0b146` |

The [offline patch generator](../wifi/build_menu_patch.py) requires the exact
original hash, updates DEX checksums, and verifies the final SHA-256. It does not
install anything. Menu-preference insertion remains a private, reviewed step
because the input contains the owner's existing settings.

## Installation and rollback observed in the experiment

A temporary bind tested the candidate first. The native icon was clicked and
opened WifiClientSettings; the original General entry still opened its own
screen. After removing the temporary mount and rechecking stock, the candidate
was atomically installed with root ownership and mode 0644, followed by sync and
a read-only `/system` remount. Private preference ownership remained system and
the expected mode was restored.

The device retains the separate Wi-Fi-menu stock ODEX/preferences backup. The
CarPlay rollback and CarPlay intermediate cleanup deliberately leave it intact.
Do not reuse the CarPlay rollback to claim all earlier menu/root changes were
removed.

## Hidden-feature inventory is not an activation list

| Candidate | Evidence | Boundary |
|---|---|---|
| Projection AP/hotspot | Native screens, services, and OEM AP APIs | Projection AP later tested; general Internet sharing is separate |
| Wi-Fi Direct | Activity/action exists | P2P connection not validated |
| Nature Sounds | Module and feature exist; menu gate differs by model step | Its later audio-source behavior must be analyzed by event order |
| Network connection | Screen exists; telematics/modem gates fail | No working modem service established |
| Media/voice settings | Shared screens exist but version gates exclude them | No complete native voice engine demonstrated |
| EV Setup entry | EV app exists; newer Setup requirements differ | Existing EV application is not the same as that hidden entry |

Navigation, IoT, region-specific connected services, and shared launcher icons
also appeared in common code. They were not activated merely because a resource
was found. Native voice data locations contained unsupported/dummy content;
Siri over CarPlay is a separate phone feature.
