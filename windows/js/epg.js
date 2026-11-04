'use strict';
/** Program guide (EPG) and catch-up for Xtream live channels. */
const Epg = {
  cache: new Map(),
  dec(s) {
    if (!s) return '';
    try {
      const bin = atob(String(s));
      const t = new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(bin, c => c.charCodeAt(0)));
      return /[\u0000-\u0008\u000e-\u001f]/.test(t) ? String(s).trim() : t.trim();
    } catch (e) { return String(s).trim(); }
  },
  parse(j) {
    const arr = j && j.epg_listings;
    if (!Array.isArray(arr)) return [];
    return arr.map(o => ({ title: this.dec(o.title), desc: this.dec(o.description), start: Number(o.start_timestamp), end: Number(o.stop_timestamp), startStr: o.start || '' }))
      .filter(p => p.start && p.end).sort((a, b) => a.start - b.start);
  },
  async load(list, it, action, extra) {
    if (list.type !== 'xtream') return [];
    const key = action + '|' + list.id + '|' + it.i;
    const hit = this.cache.get(key);
    if (hit && Date.now() - hit.t < 5 * 60000) return hit.v;
    let v = [];
    try {
      v = this.parse(await native.json(Urls.api(list) + '&action=' + action + '&stream_id=' + it.i + (extra || '')));
      if (!v.length) AppLog.add('epg', action + ': no listings for stream ' + it.i);
    } catch (e) { AppLog.add('epg', action + ' failed: ' + (e && e.message ? e.message : e)); }
    if (v.length) this.cache.set(key, { t: Date.now(), v });
    return v;
  },
  short(list, it) { return this.load(list, it, 'get_short_epg', '&limit=12'); },
  full(list, it) { return this.load(list, it, 'get_simple_data_table'); },
  hm(sec) { return new Date(sec * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); },
  day(sec) { return new Date(sec * 1000).toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' }); },
  /** Catch-up (timeshift) link of a past program. Uses the server's own clock text for the start. */
  catchupUrl(list, it, p) {
    const minutes = Math.max(1, Math.round((p.end - p.start) / 60)) + 1;
    let st;
    if (p.startStr && p.startStr.length >= 16) st = p.startStr.substring(0, 16).replace(' ', ':').replace(/:(\d{2})$/, '-$1');
    else {
      const d = new Date(p.start * 1000), z = (n) => String(n).padStart(2, '0');
      st = d.getFullYear() + '-' + z(d.getMonth() + 1) + '-' + z(d.getDate()) + ':' + z(d.getHours()) + '-' + z(d.getMinutes());
    }
    return `${baseOf(list)}/timeshift/${cred(list)}/${minutes}/${st}/${it.i}.ts`;
  },
  /** "Now / Next" block under the preview. isCurrent() tells whether the channel is still the selected one. Returns the current title. */
  async paint(box, list, it, isCurrent) {
    if (list.type !== 'xtream') { box.replaceChildren(); return ''; }
    const items = await this.short(list, it);
    if (!isCurrent()) return '';
    const now = Date.now() / 1000;
    const cur = items.find(p => p.start <= now && p.end > now);
    const next = items.find(p => p.start >= (cur ? cur.end - 1 : now));
    box.replaceChildren();
    if (!cur && !next) { box.append(h('div', { class: 'enone', text: 'No program information for this channel' })); return ''; }
    if (cur) {
      const pct = Math.min(100, Math.max(0, (now - cur.start) / (cur.end - cur.start) * 100));
      box.append(h('div', { class: 'enow' }, h('span', { class: 'tag', text: 'NOW' }), h('span', { class: 'tt', dir: 'auto', text: cur.title }), h('span', { class: 'tm', text: this.hm(cur.start) + ' – ' + this.hm(cur.end) })),
        h('div', { class: 'prog' }, h('i', { style: { width: pct + '%' } })));
    }
    if (next) box.append(h('div', { class: 'enext' }, h('span', { class: 'tag', text: 'NEXT' }), h('span', { class: 'tt', dir: 'auto', text: next.title }), h('span', { class: 'tm', text: this.hm(next.start) })));
    return cur ? cur.title : '';
  },
  /** Full guide of the channel in a window: past programs (⏪ when the provider keeps them), now, and the coming ones. */
  async guide(list, it, hooks) {
    const body = h('div', { class: 'glist' }, spinner('Loading the guide...'));
    const m = modal(h('div', { class: 'card guide' },
      h('div', { class: 'ghead' }, h('h3', { dir: 'auto', text: '📅 ' + it.n }), h('button', { class: 'btn ghost small', text: '✕', onclick: () => m.close() })),
      it.d > 0 ? h('div', { class: 'gnote', text: '⏪ ' + trf('Past programs can be played (last {0} days)', it.d) }) : null,
      body));
    const items = await this.full(list, it);
    if (!document.body.contains(m.el)) return;
    if (!items.length) { body.replaceChildren(h('div', { class: 'empty', text: 'No program information for this channel' })); return; }
    const now = Date.now() / 1000;
    const rows = [];
    let lastDay = '', nowRow = null;
    for (const p of items) {
      const dd = this.day(p.start);
      if (dd !== lastDay) { rows.push(h('div', { class: 'gday', text: dd })); lastDay = dd; }
      const isNow = p.start <= now && p.end > now, past = p.end <= now;
      const canPlay = past && it.d > 0 && p.start > now - it.d * 86400;
      const desc = h('div', { class: 'gdesc hidden', dir: 'auto', text: p.desc || '' });
      const row = h('div', { class: 'grow' + (isNow ? ' now' : '') + (past ? ' past' : '') },
        h('div', { class: 'gmain', onclick: () => { if (p.desc) desc.classList.toggle('hidden'); } },
          h('span', { class: 'gtm', text: this.hm(p.start) }),
          h('span', { class: 'gtt', dir: 'auto', text: (isNow ? '● ' : '') + p.title }),
          canPlay ? h('button', { class: 'btn small', text: '⏪ Play', onclick: (e) => { e.stopPropagation(); m.close(); hooks.catchup(p); } }) : null),
        p.desc ? desc : null);
      if (isNow) nowRow = row;
      rows.push(row);
    }
    body.replaceChildren(...rows);
    if (nowRow) nowRow.scrollIntoView({ block: 'center' });
  }
};
