# MR. PERFECT YADAV JARVIS — Android

Android app project planning notes. The first mobile build should be a native WebView shell for the existing JARVIS website, with runtime microphone permission and a visible foreground notification when a future listening service is enabled.

## Build requirements
- Android Studio (current stable)
- Android SDK 35 or compatible
- Package id: `com.mrperfectyadav.jarvis`
- Website: https://jarvis99-lake.vercel.app

## Always-listening limitation
Android requires a user-visible microphone foreground service, runtime microphone permission, and compliance with background-start restrictions. A WebView's speech recognition is not a dependable always-on wake-word engine when the app is backgrounded or the screen is locked. A robust build needs a dedicated on-device wake-word engine and its licensed model/assets. Do not claim the APK is always-on until that engine and device testing are complete.

## API key safety
Do not embed a Gemini API key in the APK. Use the existing secured backend/proxy for AI requests.
