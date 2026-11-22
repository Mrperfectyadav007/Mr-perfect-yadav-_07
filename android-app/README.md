# MR. PERFECT YADAV Android App

Existing native Android project retaining the red/black branded home screen and original app assets.

## Changes in this update branch
- Removed offline song-download UI, imports and DownloadManager helper, per requirement.
- Clarified that commercial full tracks require a licensed provider.
- Existing local MP3 playback, notification actions, equalizer and bass-boost code remain.

## Feature status — remaining work is not yet shipped
- Local MP3 playback: present; background lifecycle/service refactor and device verification remain.
- Online legal music search: present for Archive.org results; check each track's licence terms.
- Commercial full-track catalogue: requires provider agreement and credentials.
- Equalizer/Bass Boost: present; support varies by Android device.
- Playlists, favorites and recently played persistence: not fully implemented.
- Gemini AI assistant: requires secure HTTPS backend; keep API key server-side.
- Premium membership/billing: requires Google Play Billing setup and products.
- Privacy, app update and about flows: need completion and testing.
- Offline song download: intentionally excluded.

## Build
Open `android-app` in Android Studio, sync Gradle, then build an APK. Build and device playback have not yet been verified for this branch.
