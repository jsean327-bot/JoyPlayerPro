'use strict';
/* ---------------- movie / series detail + VOD player ---------------- */
const VodPlayer = (() => {
  let el = null, engine = null, hideT = 0, ctx = null, saveT = 0, speedSel = null;
  const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];
  function setSpeed(v, d) {
    const i = Math.min(SPEEDS.length - 1, Math.max(0, SPEEDS.indexOf(v.playbackRate) + d));
    v.playbackRate = SPEEDS[i]; if (speedSel) speedSel.value = String(SPEEDS[i]);
    toast('Speed ' + SPEEDS[i] + 'x', 900);
  }
  function audioMenu(video) {
    const at = video.audioTracks;
    if (!at || at.length < 2) { toast('This video has only one audio track', 1800); return; }
    const m = modal(h('div', { class: 'card form' }, h('h3', { text: 'Audio' }),
      ...[...Array(at.length).keys()].map(i => h('button', {
        class: 'btn' + (at[i].enabled ? '' : ' ghost'), style: { display: 'block', width: '100%', margin: '6px 0', textAlign: 'left' },
        text: (at[i].enabled ? '✓ ' : '') + (at[i].label || at[i].language || ('Track ' + (i + 1))),
        onclick: () => { for (let k = 0; k < at.length; k++) at[k].enabled = (k === i); m.close(); }
      }))));
  }
  function subMenu(video) {
    const m = modal(h('div', { class: 'card form' }, h('h3', { text: 'Subtitles' }),
      h('div', { class: 'd', style: { color: 'var(--muted)', fontSize: '13px', marginBottom: '8px' }, text: 'Choose an .srt or .vtt file (Arabic files work too).' }),
      h('div', { class: 'acts', style: { justifyContent: 'flex-start', flexWrap: 'wrap' } },
        h('button', { class: 'btn', text: '📂 Load file...', onclick: async () => { m.close(); await Subs.load(video); } }),
        h('button', { class: 'btn ghost', text: 'Off', onclick: () => { Subs.off(video); m.close(); } }),
        h('button', { class: 'btn ghost', text: 'A−', onclick: () => Subs.size(-2) }),
        h('button', { class: 'btn ghost', text: 'A+', onclick: () => Subs.size(2) }))));
  }
  function open(c) {                       // c = {title, url, key, next(), onEnd}
    close(true);
    ctx = c;
    const video = h('video', { controls: true, autoplay: true });
    const msg = h('div', { class: 'msg', text: 'Loading...' });
    video.volume = Number(localStorage.getItem('vol') || 1);
    speedSel = h('select', { title: 'Speed ( [ and ] )' }, SPEEDS.map(x => h('option', { value: x, text: x + 'x' })));
    speedSel.value = '1';
    speedSel.addEventListener('change', () => { video.playbackRate = Number(speedSel.value); });
    const bar = h('div', { class: 'bar' },
      h('button', { class: 'btn ghost', text: '← Back', onclick: () => close() }),
      h('div', { class: 't', dir: 'auto', text: c.title }),
      speedSel,
      h('button', { class: 'btn ghost', text: '🔊 Audio', onclick: () => audioMenu(video) }),
      h('button', { class: 'btn ghost', text: '💬 Subtitles', onclick: () => subMenu(video) }),
      h('button', { class: 'btn ghost', text: '⤢', title: 'Picture: Fit / Zoom / Stretch (Z)', onclick: () => Fit.cycle() }),
      h('button', { class: 'btn ghost', text: '😴', title: 'Sleep timer (S)', onclick: () => Sleep.cycle() }),
      c.next ? h('button', { class: 'btn ghost', text: 'Next ⏭', onclick: () => playNext() }) : null);
    el = h('div', { id: 'vod' }, bar, video, msg);
    document.body.append(el);
    native.fullscreen(true);
    engine = new Engine(video);
    const resume = (Store.state.resume[c.url] || [0])[0];
    const cbs = {
      ready: () => msg.classList.add('hidden'),
      playing: () => msg.classList.add('hidden'),
      status: (m) => { msg.textContent = m; msg.classList.remove('hidden'); },
      error: (m) => { msg.textContent = m; msg.classList.remove('hidden'); if (c.stream) AppLog.add('catchup', m); }
    };
    if (c.stream) engine.live({ direct: c.url }, cbs);      // catch-up: a recorded stream, played like a live one
    else engine.vod(c.url, resume, cbs);
    video.addEventListener('playing', () => msg.classList.add('hidden'));
    video.addEventListener('waiting', () => { msg.textContent = 'Buffering...'; msg.classList.remove('hidden'); });
    video.addEventListener('ended', () => { if (!c.stream) Store.saveResume(c.url, 0, 0); if (c.next) playNext(); else close(); });
    saveT = setInterval(() => { if (!c.stream && video.duration && video.currentTime > 5) Store.saveResume(c.url, video.currentTime, video.duration); }, 8000);
    el.addEventListener('mousemove', wake); wake();
  }
  function wake() { if (!el) return; el.classList.remove('idle'); clearTimeout(hideT); hideT = setTimeout(() => el && el.classList.add('idle'), 3200); }
  function playNext() { const c = ctx; const n = c.next && c.next(); if (n) open(n); else close(); }
  function close(silent) {
    clearInterval(saveT);
    if (el) {
      const v = el.querySelector('video');
      if (v && ctx && !ctx.stream && v.duration && v.currentTime > 5 && !v.ended) Store.saveResume(ctx.url, v.currentTime, v.duration);
      if (engine) engine.stop();
      el.remove(); el = null; engine = null;
      if (!silent) native.fullscreen(false);
    }
    if (!silent && ctx && ctx.onClose) ctx.onClose();
    if (!silent) ctx = null;
  }
  function onKey(e) {
    if (!el) return false;
    const v = el.querySelector('video');
    wake();
    switch (e.key) {
      case 'Escape': close(); return true;
      case 'ArrowLeft': v.currentTime = Math.max(0, v.currentTime - (e.shiftKey ? 60 : 10)); return true;
      case 'ArrowRight': v.currentTime = v.currentTime + (e.shiftKey ? 60 : 10); return true;
      case 'ArrowUp': v.volume = Math.min(1, v.volume + 0.05); localStorage.setItem('vol', String(v.volume)); toast('Volume ' + Math.round(v.volume * 100) + '%', 800); return true;
      case 'ArrowDown': v.volume = Math.max(0, v.volume - 0.05); localStorage.setItem('vol', String(v.volume)); toast('Volume ' + Math.round(v.volume * 100) + '%', 800); return true;
      case ']': setSpeed(v, 1); return true;
      case '[': setSpeed(v, -1); return true;
      case 'z': case 'Z': Fit.cycle(); return true;
      case 's': case 'S': Sleep.cycle(); return true;
      case ' ': v.paused ? v.play() : v.pause(); return true;
      case 'm': case 'M': v.muted = !v.muted; return true;
      case 'f': case 'F': native.isFullscreen().then(f => native.fullscreen(!f)); return true;
      case 'n': case 'N': if (ctx && ctx.next) playNext(); return true;
    }
    return false;
  }
  return { open, close, onKey, isOpen: () => !!el };
})();

