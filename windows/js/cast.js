'use strict';
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
