# Wearable protocol sources

The transport classes in `src/main/java/org/microg/wearable` and the base
schema in `src/main/proto/wearable.proto` originate from microg/Wearable,
commit `137a09912aaa6d6fbbe6689ecf588059b89ae7a2` (Apache-2.0).
Their copyright and license notices are retained.

The schema is generated with the same Wire version as GmsCore. The transport
uses generated `ADAPTER` encoders and decoders instead of the Wire 1.x API
used by the old `org.microg:wearable:0.1.1` binary.

RPC fields 13 (`requiresResponse`) and 14 (`senderRequestId`) follow the
experimental schema in microg/GmsCore PR #3204, specifically
`deadYokai/GmsCore-ble`, `wearable_tos`, schema blob
`4ea601a4e1f01a88ef14b70cdbdc7fccfef23c1d`.
This is a source reference, not independent verification against a watch.

`WireProtocolTest` uses hand-written protobuf fixtures to check legacy
encoding, framing, modern RPC fields, unknown-field preservation, and
rejection of malformed frame lengths. These fixtures are not device captures.

This migration does not implement setup RPC responses, account transfer,
notification mirroring, media controls, or the Channel API. In particular,
no successful or empty response is fabricated for
`/clockworkSetupWizard/frp_status_request`. A device trace and a verified
response schema are still needed before implementing that handler.