const Detail = {
  async open(kind, it, fav) {
    const list = Store.list(); if (!list) return;
    const isSeries = kind === 'series';
    const playIt = (c) => { Store.pushRecentVod(list, kind, it.i); VodPlayer.open(c); };
    const body = h('div', { class: 'info-body' }, spinner('Loading details...'));
    const favBtn = h('button', { class: 'btn ghost', text: fav.fav ? '' : '', onclick: () => { fav.toggleFav(); upd(); } });
    const upd = () => { favBtn.textContent = fav.fav() ? '★ In favorites' : '☆ Add to favorites'; };
    upd();
    const poster = h('img', { src: it.p || '', onerror: (e) => { e.target.style.visibility = 'hidden'; } });
    const m = modal(h('div', { class: 'card detail' },
      h('button', { class: 'x', text: '✕', onclick: () => m.close() }),
      h('div', { class: 'pi' }, poster),
      h('div', { style: { flex: 1, minWidth: 0 } }, h('h2', { dir: 'auto', text: it.n }), body)));
    let info = {}, data = null;
    try {
      if (isSeries) data = await native.json(Urls.api(list) + '&action=get_series_info&series_id=' + it.i);
      else data = await native.json(Urls.api(list) + '&action=get_vod_info&vod_id=' + it.i);
      info = data.info || {};
    } catch (e) { info = {}; }
    if (!document.body.contains(m.el)) return;
    if (info.cover_big || info.movie_image || info.cover) poster.src = info.cover_big || info.movie_image || info.cover;
    const trailer = info.youtube_trailer || '';
    const meta = [info.releasedate || info.releaseDate || '', info.genre || '', info.duration || '', info.rating || it.r ? '★ ' + (info.rating || it.r) : ''].filter(Boolean);
    const acts = h('div', { class: 'acts' });
    const kids = [h('div', { class: 'meta', dir: 'auto', text: meta.join('  •  ') })];
    if (info.cast) kids.push(h('div', { class: 'meta', dir: 'auto', text: 'Cast: ' + info.cast }));
    if (info.director) kids.push(h('div', { class: 'meta', dir: 'auto', text: 'Director: ' + info.director }));
    kids.push(h('div', { class: 'plot', dir: 'auto', text: info.plot || info.description || '' }));
    kids.push(acts);
    body.replaceChildren(...kids);

    const resumeOf = (url) => (Store.state.resume[url] || [0])[0];
    if (!isSeries) {
      const ext = (data && data.movie_data && data.movie_data.container_extension) || it.e || 'mp4';
      const url = Urls.movie(list, it, ext);
      const rs = resumeOf(url);
      acts.append(h('button', { class: 'btn', text: rs > 20 ? '▶ Resume ' + fmtTime(rs) : '▶ Watch Now', onclick: () => { m.close(); playIt({ title: it.n, url }); } }));
      if (rs > 20) acts.append(h('button', { class: 'btn ghost', text: '⏮ From start', onclick: () => { delete Store.state.resume[url]; m.close(); playIt({ title: it.n, url }); } }));
    } else {
      const eps = (data && data.episodes) || {};
      const seasons = Array.isArray(eps) ? { 1: eps } : eps;
      const keys = Object.keys(seasons).sort((a, b) => Number(a) - Number(b));
      const flat = [];
      for (const s of keys) for (const e of seasons[s]) {
        flat.push({ s, e, title: e.title || ('Episode ' + e.episode_num), url: Urls.episode(list, e.id, e.container_extension) });
      }
      const playAt = (idx) => {
        const f = flat[idx]; if (!f) return null;
        return {
          title: it.n + ' — S' + f.s + ' E' + f.e.episode_num + (f.e.title ? ' · ' + f.e.title : ''), url: f.url,
          next: () => playAt(idx + 1)
        };
      };
      if (!keys.length) { acts.append(h('div', { class: 'meta', text: 'No episodes available' })); }
      else {
        let cur = keys[0];
        const seasonBar = h('div', { class: 'seasons' });
        const epList = h('div', { class: 'eps' });
        const draw = () => {
          seasonBar.replaceChildren(...keys.map(k => h('button', { class: 'chip' + (k === cur ? ' on' : ''), text: 'Season ' + k, onclick: () => { cur = k; draw(); } })));
          epList.replaceChildren(...seasons[cur].map(e => {
            const idx = flat.findIndex(f => f.e === e);
            const done = (Store.state.resume[flat[idx].url] || [0, 0])[1] === 0 && Store.state.resume[flat[idx].url];
            const rs = resumeOf(flat[idx].url);
            return h('div', { class: 'ep' + (done ? ' done' : ''), onclick: () => { m.close(); playIt(playAt(idx)); } },
              h('span', { class: 'en', text: (done ? '✓ ' : '') + e.episode_num }),
              h('span', { class: 'et', dir: 'auto', text: e.title || ('Episode ' + e.episode_num) }),
              h('span', { class: 'ed', text: rs > 20 ? 'resume ' + fmtTime(rs) : ((e.info && e.info.duration) || '') }));
          }));
        };
        // continue = first episode with saved progress, otherwise the first one
        let startIdx = 0;
        for (let i = 0; i < flat.length; i++) if (resumeOf(flat[i].url) > 20) { startIdx = i; break; }
        const sf = flat[startIdx];
        acts.append(h('button', { class: 'btn', text: startIdx > 0 || resumeOf(sf.url) > 20 ? `▶ Continue S${sf.s} E${sf.e.episode_num}` : '▶ Watch Now', onclick: () => { m.close(); playIt(playAt(startIdx)); } }));
        body.append(seasonBar, epList);
        draw();
      }
    }
    if (trailer) acts.append(h('button', { class: 'btn burg', text: '🎬 Trailer', onclick: () => native.openExternal(/^https?:/.test(trailer) ? trailer : 'https://www.youtube.com/watch?v=' + trailer) }));
    acts.append(favBtn);
  }
};

