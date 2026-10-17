# Pairing, native confirmation, and automatic connection

## Public design guidance

[Apple's wireless CarPlay session](https://developer.apple.com/videos/play/wwdc2017/717/)
describes Bluetooth pairing, native authorization, AP credentials, Bonjour
discovery, and the CarPlay Control connection. Reconnection begins after the
Bluetooth and AP components are operational. The native device selector chooses
the active phone. Connecting another phone must not automatically replace an
active CarPlay session; switching requires a user choice. An explicit manual
disconnect is respected.

These are public workflow principles. Local timeouts, state files, observed
message numbers, firmware contracts, and the implementation below are research
decisions, not an Apple receiver specification.

## First pairing on the test head unit

1. The user adds the phone through Device Connections and completes Bluetooth
   pairing. Both sides must retain the same actual bond.
2. The controller selects the corresponding connected HFP device and prepares
   the OEM projection AP. Network setup completes before the backend starts its
   pending native Bluetooth transport.
3. The original iAP2/MFi implementation identifies and authenticates the phone.
   iOS generates its own system confirmation. The user chooses **Use CarPlay**.
4. The original stack handles the phone's Wi-Fi information request. The
   controller never substitutes a synthetic consent or authentication result.
5. The phone joins the projection AP. The controller resolves a live selected
   CarPlay control service and validates its physical station/IPv6 neighbor.
6. One control request is issued for the eligible connection generation.
   HTTP success is followed by verification of the genuine receiver session and
   native wireless-session event before the icon becomes active.

A phone already authorized for this car may not show the initial confirmation
again. Repeated tapping cannot force iOS to display a first-use prompt. A clean
first-pairing regression deliberately removes the relevant device pairing and
CarPlay authorization; ordinary reconnection tests keep those records.

## Why AP readiness is explicit

The iPhone 15 Pro investigation observed a native Wi-Fi reply while the AP state
was still disabled (`11`). An opt-in comparison of the actual outgoing message
confirmed that its SSID and passphrase matched ConnectivitySSID/ConnectivityPSK.
The hostapd PSK separately matched the expected derivation. Therefore a user
report of an incorrect password was not sufficient evidence that the backend
had sent a different passphrase.

The readiness repair retains real Bluetooth attachment events in the backend.
It starts the original delegate only after the on-unit controller has completed
the OEM AP switch and subnet setup, published readiness, and the backend has
independently checked AP state `13` and projection mode `2`. A detach cancels a
pending start. AP readiness alone cannot create a Bluetooth attachment. Duplicate
events do not repeatedly start the native delegate. No timer or Mac process
releases this gate.

The first-pairing acceptance result must be recorded separately from a successful
reconnection after restarting a backend. Those operations exercise different
phone and native state. The validation matrix is the authority for what passed.

## Manual pause and a genuinely new pairing

The original controller persisted a global manual-pause flag for the current
boot. That correctly prevented immediate reconnection after the user stopped
CarPlay, but it also survived removing and adding the same phone. A completed
new pairing could consequently remain paused despite valid native eligibility.

The backend now records real bond lifecycle events with a kernel-boot identifier.
The controller consumes a new `BOND_BONDING -> BOND_BONDED` event once, only for
the selected HFP target and only when no session is active. That explicit setup
intent can supersede an older pause. Startup snapshots, duplicate events,
ordinary Bluetooth reconnects, and another phone's events cannot do so. Removing
or re-pairing a device invalidates its old learned Wi-Fi peer association.

## More than one phone

An AP association count of one was an early diagnostic restriction. It is not
the final device-selection rule. A working selected 15 Pro session was observed
with two AP stations present.

The final selector parses bounded, source-and-service-owner-bound SRV/TXT data.
On the test 15 Pro, passive comparison established that the observed TXT `id`
matched its Bluetooth identity. This is an empirical implementation finding.
It is not represented as a field definition from Apple's public documentation.
The resulting endpoint must still map through the current IPv6 neighbor table
to an actual associated station. A legacy no-ID path is limited to a previously
verified local peer; an explicit conflicting identity is never overridden.

An established session remains selected when another phone appears. An explicit
choice of another device waits for normal finalization before starting the new
one. Address-scoped disconnect callbacks prevent one phone from pausing another.

### Separate phone/music profiles and the OEM HFP rule

The native Bluetooth manager handles HFP (phone) and A2DP (music) separately.
Its original handler contains a branch that cancels another device's HFP while
retaining that device's A2DP during CarPlay. The corresponding original log text
identifies the HFP-only cancellation. This is a rule in this firmware, not a
public Apple requirement that a second phone must lose HFP on 5 GHz.

A second important native rule is that generic `btcmConnect` rejects a device
already present in the connected-device list, even if that device has only
A2DP. The repaired selector uses the original `btcmOnlyHfpConnect` to add the
missing phone profile after the old CarPlay session has genuinely ended. It
does not require the user to illuminate both icons or force an A2DP connection.
During an active wireless session, the selected phone may itself disconnect
Bluetooth as part of the normal handover to Wi-Fi.

### Measured switch optimization

A baseline 15 Pro-to-X switch took 30.154 seconds from the selector stop request
to genuine native session start. The controller unnecessarily restored client
Wi-Fi and restarted its AP between selected phones. Its fallback also waited
for the selected known station even when another station was already present.

The accepted optimization waits for genuine old-session finalization, retains
only its own verified projection AP for a queued, still-bonded user selection,
and begins the new target without cycling the radio. A music-only target gets
its missing HFP bootstrap immediately after AP readiness. An OEM busy state is
waited out before submitting that single request. A remembered target need not
already be associated to request Bluetooth; sending HTTP still requires the
selected verified station or actual native qualification.

The measured standalone run took 9.307 seconds X-to-15 Pro and 2.780 seconds
15 Pro-to-X. Both were user-confirmed operable. A separate ordinary stop stayed
paused, restored client mode, reconnected from the same icon and retained normal
audio/tail playback. These are individual measured cases, not a latency guarantee.
Plain stop, failure or an invalid queued target still restores client mode.

During deployment the user also cleared X on both sides and re-paired while the
controller was intentionally stopped. Pair completion preceded service recovery
by about 139 seconds. The system prompt subsequently appeared and the user
accepted it successfully. That overlapped maintenance case is not a clean
first-pairing latency measurement. No iOS UI trace was captured for its display
delay, so the remaining phone-side timing cannot be assigned precisely.

## Runtime independence

The init service, root controller, native backend, command bridge, network setup,
and discovery execute on the head unit. USB syslog and resource observers on a
Mac are optional diagnostics. There is no Mac-hosted controller, required proxy,
phone-side application, or periodic host command in the connection path.

The supervisor handles bounded process recovery. It is distinct from repeatedly
requesting a phone connection. A user stop remains paused; an ordinary failure
does not cause an unbounded HTTP retry loop.
