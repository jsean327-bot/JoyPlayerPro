const { app, BrowserWindow, ipcMain, session, net, shell, Menu, powerSaveBlocker, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

const UA = 'VLC/3.0.18 LibVLC/3.0.18';
let win = null;

app.commandLine.appendSwitch('enable-blink-features', 'AudioVideoTracks');   // lets a movie with several audio languages switch between them
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
  live: (a, cats) => a.map(x => ({ i: x.stream_id, n: x.name || '', l: x.stream_icon || '', g: cats[x.category_id] || 'Other', e: x.epg_channel_id || '', d: Number(x.tv_archive) === 1 ? (Number(x.tv_archive_duration) || 1) : 0 })),
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

// ---------- XMLTV program guide for M3U playlists ----------
const xmltvCache = new Map();   // url -> { t, progs: Map(channelId -> [[start,end,title,desc]]), names: Map(lower display-name -> channelId) }
function xmlDec(s) {
  return String(s || '').replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (m, e) => {
    e = e.toLowerCase();
    if (e === 'amp') return '&'; if (e === 'lt') return '<'; if (e === 'gt') return '>'; if (e === 'quot') return '"'; if (e === 'apos') return "'";
    const c = e[1] === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
    try { return String.fromCodePoint(c); } catch { return ''; }
  }).trim();
}
function xmlTime(s) {
  const m = /^(\d{4})(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)?\s*([+-]\d{4})?/.exec(String(s || '').trim());
  if (!m) return 0;
  let t = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0));
  if (m[7]) { const sg = m[7][0] === '-' ? -1 : 1; t -= sg * ((+m[7].slice(1, 3)) * 60 + (+m[7].slice(3, 5))) * 60000; }
  return Math.floor(t / 1000);
}
async function loadXmltv(url) {
  const hit = xmltvCache.get(url);
  if (hit && Date.now() - hit.t < 3 * 3600e3) return hit;
  const res = await getText(url);
  let buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 2 && buf[0] === 0x1f && buf[1] === 0x8b) buf = require('zlib').gunzipSync(buf);
  const xml = buf.toString('utf8');
  const names = new Map(), progs = new Map();
  const lo = Date.now() / 1000 - 8 * 3600, hi = Date.now() / 1000 + 3 * 86400;
  let m;
  const rc = /<channel\s+id="([^"]*)"[^>]*>([\s\S]*?)<\/channel>/g;
  while ((m = rc.exec(xml))) {
    const id = xmlDec(m[1]), rn = /<display-name[^>]*>([\s\S]*?)<\/display-name>/g; let n;
    while ((n = rn.exec(m[2]))) names.set(xmlDec(n[1]).toLowerCase(), id);
  }
  const rp = /<programme\s+([^>]*)>([\s\S]*?)<\/programme>/g;
  while ((m = rp.exec(xml))) {
    const a = m[1];
    const st = xmlTime((/start="([^"]*)"/.exec(a) || [])[1]), en = xmlTime((/stop="([^"]*)"/.exec(a) || [])[1]);
    const ch = xmlDec((/channel="([^"]*)"/.exec(a) || [])[1]);
    if (!st || !en || !ch || en < lo || st > hi) continue;
    const t = /<title[^>]*>([\s\S]*?)<\/title>/.exec(m[2]), d = /<desc[^>]*>([\s\S]*?)<\/desc>/.exec(m[2]);
    let arr = progs.get(ch); if (!arr) progs.set(ch, arr = []);
    arr.push([st, en, xmlDec(t && t[1]), xmlDec(d && d[1]).slice(0, 500)]);
  }
  for (const a of progs.values()) a.sort((x, y) => x[0] - y[0]);
  const v = { t: Date.now(), progs, names };
  xmltvCache.set(url, v);
  if (xmltvCache.size > 3) xmltvCache.delete(xmltvCache.keys().next().value);
  return v;
}
ipcMain.handle('xmltv:get', async (_e, url, id, name) => {
  const g = await loadXmltv(url);
  const key = (id && g.progs.has(id)) ? id : (g.names.get(String(name || '').toLowerCase()) || id);
  return (g.progs.get(key) || []).map(p => ({ start: p[0], end: p[1], title: p[2], desc: p[3] }));
});

// ---------- Chromecast + external player ----------
const Cast = require('./cast');
const castSes = new Cast.Session();
ipcMain.handle('cast:scan', () => Cast.scan(3500));
ipcMain.handle('cast:play', async (_e, dev, media, at) => {
  try { await castSes.play(dev.host, dev.port, media, at); return { ok: true }; }
  catch (e) { return { ok: false, error: e && e.message ? e.message : String(e) }; }
});
ipcMain.handle('cast:status', () => castSes.status);
ipcMain.handle('cast:ctl', (_e, cmd, val) => { castSes.control(cmd, val); return true; });
ipcMain.handle('cast:stop', () => { castSes.stop(); return true; });
ipcMain.handle('ext:play', async (_e, url, title) => {
  const { spawn } = require('child_process');
  const pf = [process.env['ProgramFiles'], process.env['ProgramFiles(x86)'], process.env['LOCALAPPDATA'] && path.join(process.env['LOCALAPPDATA'], 'Programs')].filter(Boolean);
  const cands = [];
  for (const b of pf) { cands.push(path.join(b, 'VideoLAN', 'VLC', 'vlc.exe'), path.join(b, 'DAUM', 'PotPlayer', 'PotPlayerMini64.exe'), path.join(b, 'MPC-HC', 'mpc-hc64.exe'), path.join(b, 'mpv', 'mpv.exe')); }
  for (const p of cands) {
    if (fs.existsSync(p)) { try { spawn(p, [url], { detached: true, stdio: 'ignore' }).unref(); return { ok: true, app: path.basename(p) }; } catch { } }
  }
  try {   // no known player: a small playlist file opens with whatever program the PC uses for .m3u
    const f = path.join(app.getPath('temp'), 'joyplayer-open.m3u');
    fs.writeFileSync(f, '#EXTM3U\n#EXTINF:-1,' + String(title || 'Stream').replace(/[\r\n]/g, ' ') + '\n' + url + '\n');
    const err = await shell.openPath(f);
    return err ? { ok: false, error: err } : { ok: true, app: 'default' };
  } catch (e) { return { ok: false, error: String(e && e.message || e) }; }
});

