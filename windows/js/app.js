'use strict';
const Preload = {
  timer: 0,
  /** After the playlist opens, load Live, Movies and Series in the background so the tabs open instantly. */
  run() {
    clearTimeout(this.timer);
    this.timer = setTimeout(async () => {
      const l = Store.list(); if (!l) return;
      try { await Content.get(l, 'live'); } catch (e) { return; }
      if (l.type !== 'xtream') return;
      await new Promise(r => setTimeout(r, 1500));
      try { await Content.get(l, 'movies'); } catch (e) { }
      try { await Content.get(l, 'series'); } catch (e) { }
    }, 800);
  }
};

const App = {
  views: {}, current: 'home',
  show(name, force) {
    if (this.current === name && !force) return;
    const prev = this.views[this.current];
    if (prev) prev.hide();
    this.current = name;
    const v = this.views[name];
    document.querySelectorAll('.navbtn').forEach(b => b.classList.toggle('on', b.dataset.v === name));
    if (v) v.show();
  }
};

(async function boot() {
  const app = $('#app');
  await Store.load();
  const nav = h('div', { id: 'nav' }, h('span', { class: 'logo' }, 'Joy', h('b', { text: 'Player' }), ' IPTV Pro'));
  const items = [['home', 'Home'], ['live', 'Live'], ['movies', 'Movies'], ['series', 'Series'], ['playlists', 'Playlists'], ['settings', 'Settings']];
  for (const [k, t] of items) nav.append(h('button', { class: 'navbtn', 'data-v': k, text: t, onclick: () => App.show(k) }));
  nav.append(h('span', { class: 'sp' }), h('span', { class: 'info', id: 'navinfo' }), h('button', { class: 'navbtn', text: '⛶', title: 'Full screen (F11)', onclick: () => native.isFullscreen().then(f => native.fullscreen(!f)) }));
  const main = h('div', { id: 'main' });
  app.append(nav, main);

  App.views = {
    home: createHome(), live: createBrowse('live'), movies: createBrowse('movies'), series: createBrowse('series'),
    account: createAccount(), playlists: createPlaylists(), settings: createSettings()
  };
  for (const v of Object.values(App.views)) main.append(v.el);

  native.onFullscreen((on) => document.body.classList.toggle('fs', on || (App.views.live && App.views.live.inTheater())));

  document.addEventListener('keydown', (e) => {
    if (e.key === 'F11') { e.preventDefault(); native.isFullscreen().then(f => native.fullscreen(!f)); return; }
    if (VodPlayer.isOpen()) { if (VodPlayer.onKey(e)) e.preventDefault(); return; }
    if (document.querySelector('.modal')) return;
    const v = App.views[App.current];
    if (v && v.onKey && v.onKey(e)) { e.preventDefault(); return; }
    if (e.key === 'Escape' && App.current !== 'home') App.show('home');
  });

  const l = Store.list();
  if (!l) App.show('playlists');
  else { App.show('home'); Preload.run(); }
  $('#navinfo').textContent = l ? l.name : '';
  setInterval(() => { const a = Store.list(); $('#navinfo').textContent = a ? a.name : ''; }, 2000);
})();
