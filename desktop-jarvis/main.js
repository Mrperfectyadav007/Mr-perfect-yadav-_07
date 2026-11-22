const { app, BrowserWindow, Tray, Menu, shell, session } = require("electron");
const path = require("path");
const JARVIS_URL = "https://jarvis99-lake.vercel.app";
let win, tray;
app.setAppUserModelId("com.mrperfectyadav.jarvis");
function createWindow() {
  win = new BrowserWindow({
    width: 1180, height: 820, minWidth: 380, minHeight: 600,
    title: "MR. PERFECT YADAV JARVIS",
    backgroundColor: "#050817",
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  win.loadURL(JARVIS_URL);
  win.on("close", e => { if (!app.isQuitting) { e.preventDefault(); win.hide(); } });
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: "deny" }; });
}
app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(["media", "notifications"].includes(permission));
  });
  createWindow();
  // Tray icon uses Electron's built-in empty image fallback until a branded .ico is added.
  tray = new Tray(process.platform === "win32" ? path.join(__dirname, "tray.ico") : path.join(__dirname, "tray.png"));
  tray.setToolTip("MR. PERFECT YADAV JARVIS");
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: "Open JARVIS", click: () => { win.show(); win.focus(); } },
    { label: "Open website", click: () => shell.openExternal(JARVIS_URL) },
    { type: "separator" },
    { label: "Exit", click: () => { app.isQuitting = true; app.quit(); } }
  ]));
  tray.on("double-click", () => { win.show(); win.focus(); });
  app.on("activate", () => { if (win) win.show(); else createWindow(); });
});
app.on("before-quit", () => { app.isQuitting = true; });