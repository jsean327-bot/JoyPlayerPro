'use strict';
/** Cast to a Chromecast / Google TV, or open the stream in an external player (VLC...). */
const CastUI = (() => {
  let bar = null, timer = 0, dev = null, hooks = null, lastSt = null, seeking = false;
  const mime = (url, live) => {
    const u = String(url).split('?')[0].toLowerCase();
    if (u.endsWith('.m3u8') || live && !/\.(ts|mp4|mkv)$/.test(u)) return 'application/x-mpegURL';
    if (u.endsWith('.mkv')) return 'video/x-matroska';
    if (u.endsWith('.ts')) return 'video/mp2t';
    if (u.endsWith('.avi')) return 'video/x-msvideo';
    return 'video/mp4';
  };
  const mm = (s) => { s = Math.max(0, Math.floor(s || 0)); const hh = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), ss = s % 60; return (hh ? hh + ':' + String(m).padStart(2, '0') : m) + ':' + String(ss).padStart(2, '0'); };
  function stopBar() {
    clearInterval(timer); timer = 0;
    if (bar) { bar.remove(); bar = null; }
    dev = null;
  }
  async function stop() {
    const hk = hooks; stopBar();
    try { await native.castStop(); } catch (e) { }
    if (hk && hk.onStop) hk.onStop();
    hooks = null;
  }
  function drawBar(media) {
    const title = h('div', { class: 't', dir: 'auto', text: tr('Casting to') + ' ' + dev.name });
    const sub = h('div', { class: 'ct', text: media.title || '' });
    const play = h('button', { class: 'btn', text: '⏸', onclick: () => { native.castCtl(lastSt && lastSt.state === 'paused' ? 'play' : 'pause'); } });
    const seek = h('input', { type: 'range', class: 'seek', min: 0, max: 1000, value: 0 });
    const tm = h('span', { class: 'tm', text: '0:00' });
    seek.addEventListener('input', () => { seeking = true; });
    seek.addEventListener('change', () => { if (lastSt && lastSt.duration) native.castCtl('seek', Number(seek.value) / 1000 * lastSt.duration); setTimeout(() => seeking = false, 600); });
    const vol = h('input', { type: 'range', class: 'vol', min: 0, max: 100, value: 50, title: tr('Volume') });
    vol.addEventListener('input', () => native.castCtl('volume', Number(vol.value) / 100));
    bar = h('div', { id: 'castbar' },
      h('div', { class: 'cinfo' }, title, sub),
      media.live ? null : h('button', { class: 'btn ghost', text: '⟲ 10', onclick: () => lastSt && native.castCtl('seek', Math.max(0, lastSt.time - 10)) }),
      play,
      media.live ? null : h('button', { class: 'btn ghost', text: '10 ⟳', onclick: () => lastSt && native.castCtl('seek', lastSt.time + 10) }),
      media.live ? null : seek, media.live ? null : tm,
      h('span', { text: '🔊' }), vol,
      h('button', { class: 'btn burg', text: '⏹ ' + tr('Stop casting'), onclick: () => stop() }));
    document.body.append(bar);
    timer = setInterval(async () => {
      let st; try { st = await native.castStatus(); } catch (e) { return; }
      lastSt = st;
      if (st.state === 'closed') { toast('The connection to the TV was closed'); stop(); return; }
      if (st.error && !st.shown) { toast(st.error, 3500); }
      play.textContent = st.state === 'paused' ? '▶' : '⏸';
      if (!seeking && st.duration) { seek.value = String(Math.round(st.time / st.duration * 1000)); seek.style.setProperty('--p', (st.time / st.duration * 100) + '%'); }
      tm.textContent = mm(st.time) + ' / ' + mm(st.duration);
      if (document.activeElement !== vol) vol.value = String(Math.round((st.volume || 0) * 100));
    }, 1000);
  }
  /** getMedia() -> {url, title, live, at}. hooks: {onStart(), onStop()}. */
  async function pick(getMedia, hk) {
    const media = getMedia(); if (!media || !media.url) return;
    const body = h('div', {}, spinner('Looking for devices on your network...'));
    const m = modal(h('div', { class: 'card form' }, h('h3', { text: '📡 Cast' }), body,
      h('div', { class: 'acts' }, h('button', { class: 'btn ghost', text: 'Close', onclick: () => m.close() }))));
    const show = async () => {
      body.replaceChildren(spinner('Looking for devices on your network...'));
      let list = [];
      try { list = await native.castScan(); } catch (e) { }
      if (!document.body.contains(m.el)) return;
      if (!list.length) {
        body.replaceChildren(h('div', { class: 'empty', text: 'No device found. The PC and the TV must be on the same Wi-Fi / network (a VPN can hide the TV).' }),
          h('button', { class: 'btn', text: '↻ Search again', onclick: show }));
        return;
      }
      body.replaceChildren(...list.map(d => h('button', {
        class: 'btn ghost', style: { display: 'block', width: '100%', margin: '6px 0', textAlign: 'left' }, text: '📺 ' + d.name + (d.model ? '  (' + d.model + ')' : ''),
        onclick: async () => {
          body.replaceChildren(spinner('Connecting...'));
          const fresh = getMedia() || media;
          const r = await native.castPlay(d, { url: fresh.url, title: fresh.title, live: !!fresh.live, type: mime(fresh.url, fresh.live) }, fresh.at || 0);
          if (!r.ok) { body.replaceChildren(h('div', { class: 'empty', text: tr('Could not cast') + ': ' + r.error }), h('button', { class: 'btn', text: '↻ Search again', onclick: show })); return; }
          m.close(); if (bar) stopBar();
          dev = d; hooks = hk; lastSt = null;
          if (hk && hk.onStart) hk.onStart();
          drawBar(fresh);
        } })),
        h('button', { class: 'btn ghost small', text: '↻ ' + tr('Search again'), onclick: show }));
    };
    show();
  }
  async function external(getMedia) {
    const media = getMedia(); if (!media || !media.url) return;
    const r = await native.extPlay(media.url, media.title);
    if (r.ok) toast(r.app === 'default' ? 'Opened in your default player' : tr('Opened in') + ' ' + r.app, 2200);
    else toast(tr('Could not open an external player') + ': ' + r.error, 3500);
  }
  return { pick, external, isCasting: () => !!bar, stop };
})();
