# MR. PERFECT YADAV JARVIS — Cross-platform roadmap

## Existing AI
- Web app: https://jarvis99-lake.vercel.app
- Repository: this repository

## Windows first build
- Electron desktop wrapper in `desktop-jarvis/`
- System tray hide/show
- Microphone permission for the hosted JARVIS web app
- Installer build command: `npm run dist`

## Android build
- Native Android WebView shell is the first milestone.
- Next milestone: native microphone foreground service + on-device wake-word engine.
- Then test lock-screen, battery optimization, reboot/startup, and manufacturer-specific restrictions.

## Honest status
These source files are project scaffolding, not compiled EXE/APK installers. A real always-on wake word requires native integration and testing; website speech recognition alone is not equivalent.