/* ---------------- Home ---------------- */
function createHome() {
  const root = h('div', { class: 'view home hidden' });
  const exp = h('div', { class: 'exp', text: '' });
  const tile = (ic, name, go) => h('button', { class: 'tile', onclick: go }, h('span', { class: 'ic', text: ic }), h('span', { text: name }));
  const grid = h('div', { class: 'grid' },
    h('div', { class: 'tiles' },
      h('button', { class: 'tile big', onclick: () => App.show('live') }, h('span', { class: 'ic', text: '📺' }), h('span', { text: 'Live' })),
      h('button', { class: 'tile small', onclick: () => App.show('movies') }, h('span', { class: 'ic', text: '🎬' }), h('span', { text: 'Movies' })),
      h('button', { class: 'tile small', onclick: () => App.show('series') }, h('span', { class: 'ic', text: '🎞️' }), h('span', { text: 'Series' })),
      h('button', { class: 'tile small', onclick: () => App.show('account') }, h('span', { class: 'ic', text: '👤' }), h('span', { text: 'Account' })),
      h('button', { class: 'tile small', onclick: () => App.show('playlists') }, h('span', { class: 'ic', text: '☰' }), h('span', { text: 'Change Playlist' }))),
    h('div', { class: 'sidebtns' },
      h('button', { class: 'sidebtn', text: '⚙  Settings', onclick: () => App.show('settings') }),
      h('button', { class: 'sidebtn', text: '⟳  Reload', onclick: async () => { const l = Store.list(); if (!l) return toast('Add a playlist first'); toast('Downloading the lists again...'); await Content.reload(l); for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate(); Preload.run(); } }),
      h('button', { class: 'sidebtn', text: '⏻  Exit', onclick: () => native.quit() })));
  const ver = h('div', { class: 'ver' });
  root.append(h('div', { class: 'brand' }, 'Joy', h('b', { text: 'Player' }), ' IPTV Pro'), exp, grid, ver);
  native.version().then(v => { ver.textContent = 'v' + v; });
  async function refresh() {
    const l = Store.list();
    if (!l) { exp.textContent = 'No playlist yet — open "Change Playlist" to add one'; return; }
    exp.textContent = l.name;
    try {
      const a = await Account.info(l);
      if (a && a.user_info) {
        const ex = a.user_info.exp_date ? new Date(Number(a.user_info.exp_date) * 1000).toLocaleDateString() : 'Unlimited';
        exp.textContent = l.name + '  •  Expires: ' + ex;
      }
    } catch (e) { }
  }
  return { el: root, show() { root.classList.remove('hidden'); refresh(); }, hide() { root.classList.add('hidden'); }, onKey() { return false; } };
}

