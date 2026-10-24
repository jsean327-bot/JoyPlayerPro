const { app, BrowserWindow, ipcMain, session, net, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

const UA = 'VLC/3.0.18 LibVLC/3.0.18';
let win = null;

if (!app.requestSingleInstanceLock()) { app.quit(); }
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

// ---------- tiny file store (userData/store/<name>.json) ----------
function storeDir() {
  const d = path.join(app.getPath('userData'), 'store');
  fs.mkdirSync(d, { recursive: true });
  return d;
}
function safeName(n) { return String(n).replace(/[^a-zA-Z0-9_.-]/g, '_'); }
ipcMain.handle('store:read', async (_e, name) => {
  try { return await fs.promises.readFile(path.join(storeDir(), safeName(name) + '.json'), 'utf8'); }
  catch { return null; }
});
ipcMain.handle('store:write', async (_e, name, text) => {
  const f = path.join(storeDir(), safeName(name) + '.json');
  const tmp = f + '.tmp';
  await fs.promises.writeFile(tmp, text, 'utf8');
  await fs.promises.rename(tmp, f);
  return true;
});
ipcMain.handle('store:age', async (_e, name) => {
  try { const st = await fs.promises.stat(path.join(storeDir(), safeName(name) + '.json')); return Date.now() - st.mtimeMs; }
  catch { return -1; }
});
ipcMain.handle('store:clear', async (_e, prefix) => {
  try {
    for (const f of await fs.promises.readdir(storeDir())) {
      if (f.startsWith(safeName(prefix))) await fs.promises.unlink(path.join(storeDir(), f));
    }
  } catch {}
  return true;
});

// ---------- network ----------
async function getText(url) {
  const res = await net.fetch(url, { headers: { 'User-Agent': UA, 'Accept': '*/*' }, redirect: 'follow' });
  if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + (res.statusText || ''));
  return res;
}

const slimmers = {
  live: (a, cats) => a.map(x => ({ i: x.stream_id, n: x.name || '', l: x.stream_icon || '', g: cats[x.category_id] || 'Other', e: x.epg_channel_id || '' })),
  movies: (a, cats) => a.map(x => ({ i: x.stream_id, n: x.name || '', p: x.stream_icon || '', g: cats[x.category_id] || 'Other', r: Number(x.rating) || 0, a: Number(x.added) || 0, e: x.container_extension || 'mp4' })),
  series: (a, cats) => a.map(x => ({ i: x.series_id, n: x.name || '', p: x.cover || '', g: cats[x.category_id] || 'Other', r: Number(x.rating) || 0, a: Number(x.last_modified) || 0 }))
};

ipcMain.handle('api:json', async (_e, url) => {
  const res = await getText(url);
  return await res.json();
});

// Xtream list: categories + streams, slimmed down in this process (huge lists never cross the IPC as raw JSON)
ipcMain.handle('xt:list', async (_e, base, user, pass, kind) => {
  const api = `${base}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`;
  const [catAct, listAct] = kind === 'live' ? ['get_live_categories', 'get_live_streams']
    : kind === 'movies' ? ['get_vod_categories', 'get_vod_streams'] : ['get_series_categories', 'get_series'];
  const catsRaw = await (await getText(`${api}&action=${catAct}`)).json();
  const cats = {};
  if (Array.isArray(catsRaw)) for (const c of catsRaw) cats[c.category_id] = c.category_name;
  const arr = await (await getText(`${api}&action=${listAct}`)).json();
  if (!Array.isArray(arr)) throw new Error('The server returned no list (check the account)');
  return slimmers[kind](arr, cats);
});

// M3U playlist parser
function parseM3u(text) {
  const out = [];
  let cur = null, epg = '';
  const lines = text.split(/\r?\n/);
  for (let k = 0; k < lines.length; k++) {
    const ln = lines[k].trim();
    if (!ln) continue;
    if (ln.startsWith('#EXTM3U')) {
      const m = /url-tvg="([^"]*)"/i.exec(ln) || /x-tvg-url="([^"]*)"/i.exec(ln);
      if (m) epg = m[1];
    } else if (ln.startsWith('#EXTINF')) {
      const name = ln.substring(ln.lastIndexOf(',') + 1).trim();
      const logo = (/tvg-logo="([^"]*)"/i.exec(ln) || [])[1] || '';
      const group = (/group-title="([^"]*)"/i.exec(ln) || [])[1] || 'Other';
      const tid = (/tvg-id="([^"]*)"/i.exec(ln) || [])[1] || '';
      cur = { n: name || (/tvg-name="([^"]*)"/i.exec(ln) || [])[1] || 'Channel', l: logo, g: group, e: tid };
    } else if (ln.startsWith('#')) {
      continue;
    } else if (cur) {
      cur.u = ln; cur.i = out.length + 1;
      out.push(cur); cur = null;
    }
  }
  return { epg, list: out };
}
ipcMain.handle('m3u:load', async (_e, url) => {
  const text = await (await getText(url)).text();
  return parseM3u(text);
});

// ---------- window ----------
ipcMain.handle('win:fullscreen', (_e, on) => {
  if (!win) return false;
  win.setFullScreen(typeof on === 'boolean' ? on : !win.isFullScreen());
  return win.isFullScreen();
});
ipcMain.handle('win:isFullscreen', () => (win ? win.isFullScreen() : false));
ipcMain.handle('app:version', () => app.getVersion());
ipcMain.handle('app:openExternal', (_e, url) => { if (/^https?:\/\//i.test(url)) shell.openExternal(url); });
ipcMain.handle('app:quit', () => app.quit());

function createWindow() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({
    width: 1366, height: 800, minWidth: 900, minHeight: 560,
    backgroundColor: '#0b1330',
    title: 'JoyPlayer IPTV Pro',
    icon: path.join(__dirname, 'build', 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false,            // lets the player load streams from any provider (no CORS / mixed-content limits)
      autoplayPolicy: 'no-user-gesture-required',
      backgroundThrottling: false
    }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
  win.on('enter-full-screen', () => win.webContents.send('fs', true));
  win.on('leave-full-screen', () => win.webContents.send('fs', false));
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.on('closed', () => { win = null; });
}

app.whenReady().then(() => {
  // IPTV servers like a media-player User-Agent and dislike a browser Origin / Referer
  session.defaultSession.webRequest.onBeforeSendHeaders({ urls: ['http://*/*', 'https://*/*'] }, (details, cb) => {
    const h = details.requestHeaders;
    h['User-Agent'] = UA;
    delete h['Origin'];
    delete h['Referer'];
    cb({ requestHeaders: h });
  });
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => app.quit());
