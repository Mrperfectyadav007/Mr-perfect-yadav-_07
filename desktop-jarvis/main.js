const { app, BrowserWindow, Tray, Menu, shell, session, nativeImage } = require("electron");
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
  tray = new Tray(nativeImage.createFromDataURL("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAABCElEQVR4nM2XMQ7CMAxFXQQRKzCAUO7FSRBCSAzchBEhBpZ2iTgIM0OrHiJMlcBNUqdx2vy1tf+TnThJNpuvNYyoyZjmAADTPkG7T2n9dpMbr1wZtQUu0xAYEgA2dyX2+bcT4DeZb2mp8dZFGGqO42wtNAJwmFMhnNsw1JySpwXQUHKZYwhchT8AqnldvtkgRp+E6QDE6j0WbkM6FejSSz2jAJBPw9VyAVJuQQgBVVUPD3A8XeBxv4LWGvaH8/AAeaEgLxSbcaN0FqFtVHILb/d0KpAEQOw2mKZtqwKxIGyj3tkCLghXHiMA5S7Xx9x00FkrwAFBuVum/S5wJaaI7WXkAxPtbRhLX9plfbCmjFIdAAAAAElFTkSuQmCC"));
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