/* ---------------- Account ---------------- */
function createAccount() {
  const root = h('div', { class: 'view hidden' });
  const page = h('div', { class: 'page' });
  root.append(page);
  async function draw() {
    const l = Store.list();
    page.replaceChildren(h('h2', { text: 'Account' }));
    if (!l) { page.append(h('div', { class: 'empty', text: 'No playlist selected.' })); return; }
    const kv = h('div', { class: 'kv' }, h('div', { text: 'Playlist' }), h('div', { text: l.name }), h('div', { text: 'Type' }), h('div', { text: l.type === 'm3u' ? 'M3U link' : 'Xtream Codes' }));
    page.append(kv);
    if (l.type !== 'xtream') return;
    const sp = h('div', { class: 'empty', text: 'Checking the account...' });
    page.append(sp);
    try {
      const a = await Account.info(l);
      sp.remove();
      const u = a.user_info || {}, s = a.server_info || {};
      const d = (t) => t ? new Date(Number(t) * 1000).toLocaleString() : '—';
      const add = (k, v) => kv.append(h('div', { text: k }), h('div', { text: String(v == null ? '—' : v) }));
      add('Status', u.status); add('Created', d(u.created_at)); add('Expires', u.exp_date ? d(u.exp_date) : 'Unlimited');
      add('Active connections', u.active_cons); add('Max connections', u.max_connections); add('Trial', u.is_trial === '1' ? 'Yes' : 'No');
      add('Server', s.url); add('Server time', s.time_now);
    } catch (e) { sp.textContent = 'Could not read the account: ' + e.message; }
  }
  return { el: root, show() { root.classList.remove('hidden'); draw(); }, hide() { root.classList.add('hidden'); }, onKey() { return false; } };
}

