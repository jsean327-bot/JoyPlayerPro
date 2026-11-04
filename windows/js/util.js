'use strict';
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    const v = attrs[k];
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : document.createTextNode(String(c)));
  return el;
}
const $ = (s, r) => (r || document).querySelector(s);
function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function debounce(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

// Search normalisation: case, accents, Arabic letter variants and diacritics
function norm(s) {
  return String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ًͯ-ٰٟـ]/g, '')
    .replace(/[أإآٱ]/g, 'ا').replace(/ى/g, 'ي').replace(/ة/g, 'ه').replace(/\s+/g, ' ').trim();
}
// sort key: ignores leading symbols / flags / "|" so "★ AR | MBC" sorts under A
function sortKey(s) { return norm(s).replace(/^[^\p{L}\p{N}]+/u, ''); }
const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

/** Short in-app log (Settings > Error Log): what failed and what the server answered. */
const AppLog = {
  lines: [],
  async load() {
    try { const t = await native.read('log'); if (t) this.lines = JSON.parse(t); } catch (e) { }
  },
  add(tag, msg) {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    this.lines.unshift(`${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())} [${tag}] ${String(msg).replace(/\/(live|movie|series)\/[^/\s]+\/[^/\s]+\//g, '/$1/•••/•••/')}`);
    if (this.lines.length > 200) this.lines.length = 200;
    this.save();
  },
  save: debounce(() => { native.write('log', JSON.stringify(AppLog.lines)); }, 1500),
  clear() { this.lines = []; native.write('log', '[]'); },
  text() { return 'JoyPlayer IPTV Pro (Windows) - error report\n\n' + (this.lines.join('\n') || '(empty)'); }
};

/** Keeps the screen on while a player is playing. */
const KeepAwake = {
  s: new Set(),
  set(who, on) {
    const had = this.s.size > 0;
    if (on) this.s.add(who); else this.s.delete(who);
    if ((this.s.size > 0) !== had) native.awake(this.s.size > 0);
  }
};

/** Picture mode for every video: Fit / Zoom / Stretch. */
const Fit = {
  names: ['Fit', 'Zoom (fills the screen)', 'Stretch'], css: ['contain', 'cover', 'fill'],
  apply() {
    let st = document.getElementById('fitcss');
    if (!st) { st = document.createElement('style'); st.id = 'fitcss'; document.head.append(st); }
    st.textContent = 'video{object-fit:' + this.css[(Store.state.settings.fit || 0) % 3] + ' !important}';
  },
  cycle() {
    const s = Store.state.settings; s.fit = ((s.fit || 0) + 1) % 3; Store.save(); this.apply();
    toast('Picture: ' + this.names[s.fit], 1200);
  }
};

/** Sleep timer: pauses everything after the chosen minutes. */
const Sleep = {
  steps: [0, 15, 30, 45, 60, 90, 120], idx: 0, t: 0,
  cycle() {
    this.idx = (this.idx + 1) % this.steps.length;
    this.set(this.steps[this.idx]);
  },
  set(min) {
    clearTimeout(this.t);
    if (!min) { this.idx = 0; toast('Sleep timer: off', 1400); return; }
    this.t = setTimeout(() => {
      document.querySelectorAll('video').forEach(v => v.pause());
      this.idx = 0; toast('Sleep timer: stopped', 5000);
    }, min * 60000);
    toast('Sleep timer: ' + min + ' min', 1600);
  }
};

/** Subtitles from a file (.srt / .vtt) with a size setting. */
const Subs = {
  applyCss() {
    let st = document.getElementById('cuecss');
    if (!st) { st = document.createElement('style'); st.id = 'cuecss'; document.head.append(st); }
    const px = Store.state.settings.subSize || 28;
    st.textContent = 'video::cue{font-size:' + px + 'px;color:#fff;background:rgba(0,0,0,.6);font-family:"Segoe UI",Tahoma,sans-serif}';
  },
  size(d) {
    const s = Store.state.settings; s.subSize = Math.min(60, Math.max(14, (s.subSize || 28) + d)); Store.save(); this.applyCss();
    toast('Subtitle size ' + s.subSize, 900);
  },
  async load(video) {
    const r = await native.openSub();
    if (!r) return;
    let txt = r.text.replace(/^\uFEFF/, '');
    if (!/^WEBVTT/.test(txt)) txt = 'WEBVTT\n\n' + txt.replace(/\r/g, '').replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');
    this.off(video, true);
    const tr = document.createElement('track');
    tr.kind = 'subtitles'; tr.label = r.name; tr.default = true;
    tr.src = URL.createObjectURL(new Blob([txt], { type: 'text/vtt' }));
    video.append(tr);
    tr.track.mode = 'showing';
    toast('Subtitles: ' + r.name, 1800);
  },
  off(video, quiet) {
    video.querySelectorAll('track').forEach(t => { try { t.track.mode = 'disabled'; } catch (e) { } t.remove(); });
    if (!quiet) toast('Subtitles off', 1200);
  }
};

let toastTimer = 0;
function toast(msg, ms) {
  const t = $('#toast');
  t.textContent = msg; t.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('on'), ms || 2200);
}
function fmtDate(sec) {
  if (!sec) return '';
  const d = new Date(Number(sec) * 1000);
  return isNaN(d) ? '' : d.toLocaleDateString();
}
function fmtTime(s) {
  s = Math.max(0, Math.floor(s || 0));
  const hh = Math.floor(s / 3600), mm = Math.floor((s % 3600) / 60), ss = s % 60;
  return (hh ? hh + ':' + String(mm).padStart(2, '0') : mm) + ':' + String(ss).padStart(2, '0');
}
function modal(content, onClose) {
  const m = h('div', { class: 'modal', onmousedown: (e) => { if (e.target === m) close(); } }, content);
  const keyh = (e) => { if (e.key === 'Escape') { e.stopPropagation(); close(); } };
  function close() { document.removeEventListener('keydown', keyh, true); m.remove(); if (onClose) onClose(); }
  document.addEventListener('keydown', keyh, true);
  document.body.append(m);
  return { el: m, close };
}
function spinner(text) { return h('div', { class: 'loading' }, h('div', { class: 'spin' }), h('div', { text: text || 'Loading...' })); }
