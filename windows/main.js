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
// (Chromecast code lives here, in main.js, so it is always packaged)
const Cast = (() => {
  const module = { exports: {} };
/* Chromecast (Cast v2) without any npm package: mDNS discovery + the Cast TLS protocol. */
const dgram = require('dgram');
const tls = require('tls');

// ---------- tiny DNS reader / writer (mDNS) ----------
function qname(name) {
  const parts = name.split('.').filter(Boolean);
  return Buffer.concat([...parts.map(p => Buffer.concat([Buffer.from([p.length]), Buffer.from(p)])), Buffer.from([0])]);
}
function buildQuery(name) {
  const h = Buffer.alloc(12); h.writeUInt16BE(1, 4);                        // one question
  const q = Buffer.alloc(4); q.writeUInt16BE(12, 0); q.writeUInt16BE(0x8001, 2);   // PTR, IN, unicast-response bit
  return Buffer.concat([h, qname(name), q]);
}
function readName(buf, off) {
  const parts = []; let jumped = false, end = off, guard = 0;
  while (guard++ < 64) {
    const len = buf[off];
    if (len === undefined) break;
    if (len === 0) { off++; break; }
    if ((len & 0xc0) === 0xc0) { const ptr = ((len & 0x3f) << 8) | buf[off + 1]; if (!jumped) end = off + 2; jumped = true; off = ptr; continue; }
    parts.push(buf.toString('utf8', off + 1, off + 1 + len)); off += 1 + len;
  }
  if (!jumped) end = off;
  return [parts.join('.'), end];
}
function parsePacket(buf) {
  const out = [];
  if (buf.length < 12) return out;
  let off = 12;
  const qd = buf.readUInt16BE(4), total = buf.readUInt16BE(6) + buf.readUInt16BE(8) + buf.readUInt16BE(10);
  for (let i = 0; i < qd; i++) { const [, e] = readName(buf, off); off = e + 4; }
  for (let i = 0; i < total && off < buf.length; i++) {
    const [name, e] = readName(buf, off); off = e;
    if (off + 10 > buf.length) break;
    const type = buf.readUInt16BE(off), len = buf.readUInt16BE(off + 8); off += 10;
    const d = off; off += len;
    if (off > buf.length) break;
    if (type === 12) out.push({ type: 'PTR', name, target: readName(buf, d)[0] });
    else if (type === 33) out.push({ type: 'SRV', name, port: buf.readUInt16BE(d + 4), target: readName(buf, d + 6)[0] });
    else if (type === 1 && len === 4) out.push({ type: 'A', name, ip: [...buf.slice(d, d + 4)].join('.') });
    else if (type === 16) {
      const txt = {}; let p = d;
      while (p < d + len) { const l = buf[p]; const s = buf.toString('utf8', p + 1, p + 1 + l); const k = s.indexOf('='); if (k > 0) txt[s.slice(0, k)] = s.slice(k + 1); p += 1 + l; }
      out.push({ type: 'TXT', name, txt });
    }
  }
  return out;
}

/** Looks for Chromecast / Google TV devices for a few seconds. Resolves [{name, host, port}]. */
function scan(ms) {
  return new Promise((resolve) => {
    const found = new Map(), ptr = new Set(), srv = new Map(), addr = new Map(), txts = new Map();
    let sock;
    const finish = () => {
      try { sock.close(); } catch { }
      for (const inst of ptr) {
        const s = srv.get(inst); if (!s) continue;
        const ip = addr.get(s.target); if (!ip) continue;
        const t = txts.get(inst) || {};
        found.set(ip + ':' + s.port, { name: t.fn || inst.split('._googlecast')[0], model: t.md || '', host: ip, port: s.port });
      }
      resolve([...found.values()]);
    };
    try {
      sock = dgram.createSocket({ type: 'udp4', reuseAddr: true });
      sock.on('error', finish);
      sock.on('message', (msg) => {
        for (const r of parsePacket(msg)) {
          if (r.type === 'PTR' && r.name.endsWith('_googlecast._tcp.local')) ptr.add(r.target);
          else if (r.type === 'SRV') srv.set(r.name, r);
          else if (r.type === 'A') addr.set(r.name, r.ip);
          else if (r.type === 'TXT') txts.set(r.name, r.txt);
        }
      });
      sock.bind(0, () => {
        const q = buildQuery('_googlecast._tcp.local');
        const send = () => { try { sock.send(q, 5353, '224.0.0.251'); } catch { } };
        send(); setTimeout(send, 700); setTimeout(send, 1600);
        setTimeout(finish, ms || 3500);
      });
    } catch { finish(); }
  });
}

// ---------- Cast v2 protobuf frame ----------
function varint(n) { const b = []; while (n > 127) { b.push((n & 127) | 128); n >>>= 7; } b.push(n); return Buffer.from(b); }
function field(tag, str) { const v = Buffer.from(str, 'utf8'); return Buffer.concat([Buffer.from([tag]), varint(v.length), v]); }
function frame(src, dst, ns, payload) {
  const body = Buffer.concat([Buffer.from([0x08, 0x00]), field(0x12, src), field(0x1a, dst), field(0x22, ns), Buffer.from([0x28, 0x00]), field(0x32, JSON.stringify(payload))]);
  const len = Buffer.alloc(4); len.writeUInt32BE(body.length);
  return Buffer.concat([len, body]);
}
function readVarint(buf, off) { let r = 0, s = 0; for (;;) { const b = buf[off++]; r |= (b & 127) << s; if (!(b & 128)) break; s += 7; } return [r, off]; }
function unframe(body) {
  const m = { src: '', dst: '', ns: '', payload: '' }; let off = 0;
  while (off < body.length) {
    const tag = body[off++]; const f = tag >> 3, wt = tag & 7;
    if (wt === 0) { [, off] = readVarint(body, off); }
    else if (wt === 2) {
      let len; [len, off] = readVarint(body, off); const s = body.toString('utf8', off, off + len); off += len;
      if (f === 2) m.src = s; else if (f === 3) m.dst = s; else if (f === 4) m.ns = s; else if (f === 6) m.payload = s;
    } else break;
  }
  return m;
}

const NS = { con: 'urn:x-cast:com.google.cast.tp.connection', hb: 'urn:x-cast:com.google.cast.tp.heartbeat', rec: 'urn:x-cast:com.google.cast.receiver', media: 'urn:x-cast:com.google.cast.media' };

class Session {
  constructor() { this.sock = null; this.rid = 1; this.status = { state: 'idle', time: 0, duration: 0, volume: 1, muted: false, error: '' }; this.pending = new Map(); this.buf = Buffer.alloc(0); }
  send(dst, ns, payload) { if (this.sock && !this.sock.destroyed) this.sock.write(frame('sender-0', dst, ns, payload)); }
  request(dst, ns, payload, ms) {
    const id = this.rid++; payload.requestId = id;
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => { this.pending.delete(id); reject(new Error('The TV did not answer')); }, ms || 12000);
      this.pending.set(id, (m) => { clearTimeout(t); resolve(m); });
      this.send(dst, ns, payload);
    });
  }
  onData(d) {
    this.buf = Buffer.concat([this.buf, d]);
    while (this.buf.length >= 4) {
      const n = this.buf.readUInt32BE(0);
      if (this.buf.length < 4 + n) break;
      const m = unframe(this.buf.slice(4, 4 + n)); this.buf = this.buf.slice(4 + n);
      let j = {}; try { j = JSON.parse(m.payload); } catch { }
      if (m.ns === NS.hb && j.type === 'PING') this.send('receiver-0', NS.hb, { type: 'PONG' });
      if (j.type === 'MEDIA_STATUS' && j.status && j.status[0]) {
        const s = j.status[0]; this.mediaSessionId = s.mediaSessionId;
        this.status.state = String(s.playerState || '').toLowerCase();
        this.status.time = s.currentTime || 0;
        if (s.media && s.media.duration) this.status.duration = s.media.duration;
        if (s.idleReason === 'ERROR') this.status.error = 'The TV could not play this stream';
      }
      if (j.type === 'RECEIVER_STATUS' && j.status && j.status.volume) { this.status.volume = j.status.volume.level; this.status.muted = !!j.status.volume.muted; }
      if ((j.type === 'LOAD_FAILED' || j.type === 'INVALID_REQUEST') ) this.status.error = 'The TV refused this stream';
      if (j.requestId && this.pending.has(j.requestId)) { const f = this.pending.get(j.requestId); this.pending.delete(j.requestId); f({ ns: m.ns, ...j }); }
    }
  }
  connect(host, port) {
    return new Promise((resolve, reject) => {
      this.close();
      const s = this.sock = tls.connect({ host, port: port || 8009, rejectUnauthorized: false }, () => {
        this.send('receiver-0', NS.con, { type: 'CONNECT' });
        this.hbT = setInterval(() => this.send('receiver-0', NS.hb, { type: 'PING' }), 5000);
        this.stT = setInterval(() => { if (this.transport) this.send(this.transport, NS.media, { type: 'GET_STATUS', requestId: this.rid++ }); }, 1500);
        resolve();
      });
      s.on('data', (d) => this.onData(d));
      s.on('error', (e) => { this.status.error = e.message; reject(e); });
      s.on('close', () => { clearInterval(this.hbT); clearInterval(this.stT); this.status.state = 'closed'; });
    });
  }
  async play(host, port, media, startAt) {
    this.status = { state: 'loading', time: 0, duration: 0, volume: this.status.volume, muted: false, error: '' };
    await this.connect(host, port);
    const r = await this.request('receiver-0', NS.rec, { type: 'LAUNCH', appId: 'CC1AD845' });
    const app = (r.status && r.status.applications || []).find(a => a.appId === 'CC1AD845');
    if (!app) throw new Error('The TV did not start the player');
    this.transport = app.transportId; this.sessionId = app.sessionId;
    this.send(this.transport, NS.con, { type: 'CONNECT' });
    this.send(this.transport, NS.media, { type: 'GET_STATUS', requestId: this.rid++ });
    this.send(this.transport, NS.media, { type: 'LOAD', requestId: this.rid++, sessionId: this.sessionId, autoplay: true, currentTime: startAt || 0,
      media: { contentId: media.url, streamType: media.live ? 'LIVE' : 'BUFFERED', contentType: media.type, metadata: { type: 0, metadataType: 0, title: media.title || '' } } });
  }
  control(cmd, val) {
    if (!this.sock || this.sock.destroyed || !this.transport) return;
    const id = this.mediaSessionId;
    if (cmd === 'pause' && id != null) this.send(this.transport, NS.media, { type: 'PAUSE', requestId: this.rid++, mediaSessionId: id });
    else if (cmd === 'play' && id != null) this.send(this.transport, NS.media, { type: 'PLAY', requestId: this.rid++, mediaSessionId: id });
    else if (cmd === 'seek' && id != null) this.send(this.transport, NS.media, { type: 'SEEK', requestId: this.rid++, mediaSessionId: id, currentTime: Math.max(0, Number(val) || 0) });
    else if (cmd === 'volume') this.send('receiver-0', NS.rec, { type: 'SET_VOLUME', requestId: this.rid++, volume: { level: Math.min(1, Math.max(0, Number(val) || 0)) } });
    else if (cmd === 'mute') this.send('receiver-0', NS.rec, { type: 'SET_VOLUME', requestId: this.rid++, volume: { muted: !!val } });
  }
  stop() {
    try { if (this.sessionId) this.send('receiver-0', NS.rec, { type: 'STOP', requestId: this.rid++, sessionId: this.sessionId }); } catch { }
    setTimeout(() => this.close(), 300);
  }
  close() {
    clearInterval(this.hbT); clearInterval(this.stT);
    if (this.sock) { try { this.sock.destroy(); } catch { } this.sock = null; }
    this.transport = this.sessionId = this.mediaSessionId = null;
  }
}

module.exports = { scan, Session, parsePacket, buildQuery, frame, unframe };

  return module.exports;
})();
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

// ---------- connection test (Settings) ----------
ipcMain.handle('net:test', async (_e, tests) => {
  const out = [];
  for (const t of tests) {
    const t0 = Date.now(), c = new AbortController(), to = setTimeout(() => c.abort(), 9000);
    try {
      const res = await net.fetch(t.url, { headers: { 'User-Agent': UA, 'Accept': '*/*' }, signal: c.signal, redirect: 'follow' });
      let bytes = 0;
      if (t.read && res.body) { const rd = res.body.getReader(); const r = await rd.read(); bytes = r.value ? r.value.length : 0; try { await rd.cancel(); } catch { } }
      out.push({ n: t.n, status: res.status, ms: Date.now() - t0, bytes, ct: res.headers.get('content-type') || '' });
    } catch (e) { out.push({ n: t.n, status: 0, ms: Date.now() - t0, err: String((e && e.cause && e.cause.message) || (e && e.message) || e) }); }
    clearTimeout(to); try { c.abort(); } catch { }
  }
  return out;
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