/* ---------------- Playlists ---------------- */
function createPlaylists() {
  const root = h('div', { class: 'view hidden' });
  const page = h('div', { class: 'page' });
  root.append(page);
  function form(existing) {
    const l = existing || { type: 'xtream' };
    const f = {
      name: h('input', { value: l.name || '', placeholder: 'My IPTV' }),
      type: h('select', {}, h('option', { value: 'xtream', text: 'Xtream Codes (server + user + password)' }), h('option', { value: 'm3u', text: 'M3U link' })),
      server: h('input', { value: l.server || '', placeholder: 'http://server.com:8080' }),
      user: h('input', { value: l.user || '' }),
      pass: h('input', { value: l.pass || '' }),
      url: h('input', { value: l.url || '', placeholder: 'http://.../get.php?username=..&password=..&type=m3u_plus' })
    };
    f.type.value = l.type || 'xtream';
    const xt = h('div', {}, field('Server address', f.server), field('Username', f.user), field('Password', f.pass));
    const mu = h('div', {}, field('M3U link', f.url));
    const sw = () => { xt.classList.toggle('hidden', f.type.value !== 'xtream'); mu.classList.toggle('hidden', f.type.value !== 'm3u'); };
    f.type.addEventListener('change', sw); sw();
    const m = modal(h('div', { class: 'card form' }, h('h3', { text: existing ? 'Edit playlist' : 'Add playlist' }),
      field('Name', f.name), field('Type', f.type), xt, mu,
      h('div', { class: 'acts' }, h('button', { class: 'btn ghost', text: 'Cancel', onclick: () => m.close() }),
        h('button', { class: 'btn', text: 'Save', onclick: async () => {
          const o = { id: existing ? existing.id : 'p' + Date.now().toString(36), name: f.name.value.trim() || 'My IPTV', type: f.type.value };
          // a get.php?username=..&password=.. link (pasted in either box) is an Xtream account
          const lk = parseXtreamLink(o.type === 'm3u' ? f.url.value : f.server.value);
          if (lk) { o.type = 'xtream'; f.server.value = lk.server; f.user.value = lk.user; f.pass.value = lk.pass; }
          if (o.type === 'xtream') {
            o.server = f.server.value.trim(); o.user = f.user.value.trim(); o.pass = f.pass.value.trim();
            if (!o.server || !o.user) return toast('Enter the server address and the username');
          } else {
            o.url = f.url.value.trim();
            if (!/^https?:\/\//i.test(o.url)) return toast('Enter a valid http(s) link');
          }
          if (existing) { Object.assign(existing, o); await Content.reload(existing); }
          else Store.state.lists.push(o);
          Store.state.active = o.id; Store.save(); m.close();
          for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate();
          toast('Saved'); draw(); Preload.run();
        } }))));
    setTimeout(() => f.name.focus(), 50);
  }
  function field(label, input) { return h('div', { class: 'field' }, h('label', { text: label }), input); }
  function move(l, d) {
    const a = Store.state.lists, i = a.indexOf(l), j = i + d;
    if (i < 0 || j < 0 || j >= a.length) return;
    [a[i], a[j]] = [a[j], a[i]]; Store.save(); draw();
  }
  function draw() {
    page.replaceChildren(h('h2', { text: 'Playlists' }));
    const grid = h('div', { class: 'plist' });
    for (const l of Store.state.lists) {
      grid.append(h('div', { class: 'pl' + (l.id === Store.state.active ? ' act' : '') },
        h('div', { class: 'n', dir: 'auto', text: l.name }),
        h('div', { class: 'u', text: l.type === 'm3u' ? l.url : baseOf(l) + '  •  ' + l.user }),
        h('div', { class: 'r' },
          h('button', { class: 'btn small', text: l.id === Store.state.active ? '✓ In use' : 'Use', onclick: () => {
            Store.state.active = l.id; Store.save();
            for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate();
            toast('Playlist: ' + l.name); draw(); Preload.run();
          } }),
          h('button', { class: 'btn small ghost', text: '▲', title: 'Move up', onclick: () => move(l, -1) }),
          h('button', { class: 'btn small ghost', text: '▼', title: 'Move down', onclick: () => move(l, 1) }),
          h('button', { class: 'btn small ghost', text: 'Edit', onclick: () => form(l) }),
          h('button', { class: 'btn small burg', text: 'Delete', onclick: async () => {
            if (!confirm('Delete "' + l.name + '"?')) return;
            Store.state.lists = Store.state.lists.filter(x => x.id !== l.id);
            if (Store.state.active === l.id) Store.state.active = Store.state.lists[0] ? Store.state.lists[0].id : null;
            await Content.reload(l); Store.save();
            for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate();
            draw();
          } }))));
    }
    page.append(grid);
    page.append(h('div', { style: { marginTop: '16px' } }, h('button', { class: 'btn', text: '＋ Add playlist', onclick: () => form(null) })));
    if (!Store.state.lists.length) page.append(h('div', { class: 'empty', text: 'Add your Xtream Codes account or an M3U link to start.' }));
  }
  return { el: root, show() { root.classList.remove('hidden'); draw(); }, hide() { root.classList.add('hidden'); }, onKey() { return false; }, add: () => form(null) };
}

/* ---------------- Settings ---------------- */
function createSettings() {
  const root = h('div', { class: 'view hidden' });
  const page = h('div', { class: 'page' });
  root.append(page);
  function draw() {
    const S = Store.state.settings;
    const fmt = h('select', {}, h('option', { value: 'auto', text: 'Automatic (HLS, then TS)' }), h('option', { value: 'hls', text: 'HLS (m3u8)' }), h('option', { value: 'ts', text: 'MPEG-TS (.ts)' }));
    fmt.value = S.liveFmt || 'auto';
    fmt.addEventListener('change', () => { S.liveFmt = fmt.value; Store.save(); toast('Saved'); });
    const dns = h('select', {}, h('option', { value: 0, text: 'Off (system DNS)' }), h('option', { value: 1, text: 'Cloudflare' }), h('option', { value: 2, text: 'Google' }), h('option', { value: 3, text: 'Quad9' }));
    native.dohGet().then(v => { dns.value = String(v); });
    dns.addEventListener('change', async () => { await native.dohSet(Number(dns.value)); toast('Secure DNS saved'); });
    page.replaceChildren(h('h2', { text: 'Settings' }),
      row('Live stream format', 'If channels do not start, try the other format.', fmt),
      row('Secure DNS (DoH)', 'Helps when your provider or router blocks the IPTV server name. If the server still does not open, try another one.', dns),
      row('Subtitle size', 'For subtitle files loaded in a movie (also A− / A+ in the Subtitles window).', (() => {
        const sz = h('select', {}, [20, 24, 28, 34, 40, 48].map(n => h('option', { value: n, text: String(n) })));
        sz.value = String(S.subSize || 28);
        sz.addEventListener('change', () => { S.subSize = Number(sz.value); Store.save(); Subs.applyCss(); toast('Saved'); });
        return sz;
      })()),
      row('Updates', 'Looks for a newer Windows build and installs it.', h('button', { class: 'btn', text: '⬆ Check now', onclick: () => Updater.check(false) })),
      row('Error Log', 'What failed and what the server answered (passwords are hidden). Send it when something does not play.', h('button', { class: 'btn ghost', text: '🪲 Open', onclick: () => openLog() })),
      row('Backup & Restore', 'Saves your playlists, favorites and progress to a file (it contains your account passwords: keep it private).', h('div', { style: { display: 'flex', gap: '8px' } },
        h('button', { class: 'btn ghost', text: '💾 Save', onclick: () => backupSave() }),
        h('button', { class: 'btn ghost', text: '📂 Restore', onclick: () => backupRestore(draw) }))),
      row('Full screen', 'F11 also works.', h('button', { class: 'btn', text: '⛶ Toggle', onclick: () => native.isFullscreen().then(f => native.fullscreen(!f)) })),
      row('Clear saved lists', 'Deletes the saved copy of the channel / movie / series lists (they are downloaded again).', h('button', { class: 'btn burg', text: 'Clear & reload', onclick: async () => { const l = Store.list(); if (l) { await Content.reload(l); for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate(); toast('Cleared'); Preload.run(); } } })),
      h('div', { class: 'setrow' }, h('div', {}, h('div', { text: 'Keyboard' }),
        h('div', { class: 'd', text: 'Live: ↑/↓ change channel, Enter full screen · Full screen: ←/→ change channel, ↑/↓ volume, Space pause, M mute, Z picture mode, S sleep timer, Esc exit · Movies: ←/→ seek 10 s (Shift 60 s), [ ] speed, N next episode, Z picture mode, S sleep timer · / search' }))));
  }
  function row(t, d, ctrl) { return h('div', { class: 'setrow' }, h('div', {}, h('div', { text: t }), h('div', { class: 'd', text: d })), ctrl); }
  return { el: root, show() { root.classList.remove('hidden'); draw(); }, hide() { root.classList.add('hidden'); }, onKey() { return false; } };
}

/* ---------------- Error log / backup / updates ---------------- */
function openLog() {
  const box = h('div', { class: 'logbox', text: AppLog.text() });
  const m = modal(h('div', { class: 'card form' }, h('h3', { text: 'Error Log' }), box,
    h('div', { class: 'acts' },
      h('button', { class: 'btn ghost', text: 'Close', onclick: () => m.close() }),
      h('button', { class: 'btn burg', text: 'Clear', onclick: () => { AppLog.clear(); box.textContent = AppLog.text(); } }),
      h('button', { class: 'btn', text: 'Copy', onclick: () => { navigator.clipboard.writeText(AppLog.text()).then(() => toast('Copied')); } }))));
}
async function backupSave() {
  try {
    const r = await native.backupExport(JSON.stringify({ app: 'joyplayer-windows', v: 1, state: Store.state }));
    if (r) toast('Backup saved');
  } catch (e) { toast('Could not save the backup'); AppLog.add('backup', e.message || e); }
}
async function backupRestore(redraw) {
  try {
    const t = await native.backupImport();
    if (!t) return;
    const o = JSON.parse(t);
    if (!o || !o.state || !Array.isArray(o.state.lists)) { toast('This is not a JoyPlayer backup'); return; }
    if (!confirm('Replace the current playlists, favorites and progress with the backup?')) return;
    Object.assign(Store.state, o.state);
    Store.state.settings = Object.assign({ liveFmt: 'auto' }, o.state.settings);
    Store.state.settings.sort = Object.assign({ live: 0, movies: 1, series: 1 }, (o.state.settings || {}).sort);
    Store.save();
    for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate();
    toast('Backup restored'); if (redraw) redraw(); Preload.run();
  } catch (e) { toast('Could not read the backup'); AppLog.add('backup', e.message || e); }
}
const Updater = {
  async check(silent) {
    try {
      const r = await native.updateCheck();
      if (!r.newer) { if (!silent) toast(r.cur ? 'You have the newest version (build ' + r.cur + ')' : 'Update check works in the installed app only'); return; }
      const m = modal(h('div', { class: 'card form' }, h('h3', { text: 'New version available' }),
        h('div', { text: 'Build ' + r.n + ' is ready (you have build ' + r.cur + ').' }),
        h('div', { class: 'acts' },
          h('button', { class: 'btn ghost', text: 'Later', onclick: () => m.close() }),
          h('button', { class: 'btn', text: r.url ? 'Update now' : 'Open download page', onclick: async () => {
            if (!r.url) { native.openExternal(r.page); m.close(); return; }
            toast('Downloading the update...', 6000);
            try {
              const how = await native.updateInstall(r.url, r.name, r.portable);
              toast(how === 'folder' ? 'Downloaded: close this window and run the new file' : 'Starting the installer...', 5000);
              m.close();
            } catch (e) { AppLog.add('update', e.message || e); toast('Download failed: opening the page instead'); native.openExternal(r.page); }
          } }))));
    } catch (e) { AppLog.add('update', e.message || e); if (!silent) toast('Could not check for updates'); }
  }
};
