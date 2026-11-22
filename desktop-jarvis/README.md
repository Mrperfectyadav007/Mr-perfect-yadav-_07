# MR. PERFECT YADAV JARVIS — Windows

This Electron desktop companion opens the existing JARVIS web app in a desktop window, keeps it in the Windows notification area (system tray) when closed, and allows microphone permission for the website.

## Build on Windows
1. Install Node.js LTS.
2. Open Command Prompt in this folder.
3. Run `npm install`.
4. Run `npm start` to test.
5. Run `npm run dist` to create the Windows installer in `dist/`.

## Important
- Add a valid `tray.ico` file to this folder before packaging. (The current placeholder path is intentionally not a fabricated icon.)
- Enable “Hey Jarvis” inside the JARVIS page and allow microphone access.
- To launch at Windows sign-in, enable the app's Startup option in Windows Settings > Apps > Startup after installing, or add its shortcut to the Startup folder.
- This is not a system-wide offline wake-word engine. Recognition depends on the browser engine, network/provider availability, microphone permission, and the page remaining open. It cannot listen while the PC is shut down.
- Keep Gemini API keys out of source code; configure them inside the existing JARVIS settings.
