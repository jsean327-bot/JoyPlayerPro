'use strict';
const MAX_AGE = 24 * 3600 * 1000;

const Store = {
  state: { lists: [], active: null, settings: { liveFmt: 'auto', sort: { live: 0, movies: 1, series: 1 } }, user: {}, resume: {} },
  async load() {
    try {
      const t = await native.read('state');
      if (t) {
        const s = JSON.parse(t);
        Object.assign(this.state, s);
        this.state.settings = Object.assign({ liveFmt: 'auto' }, s.settings);
        this.state.settings.sort = Object.assign({ live: 0, movies: 1, series: 1 }, (s.settings || {}).sort);
      }
    } catch (e) { }
  },
  save: debounce(() => native.write('state', JSON.stringify(Store.state)), 400),
  list() { return this.state.lists.find(l => l.id === this.state.active) || null; },
  user(list) {
    const id = list ? list.id : '_';
    const u = this.state.user[id] || (this.state.user[id] = {});
    u.fav = u.fav || {}; u.favCats = u.favCats || {}; u.recent = u.recent || [];
    for (const k of ['live', 'movies', 'series']) { u.fav[k] = u.fav[k] || []; u.favCats[k] = u.favCats[k] || []; }
    return u;
  },
  saveResume(url, pos, dur) {
    const r = this.state.resume;
    r[url] = [Math.floor(pos), Math.floor(dur), Date.now()];
    const keys = Object.keys(r);
    if (keys.length > 400) keys.sort((a, b) => r[a][2] - r[b][2]).slice(0, keys.length - 400).forEach(k => delete r[k]);
    this.save();
  }
};

function baseOf(list) {
  let s = (list.server || '').trim();
  if (!/^https?:\/\//i.test(s)) s = 'http://' + s;
  return s.replace(/\/+$/, '').replace(/\/(player_api|get|panel_api|c)(\.php)?.*$/i, '');
}
const cred = (l) => encodeURIComponent(l.user) + '/' + encodeURIComponent(l.pass);
const Urls = {
  api(l) { return `${baseOf(l)}/player_api.php?username=${encodeURIComponent(l.user)}&password=${encodeURIComponent(l.pass)}`; },
  live(l, it) {
    if (l.type === 'm3u') return { direct: it.u };
    const b = baseOf(l);
    return { m3u8: `${b}/live/${cred(l)}/${it.i}.m3u8`, ts: `${b}/live/${cred(l)}/${it.i}.ts` };
  },
  movie(l, it, ext) { return `${baseOf(l)}/movie/${cred(l)}/${it.i}.${ext || it.e || 'mp4'}`; },
  episode(l, id, ext) { return `${baseOf(l)}/series/${cred(l)}/${id}.${ext || 'mp4'}`; }
};

const Content = {
  mem: {}, refreshing: new Set(),
  async fetch(list, kind) {
    if (list.type === 'm3u') {
      if (kind !== 'live') return [];
      const r = await native.m3u(list.url);
      if (r.epg) { list.epg = r.epg; Store.save(); }
      return r.list;
    }
    return await native.xtList(baseOf(list), list.user, list.pass, kind);
  },
  prep(arr) {
    const byCat = new Map();
    for (const it of arr) {
      let a = byCat.get(it.g);
      if (!a) byCat.set(it.g, a = []);
      a.push(it);
    }
    const cats = [...byCat].map(([name, items]) => ({ name, count: items.length }));
    return { items: arr, byCat, cats, indexed: false };
  },
  /** Saved copy first (instant), refreshed quietly in the background when it is old. */
  async get(list, kind) {
    const key = list.id + '_' + kind;
    if (this.mem[key]) return this.mem[key];
    const txt = await native.read('c_' + key);
    if (txt) {
      let arr = null;
      try { arr = JSON.parse(txt); } catch (e) { }
      if (arr && arr.length) {
        this.mem[key] = this.prep(arr);
        const age = await native.age('c_' + key);
        if (age < 0 || age > MAX_AGE) this.refreshLater(list, kind, key);
        return this.mem[key];
      }
    }
    const arr = await this.fetch(list, kind);
    this.mem[key] = this.prep(arr);
    if (arr.length) native.write('c_' + key, JSON.stringify(arr));
    return this.mem[key];
  },
  refreshLater(list, kind, key) {
    if (this.refreshing.has(key)) return;
    this.refreshing.add(key);
    setTimeout(async () => {
      try {
        const arr = await this.fetch(list, kind);
        if (arr.length) await native.write('c_' + key, JSON.stringify(arr));   // used the next time the app starts
      } catch (e) { }
    }, 15000);
  },
  async reload(list) {
    await native.clear('c_' + list.id + '_');
    for (const k of Object.keys(this.mem)) if (k.startsWith(list.id + '_')) delete this.mem[k];
    this.refreshing.clear();
  },
  ensureIndex(d) {
    if (d.indexed) return;
    for (const it of d.items) it._s = norm(it.n + ' ' + it.g);
    d.indexed = true;
  }
};

const Account = {
  async info(list) {
    if (list.type !== 'xtream') return null;
    return await native.json(Urls.api(list));
  }
};
