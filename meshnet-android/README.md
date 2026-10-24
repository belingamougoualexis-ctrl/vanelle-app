# Vanelle MeshNet — Android

Vanelle MeshNet is an Android local-network communication app built around the VAMP packet format and Android Wi-Fi Direct. The interface uses a navy, cyan and violet visual system.

## Implemented in this source
- Native Android interface with visible network state, peer discovery, connection requests, and group disconnection.
- Runtime permission flow for nearby Wi-Fi Direct discovery on Android 13+ and legacy location permission handling on Android 12L and earlier.
- One-hop TCP text transport inside a Wi-Fi Direct group, with received/sent messages shown in the local UI.
- Versioned VAMP packet envelope with payload size validation, TTL decrement/expiry, bounded duplicate cache, and deterministic JVM tests.
- Explicit software simulation that does not claim to measure radio performance.
- CI checks for JVM tests, debug APK compilation, minified release variant, Android emulator UI tests, and Android lint.

## Important operational limits
- This implementation does not create a new radio waveform or change the hardware frequency capabilities of a phone.
- Wi-Fi Direct is a nearby-device transport. This code does not yet implement multi-hop forwarding between independent groups, Internet gateway routing, file transfer, or end-to-end encryption.
- Local TCP chat is currently unencrypted. Do not use it for confidential content.
- The release build produced by CI is not signed with a production signing key. A production signature must be kept securely by the publisher and reused for updates.
- Real device-to-device tests on the target Samsung tablet and other supported devices have not been performed by CI; emulator success cannot prove hardware interoperability or radio range.

## Build locally
With JDK 17 and Android SDK platform/build tools 35 installed:
`gradle testDebugUnitTest assembleDebug assembleRelease lintDebug`

The workflow uploads debug and release build outputs plus test/lint reports as a GitHub Actions artifact.
