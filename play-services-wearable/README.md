# WearOS compatibility verification

This branch implements a staged subset of the WearOS support work tracked by
microg/GmsCore#2843. The code in this branch is kept in an owner-fork QA pull
request until repository checks, physical-device validation, and human review
are complete.

## Implemented compatibility surface

The current implementation includes:

- legacy Wearable Node, Data, and Message API plumbing backed by the existing
  microG wearable service;
- preservation of the configured `connectionEnabled` state;
- peer-node tracking during connect/disconnect without replacing the local
  `nodeId`;
- the modern Binder methods observed in public microG WearOS research:
  `getNodeId` (66), `updateConnectionStrategy` (71),
  `getRelatedConfigs` (72), and `updateConfig` (73);
- `onGetNodeIdResponse` callback transaction 38;
- backward-compatible `ConnectionConfiguration` fields for package name and
  retry strategy;
- notification forwarding over the existing Wearable Message API on
  `/wearable/notification`;
- media-state forwarding on `/wearable/media` and media commands on
  `/wearable/media/command`, without introducing a new Bluetooth/RFCOMM
  transport or radio UUID.

The Binder transaction IDs and parcel fields above are based on the public
research in microg/GmsCore#3204 rather than guessed radio/protocol values.

## Build verification

From the GmsCore repository root:

```bash
export GRADLE_MICROG_VERSION_WITHOUT_GIT=1
./gradlew :play-services-wearable:assembleDebug :play-services-wearable:core:assembleDebug
./gradlew :play-services-wearable:testDebugUnitTest
```

The owner-fork QA flow records the exact build result in the draft pull
request.

## Transport evidence

A public stock-GMS WearOS trace used for transport/configuration comparison is:

https://github.com/microg/GmsCore/pull/3204#issuecomment-3980274818

That trace demonstrates a real `WearableBt` socket, a server
`ConnectionConfiguration` with Type 1 / Role 2, authentication/encryption,
handshake, peer connection, and initial data sync.

This trace is evidence for transport/configuration behavior only. It is not a
claim that this branch has completed physical-device setup or final bounty
acceptance.

The notification/media message contract used by the independently written
bridge layer is documented publicly in the earlier Apache-2.0 WearOS
compatibility work at:

https://github.com/microg/GmsCore/pull/3286

The branch uses the documented message paths and payload fields as protocol
reference only; it does not copy the prior pull request implementation bodies.
Physical-device behavior still requires independent validation.

## Submission gate

Do not submit this branch upstream as complete until genuine physical WearOS
validation and meaningful human review/contribution are documented.