// ---------- keep the screen awake while something plays ----------
let blockerId = null;
ipcMain.handle('power:awake', (_e, on) => {
  if (on && blockerId === null) blockerId = powerSaveBlocker.start('prevent-display-sleep');
  else if (!on && blockerId !== null) { try { powerSaveBlocker.stop(blockerId); } catch {} blockerId = null; }
  return true;
});

// ---------- secure DNS (DoH): 0 = off, 1 Cloudflare, 2 Google, 3 Quad9 ----------
const DOH = { 1: 'https://cloudflare-dns.com/dns-query', 2: 'https://dns.google/dns-query', 3: 'https://dns.quad9.net/dns-query' };
function dohFile() { return path.join(storeDir(), 'doh.json'); }
function dohMode() { try { return Number(JSON.parse(fs.readFileSync(dohFile(), 'utf8')).mode) || 0; } catch { return 0; } }
function applyDoh(mode) {
  try {
    if (mode && DOH[mode]) app.configureHostResolver({ secureDnsMode: 'automatic', secureDnsServers: [DOH[mode]] });   // automatic = falls back to the normal DNS
    else app.configureHostResolver({ secureDnsMode: 'off', secureDnsServers: [] });
  } catch {}
}
ipcMain.handle('doh:get', () => dohMode());
ipcMain.handle('doh:set', (_e, mode) => {
  mode = Number(mode) || 0;
  fs.writeFileSync(dohFile(), JSON.stringify({ mode }));
  applyDoh(mode);
  return mode;
});

// ---------- subtitle file (.srt / .vtt), Arabic files saved as Windows-1256 are read too ----------
ipcMain.handle('sub:open', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Choose a subtitle file', properties: ['openFile'], filters: [{ name: 'Subtitles', extensions: ['srt', 'vtt'] }] });
  if (r.canceled || !r.filePaths[0]) return null;
  const buf = await fs.promises.readFile(r.filePaths[0]);
  let text;
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(buf); } catch { text = new TextDecoder('windows-1256').decode(buf); }
  return { name: path.basename(r.filePaths[0]), text };
});

// ---------- backup / restore ----------
ipcMain.handle('backup:export', async (_e, text) => {
  const r = await dialog.showSaveDialog(win, { title: 'Save backup', defaultPath: 'JoyPlayer-backup.json', filters: [{ name: 'JSON', extensions: ['json'] }] });
  if (r.canceled || !r.filePath) return false;
  await fs.promises.writeFile(r.filePath, text, 'utf8');
  return r.filePath;
});
ipcMain.handle('backup:import', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Choose a backup file', properties: ['openFile'], filters: [{ name: 'JSON', extensions: ['json'] }] });
  if (r.canceled || !r.filePaths[0]) return null;
  return await fs.promises.readFile(r.filePaths[0], 'utf8');
});

// ---------- updates (Windows releases are tagged win-<build number>; the build number is the 3rd part of the version) ----------
const REPO = 'jsean327-bot/JoyPlayerPro';
ipcMain.handle('update:check', async () => {
  const cur = Number((app.getVersion().split('.')[2]) || 0);
  const res = await net.fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`, { headers: { 'Accept': 'application/vnd.github+json' } });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const rels = (await res.json()).filter(r => /^win-\d+$/.test(r.tag_name));
  rels.sort((a, b) => Number(b.tag_name.slice(4)) - Number(a.tag_name.slice(4)));
  const rel = rels[0];
  if (!rel) return { newer: false, cur, n: 0 };
  const n = Number(rel.tag_name.slice(4));
  const portable = !!process.env.PORTABLE_EXECUTABLE_DIR;
  const want = `JoyPlayer-IPTV-Pro-${portable ? 'Portable' : 'Setup'}-${process.arch}.exe`;
  const asset = (rel.assets || []).find(a => a.name === want);
  return { newer: n > cur && cur > 0, cur, n, url: asset ? asset.browser_download_url : '', name: asset ? asset.name : '', page: rel.html_url, portable };
});
ipcMain.handle('update:install', async (_e, url, name, portable) => {
  if (!/^https:\/\/github\.com\//.test(url) || !/^[\w.-]+\.exe$/.test(name)) throw new Error('Bad update link');
  const res = await net.fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const dest = path.join(app.getPath('temp'), name);
  await fs.promises.writeFile(dest, Buffer.from(await res.arrayBuffer()));
  if (portable) { shell.showItemInFolder(dest); return 'folder'; }
  await shell.openPath(dest);
  setTimeout(() => app.quit(), 800);
  return 'installer';
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
  applyDoh(dohMode());
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => app.quit());
