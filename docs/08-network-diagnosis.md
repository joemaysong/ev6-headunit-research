# DHCP, subnet isolation, Bonjour, and IPv6

## Evidence behind the early disconnects

The original symptom was a projection session exiting after roughly 40-60
seconds. In the observed failure, the phone closed its TCP streams first and
the receiver finalized about 0.94 seconds later. AP association remained present.
CPU and memory measurements did not establish receiver overload. A Bluetooth
reconnection after finalization was a consequence to distinguish from the
earliest network failure.

An AP-only comparison removed the video/audio receiver from the experiment.
The phone sent DHCP DISCOVER, the server produced OFFER, but REQUEST/ACK did not
complete. The phone remained in initialization/link-local configuration.
The head unit had overlapping Ethernet and projection-AP IPv4 subnets. A route
lookup for the offer selected Ethernet rather than the wireless interface.

The repair used the OEM netd interface to place the projection AP on an unused
private `/24`, selected from a bounded candidate range. Ethernet configuration
was preserved. The original interface configuration was saved locally for
normal stop/rollback, and asynchronous netd responses were parsed through the
actual final response rather than treating an early notification as completion.

After isolation, both sides showed a completed DHCP exchange. Interactive runs
of 10 minutes 29 seconds and 3 minutes 10 seconds ended by deliberate stop.
These are observed durations, not a lifetime reliability guarantee.

## Ownership of AP changes

`OemApSwitch` uses the head unit's real Wi-Fi APIs and state notifications.
The controller saves the prior client preference, switches client to projection
AP, verifies state, and applies the subnet. Normal finalization restores the
saved interface state and enables/persists Wi-Fi client use. The original AP
configuration remains private on the head unit.

No transmit-power increase, driver replacement, firewall disablement, invented
signal-strength value, or extended media timeout was the demonstrated network
fix. The first-pairing AP readiness repair is separate from the subnet repair.

## Discovery and selected-device validation

The controller subscribes on `wlan0` to IPv6 Bonjour discovery for the CarPlay
control service. It uses the live advertised SRV hostname/port and retains the
IPv6 link-local interface scope. No captured ephemeral port is hard-coded.

The bounded DNS parser rejects malformed names, truncated messages, invalid
ports, expired records, conflicting identities, and cross-source/service-owner
mixing. A continuous subscription allows SRV and TXT to arrive in either order
or in separate packets. Current NDP and physical AP association are checked
before a control request. A bounded DNS nudge can populate an unresolved
neighbor; it is not repeated HTTP connection polling.

An early station tool returned no data through ordinary redirected pipes.
The OEM `wl` utility required a PTY. The fixed helper has a fixed command,
bounded output and duration. An empty pipe was not evidence of zero stations.
The final selector allows multiple AP stations and uses the selected identity,
not the first packet or association count, to choose a phone.

## Diagnostic boundaries

Phone USB syslog, car log relays, DHCP metadata, TCP direction/flags/lengths,
resource counters, and PCM state were separate observations. RVI required the
owner's local administrator interaction; no password was embedded in a script.
PKTAP capture length/filter behavior was validated before interpreting an empty
result. A zero exported-record count did not by itself prove zero traffic.

Public results contain numeric summaries only. AP names, passphrases, pairing
databases, addresses, and raw network/media captures are not included.
