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
    catRows = [{ k: '*all*', name: 'All', count: d.items.length }, { k: '*fav*', name: 'Favorite', count: S.favCount }];
    if (isLive) catRows.push({ k: '*rec*', name: 'Recently watched', count: Store.user(S.list).recent.length });
    let cats = d.cats.slice();
    const m = Store.state.settings.sort[kind] || 0;
    const az = isLive ? m === 1 : m === 2, za = isLive ? m === 2 : m === 3;
    if (az || za) { cats.forEach(c => c._k = c._k || sortKey(c.name)); cats.sort((a, b) => (za ? -1 : 1) * collator.compare(a._k, b._k)); }
    cats = cats.filter(c => S.favCats.has(c.name)).concat(cats.filter(c => !S.favCats.has(c.name)));   // favorite categories first
    for (const c of cats) catRows.push({ k: c.name, name: c.name, count: c.count });
    catList.setCount(catRows.length, true);
  }
  function computeFavCount() {
    S.favCount = S.data.items.reduce((n, it) => n + ((S.favSet.has(it.i) || S.favCats.has(it.g)) ? 1 : 0), 0);
  }
  function pickCat(k) {
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
    if (S.cat === '*all*') return d.items;
    if (S.cat === '*fav*') return d.items.filter(it => S.favSet.has(it.i) || S.favCats.has(it.g));
    if (S.cat === '*rec*') {
      const map = new Map(d.items.map(i => [String(i.i), i]));
      return Store.user(S.list).recent.map(id => map.get(String(id))).filter(Boolean);
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
    countLbl.textContent = S.shown.length.toLocaleString() + ' items';
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
      h('span', { class: 'fv' + (S.favSet.has(it.i) ? ' on' : ''), text: '★', title: 'Favorite', onclick: (e) => { e.stopPropagation(); toggleFav(it); } }));
  }
  function posterRow(r) {
    const row = h('div', { class: 'prow' });
    for (let c = 0; c < gridCols; c++) {
      const it = S.shown[r * gridCols + c]; if (!it) break;
      row.append(h('div', { class: 'poster', onclick: () => Detail.open(kind, it, { fav: () => S.favSet.has(it.i), toggleFav: () => toggleFav(it) }) },
        h('div', { class: 'im', style: it.p ? { backgroundImage: `url("${it.p.replace(/"/g, '%22')}")` } : {} },
          S.favSet.has(it.i) ? h('span', { class: 'fv', text: '★' }) : null,
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
  let video, stage, msgEl, topEl, ptitle, pfav, idleTimer = 0, selTimer = 0;
  if (isLive) {
    video = engine.v; video.volume = Number(localStorage.getItem('vol') || 1);
    msgEl = h('div', { class: 'msg', text: 'Select a channel' });
    topEl = h('div', { class: 'top' });
    stage = h('div', { class: 'stage' }, video, msgEl, topEl);
    ptitle = h('div', { class: 't', dir: 'auto', text: '' });
    pfav = h('button', { class: 'btn ghost', text: '★ Favorite', onclick: () => { if (S.sel) toggleFav(S.sel); } });
    const pane = h('div', { class: 'live-pane' }, stage,
      h('div', { class: 'pinfo' }, ptitle, pfav, h('button', { class: 'btn', text: '⛶ Full screen', onclick: () => enterTheater() })));
    cols.append(pane);
    stage.addEventListener('dblclick', () => { if (S.theater) exitTheater(); else enterTheater(); });
    stage.addEventListener('mousemove', wake);
    stage.addEventListener('wheel', (e) => { if (S.theater) { vol(e.deltaY < 0 ? 0.05 : -0.05); } }, { passive: true });
    video.addEventListener('click', () => { if (S.theater) { video.paused ? video.play() : video.pause(); } });
  }
  function wake() {
    if (!stage) return;
    stage.classList.remove('idle'); clearTimeout(idleTimer);
    if (S.theater) idleTimer = setTimeout(() => stage.classList.add('idle'), 3000);
  }
  function vol(d) {
    video.muted = false; video.volume = Math.min(1, Math.max(0, video.volume + d));
    localStorage.setItem('vol', String(video.volume)); toast('Volume ' + Math.round(video.volume * 100) + '%', 900);
  }
  function playSel() {
    const it = S.sel; if (!it || !isLive) return;
    ptitle.textContent = it.n; topEl.textContent = it.n;
    pfav.className = 'btn ghost' + (S.favSet.has(it.i) ? ' burg' : '');
    msgEl.textContent = 'Connecting...'; msgEl.classList.remove('hidden');
    const u = Store.user(S.list);
    u.recent = [it.i, ...u.recent.filter(x => x !== it.i)].slice(0, 60); Store.save();
    engine.live(Urls.live(S.list, it), {
      playing: () => msgEl.classList.add('hidden'),
      status: (m) => { msgEl.textContent = m; msgEl.classList.remove('hidden'); },
      error: (m) => { msgEl.textContent = m; msgEl.classList.remove('hidden'); }
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
  const loadingEl = () => spinner('Loading ' + title.toLowerCase() + '...');
  async function load() {
    const list = Store.list(); S.list = list;
    if (!list) { showEmpty('Add a playlist first (Playlists page).'); return; }
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
      showEmpty('Could not load the list: ' + (e && e.message ? e.message.replace(/^Error invoking remote method '[^']+': (Error: )?/, '') : e));
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
      await load();
      if (isLive && S.sel && S.data) playSel();
    },
    hide() {
      root.classList.add('hidden');
      if (isLive) { clearTimeout(selTimer); if (S.theater) exitTheater(); engine.stop(); }
    },
    invalidate() { S.loadedFor = null; S.data = null; built = false; },
    onKey,
    inTheater: () => S.theater
  };
}
