# Vanelle MeshNet — Android

Vanelle MeshNet is an Android local-network communication app built around the VAMP packet format and Android Wi-Fi Direct. The interface uses a navy, cyan and violet visual system.

## Implemented in this source
- Native Android interface with visible network state, peer discovery, connection requests, and group disconnection.
- Runtime permission flow for nearby Wi-Fi Direct discovery on Android 13+ and legacy location permission handling on Android 12L and earlier.
- One-hop TCP text transport inside a Wi-Fi Direct group, with received/sent messages shown in the local UI.
- Versioned VAMP packet envelope with payload size validation, TTL decrement/expiry, bounded duplicate cache, and deterministic JVM tests.
- A bounded, transport-agnostic store-and-forward routing core with expiring route entries, TTL decrement, duplicate suppression, queue limits, next-hop selection, acknowledgements, and unit tests. It is a routing foundation; the current Wi-Fi Direct transport/UI do not yet connect this core into a working multi-hop mesh.
- Explicit software simulation that does not claim to measure radio performance.
- CI checks for JVM tests, debug APK compilation, minified release variant, Android emulator UI tests, and Android lint.

## Important operational limits
- This implementation does not create a new radio waveform or change the hardware frequency capabilities of a phone.
- Wi-Fi Direct is a nearby-device transport. Multi-hop forwarding between independent groups, Internet gateway routing, local web hosting, file transfer, and end-to-end encryption are not yet implemented end-to-end.
- Local TCP chat is currently unencrypted. Do not use it for confidential content.
- The release build produced by CI is not signed with a production signing key. A production signature must be kept securely by the publisher and reused for updates.
- Real device-to-device tests on the target Samsung tablet and other supported devices have not been performed by CI; emulator success cannot prove hardware interoperability or radio range.

## Build locally
With JDK 17 and Android SDK platform/build tools 35 installed:
`gradle testDebugUnitTest assembleDebug assembleRelease lintDebug`

The workflow uploads debug and release build outputs plus test/lint reports as a GitHub Actions artifact when the workflow completes.
