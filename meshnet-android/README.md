# Vanelle MeshNet — Android MVP 0.1

A clean, futuristic Android prototype for VAMP packet experiments and nearby-device discovery using Android Wi-Fi Direct (P2P).

## What this version does
- Requests only the nearby permission needed for Wi-Fi Direct discovery (Android 13+) or fine location on older Android versions.
- Checks Android's Wi-Fi Direct state and starts peer discovery.
- Displays peers reported by Android and sends a connection request when a peer is selected.
- Requests removal of a Wi-Fi Direct group through the system API.
- Runs a local software-only packet/hash/deduplication demonstration clearly labelled SIMULATED.
- Uses a dark navy, cyan and violet interface with status and error messages.

## What it does not claim
- No new physical radio waveform or long-distance capability.
- No multi-hop forwarding or end-to-end chat protocol in this MVP.
- Peer discovery/connection needs compatible nearby hardware, system support, Wi-Fi enabled and required permission.
- A successful connection request is not proof of application-level data delivery.
- The software-only demo is not a radio test.

## Build
From this directory with JDK 17 and Android SDK installed, run: gradle assembleDebug.
The GitHub Actions workflow builds the debug APK and uploads it as an artifact. It also runs JVM unit tests and Android lint where available.

## Permission rationale
Wi-Fi Direct uses the INTERNET permission for sockets even when no Internet connection is used. NEARBY_WIFI_DEVICES is requested on Android 13+; Android 12L and earlier use location permission for discovery. The app does not request microphone, contacts, SMS, camera, call logs, or background location.
