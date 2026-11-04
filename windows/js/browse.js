'use strict';
/** Live / Movies / Series browser: categories | list or poster grid | preview. */
function createBrowse(kind) {
  const isLive = kind === 'live';
  const title = isLive ? 'Live' : kind === 'movies' ? 'Movies' : 'Series';
  const S = { cat: '*all*', q: '', sel: null, data: null, shown: [], favSet: new Set(), favCats: new Set(), list: null, loadedFor: null, theater: false, favCount: 0 };
  const engine = isLive ? new Engine(h('video', { autoplay: true })) : null;

  const root = h('div', { class: 'view hidden' });
  const search = h('input', { type: 'search', placeholder: 'Search in the open category...' });
  const sortOpts = isLive ? ['Default order', 'A → Z', 'Z → A'] : ['Default order', 'Recently added', 'A → Z', 'Z → A', 'Top rated'];
  const sortSel = h('select', { title: 'Sort' }, sortOpts.map((t, i) => h('option', { value: i, text: t })));
  sortSel.value = String(Store.state.settings.sort[kind] || 0);
  const countLbl = h('span', { class: 'info', style: { color: 'var(--muted)', marginLeft: '6px' } });
  const toolbar = h('div', { class: 'toolbar' },
    h('b', { text: title, style: { marginRight: '8px', color: 'var(--gold)' } }),
    search, sortSel,
    h('button', { class: 'btn ghost', text: '✕', title: 'Clear search', onclick: () => { search.value = ''; setQuery(''); } }),
    countLbl, h('span', { style: { flex: 1 } }),
    h('button', { class: 'btn ghost', text: '⟳ Reload', title: 'Download this list again', onclick: () => reloadList() })
  );
  const catsEl = h('div', { class: 'cats' });
  const itemsEl = h('div', { class: isLive ? 'listcol' : 'pgrid' });
  const cols = h('div', { class: 'cols' }, catsEl, itemsEl);
  root.append(toolbar, cols);

  // ----- categories
  let catRows = [];
  const catList = new VList(catsEl, 38, (i) => {
    const r = catRows[i]; if (!r) return null;
    const star = r.k.startsWith('*') ? null : h('span', {
      class: 'st' + (S.favCats.has(r.k) ? ' on' : ''), text: '★', title: 'Favorite category',
      onclick: (e) => { e.stopPropagation(); toggleFavCat(r.k); }
    });
    return h('div', { class: 'catrow' + (S.cat === r.k ? ' on' : ''), onclick: () => pickCat(r.k) },
      h('span', { class: 'nm', dir: 'auto', text: r.name }), h('span', { class: 'ct', text: r.count }), star);
  });
  function buildCats() {
    const d = S.data;
    const blk = Store.user(S.list).hide[kind].length || Store.user(S.list).lock[kind].length ? Parental.blocked(S.list, kind) : null;
    catRows = [{ k: '*all*', name: 'All', count: blk && blk.size ? d.items.reduce((n, it) => n + (blk.has(it.g) ? 0 : 1), 0) : d.items.length }, { k: '*fav*', name: 'Favorite', count: S.favCount }];
    catRows.push({ k: '*rec*', name: 'Recently watched', count: recIds().length });
    const hid = new Set(Store.user(S.list).hide[kind]);
    let cats = d.cats.filter(c => !hid.has(c.name));
    const m = Store.state.settings.sort[kind] || 0;
    const az = isLive ? m === 1 : m === 2, za = isLive ? m === 2 : m === 3;
    if (az || za) { cats.forEach(c => c._k = c._k || sortKey(c.name)); cats.sort((a, b) => (za ? -1 : 1) * collator.compare(a._k, b._k)); }
    cats = cats.filter(c => S.favCats.has(c.name)).concat(cats.filter(c => !S.favCats.has(c.name)));   // favorite categories first
    for (const c of cats) catRows.push({ k: c.name, name: (Parental.isLocked(S.list, kind, c.name) ? '🔒 ' : '') + c.name, count: c.count });
    catList.setCount(catRows.length, true);
  }
  const recIds = () => isLive ? Store.user(S.list).recent : (Store.user(S.list).recentVod[kind] || []);
  function computeFavCount() {
    S.favCount = S.data.items.reduce((n, it) => n + ((S.favSet.has(it.i) || S.favCats.has(it.g)) ? 1 : 0), 0);
  }
  function pickCat(k) {
    if (!k.startsWith('*') && Parental.isLocked(S.list, kind, k)) {
      Parental.askPin(() => { Parental.unlocked.add(Parental.key(S.list, kind, k)); buildCats(); pickCat(k); });
      return;
    }
    S.cat = k; S.q = ''; search.value = '';
    refreshShown(); catList.refresh(true);
  }
  function toggleFavCat(name) {
    if (S.favCats.has(name)) S.favCats.delete(name); else S.favCats.add(name);
    const u = Store.user(S.list); u.favCats[kind] = [...S.favCats]; Store.save();
    toast(S.favCats.has(name) ? 'Category added to favorites' : 'Category removed from favorites');
    computeFavCount(); buildCats(); if (S.cat === '*fav*') refreshShown();
  }

  // ----- items
  function baseItems() {
    const d = S.data;
    const bl = Store.user(S.list).hide[kind].length || Store.user(S.list).lock[kind].length ? Parental.blocked(S.list, kind) : null;
    const ok = (it) => !bl || !bl.has(it.g);
    if (S.cat === '*all*') return bl && bl.size ? d.items.filter(ok) : d.items;
    if (S.cat === '*fav*') return d.items.filter(it => ok(it) && (S.favSet.has(it.i) || S.favCats.has(it.g)));
    if (S.cat === '*rec*') {
      const map = new Map(d.items.map(i => [String(i.i), i]));
      return recIds().map(id => map.get(String(id))).filter(Boolean).filter(ok);
    }
    return d.byCat.get(S.cat) || [];
  }
  function sortItems(arr) {
    const m = Store.state.settings.sort[kind] || 0;
    if (m === 0) return arr;
    const key = (it) => it._k || (it._k = sortKey(it.n));
    const a = arr.slice();
    if (isLive) return a.sort((x, y) => (m === 2 ? -1 : 1) * collator.compare(key(x), key(y)));
    if (m === 1) return a.sort((x, y) => y.a - x.a);
    if (m === 2) return a.sort((x, y) => collator.compare(key(x), key(y)));
    if (m === 3) return a.sort((x, y) => collator.compare(key(y), key(x)));
    return a.sort((x, y) => y.r - x.r);
  }
  function refreshShown(keepScroll) {
    if (!S.data) return;
    let arr = baseItems();
    const words = norm(S.q).split(' ').filter(Boolean);
    if (words.length) { Content.ensureIndex(S.data); arr = arr.filter(it => words.every(w => it._s.includes(w))); }
    S.shown = sortItems(arr);
    countLbl.textContent = trf('{0} items', S.shown.length.toLocaleString());
    updateItemsView(keepScroll);
  }
  let itemList = null, gridCols = 1;
  function posterCols() { return Math.max(1, Math.floor(((itemsEl.clientWidth || 800) - 14) / 164)); }
  function updateItemsView(keepScroll) {
    if (!itemList) return;
    if (!S.shown.length) emptyMsg.classList.remove('hidden'); else emptyMsg.classList.add('hidden');
    if (isLive) itemList.setCount(S.shown.length, keepScroll);
    else { gridCols = posterCols(); itemList.setCount(Math.ceil(S.shown.length / gridCols), keepScroll); }
  }
  const emptyMsg = h('div', { class: 'empty hidden', text: 'Nothing here', style: { position: 'absolute', left: 0, right: 0, top: '70px', pointerEvents: 'none' } });

  function liveRow(i) {
    const it = S.shown[i]; if (!it) return null;
    const isSel = S.sel && S.sel.i === it.i;
    return h('div', { class: 'chrow' + (isSel ? ' sel' : ''), onclick: () => rowClick(it), ondblclick: () => { if (S.sel === it || (S.sel && S.sel.i === it.i)) enterTheater(); } },
      h('span', { class: 'num', text: i + 1 }),
      it.l ? h('img', { src: it.l, loading: 'lazy', onerror: (e) => { e.target.style.visibility = 'hidden'; } }) : h('span', { style: { width: '40px' } }),
      h('span', { class: 'nm', dir: 'auto', text: it.n }),
      it.d > 0 ? h('span', { class: 'arch', text: '⏪', title: trf('Catch-up: {0} days', it.d) }) : null,
      h('span', { class: 'fv' + (S.favSet.has(it.i) ? ' on' : ''), text: '★', title: 'Favorite', onclick: (e) => { e.stopPropagation(); toggleFav(it); } }));
  }
  function posterRow(r) {
    const row = h('div', { class: 'prow' });
    for (let c = 0; c < gridCols; c++) {
      const it = S.shown[r * gridCols + c]; if (!it) break;
      row.append(h('div', { class: 'poster', onclick: () => Detail.open(kind, it, { fav: () => S.favSet.has(it.i), toggleFav: () => toggleFav(it) }) },
        h('div', { class: 'im', style: it.p ? { backgroundImage: `url("${it.p.replace(/"/g, '%22')}")` } : {} },
          S.favSet.has(it.i) ? h('span', { class: 'fv', text: '★' }) : null,
          (!isLive && kind === 'movies' && (Store.state.resume[Urls.movie(S.list, it)] || [])[1] === 0) ? h('span', { class: 'wt', text: '✓', title: 'Watched' }) : null,
          it.r > 0 ? h('span', { class: 'rt', text: '★ ' + (it.r > 5 ? (it.r / 2).toFixed(1) : it.r.toFixed(1)) }) : null),
        h('div', { class: 'nm', dir: 'auto', text: it.n })));
    }
    return row;
  }
  function toggleFav(it) {
    if (S.favSet.has(it.i)) S.favSet.delete(it.i); else S.favSet.add(it.i);
    const u = Store.user(S.list); u.fav[kind] = [...S.favSet]; Store.save();
    toast(S.favSet.has(it.i) ? 'Added to favorites' : 'Removed from favorites');
    computeFavCount(); buildCats();
    if (S.cat === '*fav*') refreshShown(true); else itemList.refresh(true);
    if (isLive) pfav.className = 'btn ghost' + (S.sel && S.favSet.has(S.sel.i) ? ' burg' : '');
  }
  function setQuery(q) { S.q = q; refreshShown(); }
  search.addEventListener('input', debounce(() => setQuery(search.value), 160));
  sortSel.addEventListener('change', () => {
    Store.state.settings.sort[kind] = Number(sortSel.value); Store.save();
    if (S.data) { buildCats(); refreshShown(); }
  });

  // ----- live preview
  let video, stage, msgEl, topEl, ptitle, pfav, epgBox, idleTimer = 0, selTimer = 0, epgTimer = 0;
  if (isLive) {
    video = engine.v; video.volume = Number(localStorage.getItem('vol') || 1);
    msgEl = h('div', { class: 'msg', text: 'Select a channel' });
    topEl = h('div', { class: 'top' });
    const liveMedia = () => { if (!S.sel) { toast('Select a channel first'); return null; } const u = Urls.live(S.list, S.sel); return { url: u.m3u8 || u.direct, title: S.sel.n, live: true, at: 0 }; };
    const castLive = () => CastUI.pick(liveMedia, { onStart: () => { video.pause(); }, onStop: () => { if (S.sel) playSel(); } });
    const extLive = () => { const m = liveMedia(); if (m) { video.pause(); CastUI.external(() => m); } };
    const lPlay = h('button', { class: 'cb big', text: '⏸', title: 'Play / Pause (Space)', onclick: () => { video.paused ? video.play().catch(() => { }) : video.pause(); } });
    const lMute = h('button', { class: 'cb', text: '🔊', title: 'Mute (M)', onclick: () => { video.muted = !video.muted; } });
    const lVol = h('input', { type: 'range', class: 'vol', min: 0, max: 100, value: Math.round(video.volume * 100), title: 'Volume' });
    lVol.addEventListener('input', () => { video.muted = false; video.volume = Number(lVol.value) / 100; localStorage.setItem('vol', String(video.volume)); });
    video.addEventListener('volumechange', () => { lVol.value = String(Math.round((video.muted ? 0 : video.volume) * 100)); lMute.textContent = video.muted || video.volume === 0 ? '🔇' : (video.volume < 0.5 ? '🔉' : '🔊'); });
    video.addEventListener('play', () => { lPlay.textContent = '⏸'; });
    video.addEventListener('pause', () => { lPlay.textContent = '▶'; });
    const pPlay = h('button', { class: 'btn', text: '⏸', title: 'Play / Pause (Space)', onclick: () => { video.paused ? video.play().catch(() => { }) : video.pause(); } });
    video.addEventListener('play', () => { pPlay.textContent = '⏸'; });
    video.addEventListener('pause', () => { pPlay.textContent = '▶'; });
    const lbar = h('div', { class: 'lbar' },
      h('button', { class: 'cb', text: '⏮', title: 'Previous channel (←)', onclick: () => zap(-1) }),
      lPlay,
      h('button', { class: 'cb', text: '⏭', title: 'Next channel (→)', onclick: () => zap(1) }),
      h('div', { class: 'sp' }), lMute, lVol,
      h('button', { class: 'cb', text: '🔊 Audio', onclick: () => VodPlayer.audioMenu(video) }),
      h('button', { class: 'cb', text: '⤢', title: 'Picture: Fit / Zoom / Stretch (Z)', onclick: () => Fit.cycle() }),
      h('button', { class: 'cb', text: '😴', title: 'Sleep timer (S)', onclick: () => Sleep.cycle() }),
      h('button', { class: 'cb', text: '📡', title: 'Cast to TV', onclick: castLive }),
      h('button', { class: 'cb', text: '🖥', title: 'Open in VLC / another player', onclick: extLive }),
      h('button', { class: 'cb', text: '✕', title: 'Exit full screen (Esc)', onclick: () => exitTheater() }));
    stage = h('div', { class: 'stage' }, video, msgEl, topEl, lbar);
    ptitle = h('div', { class: 't', dir: 'auto', text: '' });
    pfav = h('button', { class: 'btn ghost', text: '★ Favorite', onclick: () => { if (S.sel) toggleFav(S.sel); } });
    epgBox = h('div', { class: 'epgbox' });
    const pane = h('div', { class: 'live-pane' }, stage,
      h('div', { class: 'pinfo' },
        h('button', { class: 'btn ghost', text: '⏮', title: 'Previous channel', onclick: () => zap(-1) }),
        pPlay,
        h('button', { class: 'btn ghost', text: '⏭', title: 'Next channel', onclick: () => zap(1) }),
        ptitle, pfav,
        h('button', { class: 'btn ghost', text: '📅 Guide', title: 'Program guide and catch-up', onclick: () => openGuide() }),
        h('button', { class: 'btn ghost', text: '📡', title: 'Cast to TV', onclick: castLive }),
        h('button', { class: 'btn ghost', text: '🖥', title: 'Open in VLC / another player', onclick: extLive }),
        h('button', { class: 'btn', text: '⛶ Full screen', onclick: () => enterTheater() })),
      epgBox);
    cols.append(pane);
    stage.addEventListener('dblclick', () => { if (S.theater) exitTheater(); else enterTheater(); });
    stage.addEventListener('mousemove', wake);
    document.addEventListener('mousemove', () => { if (S.theater) wake(); });
    document.addEventListener('mousedown', () => { if (S.theater) wake(); });
    stage.addEventListener('wheel', (e) => { if (S.theater) { vol(e.deltaY < 0 ? 0.05 : -0.05); } }, { passive: true });
    video.addEventListener('click', () => { if (S.theater) { video.paused ? video.play() : video.pause(); } });
  }
  function wake() {
    if (!stage) return;
    stage.classList.remove('idle'); clearTimeout(idleTimer);
    if (S.theater) idleTimer = setTimeout(() => { if (video.paused) wake(); else stage.classList.add('idle'); }, 4000);
  }
  function vol(d) {
    video.muted = false; video.volume = Math.min(1, Math.max(0, video.volume + d));
    localStorage.setItem('vol', String(video.volume)); toast(trf('Volume {0}%', Math.round(video.volume * 100)), 900);
  }
  function playSel() {
    const it = S.sel; if (!it || !isLive) return;
    ptitle.textContent = it.n; topEl.textContent = it.n;
    pfav.className = 'btn ghost' + (S.favSet.has(it.i) ? ' burg' : '');
    msgEl.textContent = tr('Connecting...'); msgEl.classList.remove('hidden');
    const u = Store.user(S.list);
    u.recent = [it.i, ...u.recent.filter(x => x !== it.i)].slice(0, 60); Store.save();
    paintEpg();
    engine.live(Urls.live(S.list, it), {
      playing: () => msgEl.classList.add('hidden'),
      status: (m) => { msgEl.textContent = tr(m); msgEl.classList.remove('hidden'); },
      error: (m) => { msgEl.textContent = m; msgEl.classList.remove('hidden'); AppLog.add('live', it.n + ' | ' + m); }
    });
  }
  async function paintEpg() {
    const it = S.sel; if (!it || !epgBox) return;
    const t = await Epg.paint(epgBox, S.list, it, () => S.sel === it);
    if (t && S.sel === it) topEl.textContent = it.n + '  —  ' + t;
  }
  function openGuide() {
    const it = S.sel; if (!it) { toast('Select a channel first'); return; }
    if (!Epg.hasGuide(S.list)) { toast('This playlist has no program guide link (add one when editing the playlist)'); return; }
    Epg.guide(S.list, it, {
      catchup: (p) => {
        const url = Epg.catchupUrl(S.list, it, p);
        clearTimeout(selTimer); engine.stop();                // the live channel must release its connection first
        msgEl.textContent = tr('Catch-up playing'); msgEl.classList.remove('hidden');
        VodPlayer.open({ title: it.n + ' — ' + p.title + ' (' + Epg.hm(p.start) + ')', url, stream: true, onClose: () => { if (S.sel) playSel(); } });
      }
    });
  }
  function select(it, scroll) {
    S.sel = it; itemList.refresh(true);
    if (scroll) { const i = S.shown.findIndex(x => x.i === it.i); if (i >= 0) itemList.ensureVisible(i); }
    clearTimeout(selTimer); selTimer = setTimeout(playSel, 250);
  }
  function rowClick(it) {
    if (S.sel && S.sel.i === it.i) { enterTheater(); return; }
    if (S.q) {            // picked from a search: open its whole category with the channel selected
      S.cat = it.g; S.q = ''; search.value = '';
      refreshShown(); catList.refresh(true);
      const i = S.shown.findIndex(x => x.i === it.i);
      S.sel = it; itemList.refresh(true); if (i >= 0) itemList.scrollTo(i);
      const ci = catRows.findIndex(r => r.k === it.g); if (ci >= 0) catList.ensureVisible(ci);
      clearTimeout(selTimer); selTimer = setTimeout(playSel, 150);
      return;
    }
    select(it, false);
  }
  function enterTheater() {
    if (!S.sel) return;
    S.theater = true; stage.classList.add('theater'); native.fullscreen(true); wake();
    video.focus && video.blur();
  }
  function exitTheater() {
    S.theater = false; stage.classList.remove('theater', 'idle'); clearTimeout(idleTimer); native.fullscreen(false);
  }
  function zap(d) {
    if (!S.shown.length) return;
    let i = S.sel ? S.shown.findIndex(x => x.i === S.sel.i) : -1;
    i = ((i + d) % S.shown.length + S.shown.length) % S.shown.length;
    const it = S.shown[i];
    S.sel = it; itemList.refresh(true); itemList.ensureVisible(i);
    toast((i + 1) + '. ' + it.n, 1400);
    clearTimeout(selTimer); selTimer = setTimeout(playSel, 200);
  }

  // ----- loading
  async function reloadList() {
    const list = Store.list(); if (!list) return;
    toast('Downloading the list again...');
    await Content.reload(list);
    for (const v of Object.values(App.views)) if (v.invalidate) v.invalidate();
    App.show(App.current, true);
  }
  const loadingEl = () => spinner('Loading...');
  async function load() {
    const list = Store.list(); S.list = list;
    if (!list) { showEmpty('Add a playlist first'); return; }
    if (!isLive && list.type !== 'xtream') { showEmpty('Movies and Series need an Xtream Codes playlist.'); return; }
    if (S.loadedFor === list.id && S.data) return;
    const sp = loadingEl(); root.append(sp);
    try {
      S.data = await Content.get(list, kind);
      const u = Store.user(list);
      S.favSet = new Set(u.fav[kind]); S.favCats = new Set(u.favCats[kind]);
      S.cat = '*all*'; S.q = ''; search.value = ''; S.sel = null; S.loadedFor = list.id;
      computeFavCount(); build(); buildCats(); refreshShown();
    } catch (e) {
      showEmpty(trf('Could not load the list: {0}', (e && e.message ? e.message.replace(/^Error invoking remote method '[^']+': (Error: )?/, '') : e)));
    } finally { sp.remove(); }
  }
  function showEmpty(text) {
    catsEl.replaceChildren(); itemsEl.replaceChildren(h('div', { class: 'empty', text }));
    itemList = null; S.data = null; S.loadedFor = null;
    built = false;
  }
  let built = false;
  function build() {
    if (built) return;
    built = true;
    catsEl.replaceChildren(catList.sizer);
    itemsEl.replaceChildren();
    if (isLive) {
      itemList = new VList(h('div', { style: { flex: 1, position: 'relative' } }), 48, liveRow);
      itemsEl.append(itemList.el, emptyMsg);
    } else {
      const holder = h('div', { style: { position: 'absolute', inset: 0 } });
      itemsEl.style.position = 'relative'; itemsEl.append(holder, emptyMsg);
      itemList = new VList(holder, 275, posterRow);
      new ResizeObserver(() => { if (S.data && posterCols() !== gridCols) updateItemsView(true); }).observe(itemsEl);
    }
  }

  // ----- keyboard (called by the app for the active view)
  function onKey(e) {
    const tag = (e.target && e.target.tagName) || '';
    if (isLive && S.theater) {
      switch (e.key) {
        case 'Escape': exitTheater(); return true;
        case 'ArrowLeft': case 'PageUp': zap(-1); return true;
        case 'ArrowRight': case 'PageDown': zap(1); return true;
        case 'ArrowUp': vol(0.05); return true;
        case 'ArrowDown': vol(-0.05); return true;
        case ' ': video.paused ? video.play() : video.pause(); return true;
        case 'm': case 'M': video.muted = !video.muted; return true;
        case 'z': case 'Z': Fit.cycle(); return true;
        case 's': case 'S': Sleep.cycle(); return true;
        case 'Enter': exitTheater(); return true;
      }
      return false;
    }
    if (tag === 'INPUT' || tag === 'SELECT') { if (e.key === 'Escape') { e.target.blur(); return true; } return false; }
    if (isLive && S.data) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { zap(e.key === 'ArrowDown' ? 1 : -1); return true; }
      if (e.key === 'Enter') { enterTheater(); return true; }
    }
    if (e.key === '/' || (e.ctrlKey && e.key.toLowerCase() === 'f')) { search.focus(); search.select(); return true; }
    return false;
  }

  return {
    el: root, kind,
    async show() {
      root.classList.remove('hidden');
      if (isLive) { clearInterval(epgTimer); epgTimer = setInterval(() => { if (!S.theater) paintEpg(); }, 60000); }
      await load();
      if (isLive && S.sel && S.data) playSel();
    },
    hide() {
      root.classList.add('hidden');
      if (isLive) { clearInterval(epgTimer); clearTimeout(selTimer); if (S.theater) exitTheater(); engine.stop(); }
    },
    invalidate() { S.loadedFor = null; S.data = null; built = false; },
    onKey,
    inTheater: () => S.theater
  };
}
