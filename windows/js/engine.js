'use strict';
/** Plays live streams (HLS first, MPEG-TS fallback) and files (mp4 / mkv ...) in a <video> element. */
class Engine {
  constructor(video) {
    this.v = video; this.hls = null; this.ts = null; this.token = 0; this.lost = 0; this.last = null; this.stallT = 0;
    video.addEventListener('playing', () => KeepAwake.set(this, true));
    video.addEventListener('pause', () => KeepAwake.set(this, false));
  }
  /** What the server answers for a link (HTTP status, 0 = no answer): tells a refused login from a lost connection. */
  async probe(url) {
    try {
      const r = await fetch(url, { headers: { Range: 'bytes=0-0' } });
      try { if (r.body) r.body.cancel(); } catch (e) { }
      return r.status;
    } catch (e) { return 0; }
  }
  stopEngines() {
    if (this.hls) { try { this.hls.destroy(); } catch (e) { } this.hls = null; }
    if (this.ts) { try { this.ts.pause(); this.ts.unload(); this.ts.detachMediaElement(); this.ts.destroy(); } catch (e) { } this.ts = null; }
  }
  stop() {
    this.token++;
    this.stopEngines();
    clearTimeout(this.stallT);
    KeepAwake.set(this, false);
    const v = this.v;
    try { v.onerror = null; v.onwaiting = null; v.onplaying = null; v.pause(); v.removeAttribute('src'); v.load(); } catch (e) { }
  }
  /** urls = {m3u8, ts} (Xtream) or {direct} (M3U). cb = {playing(type), error(msg), status(msg)} */
  live(urls, cb, again) {
    const my = ++this.token;
    if (!again) { this.deny = 0; this.lost = 0; }
    this.stopEngines();
    this.last = [urls, cb];
    const v = this.v;
    const fmt = Store.state.settings.liveFmt || 'auto';
    const plan = [];
    if (urls.direct) {
      const low = urls.direct.split('?')[0].toLowerCase();
      if (low.endsWith('.m3u8') || low.endsWith('.m3u')) plan.push(['hls', urls.direct]);
      else plan.push(['ts', urls.direct], ['native', urls.direct]);
    } else if (fmt === 'ts') plan.push(['ts', urls.ts], ['hls', urls.m3u8]);
    else if (fmt === 'hls') plan.push(['hls', urls.m3u8], ['ts', urls.ts]);
    else plan.push(['hls', urls.m3u8], ['ts', urls.ts]);
    if (urls.raw) plan.push(['ts', urls.raw]);     // some servers only answer the short form  /user/pass/id
    const diag = [];                                // what each try answered (shown if everything fails)

    const step = (k) => {
      if (my !== this.token) return;
      if (k >= plan.length) { cb.error && cb.error(tr('Could not play this channel (the server did not answer or the format is not supported).') + (diag.length ? '  [' + diag.join(' | ') + ']' : '')); return; }
      const [type, url] = plan[k];
      this.stopEngines();
      let settled = false, recovered = false;
      const onPlaying = () => {
        if (settled || my !== this.token) return;
        settled = true; clearTimeout(timer); v.removeEventListener('playing', onPlaying);
        this.lost = 0; this.deny = 0;
        cb.playing && cb.playing(type);
      };
      const fail = () => {
        if (my !== this.token) return;
        if (settled) {                       // stream dropped while watching: reconnect a few times
          if (this.lost++ < 4) { cb.status && cb.status(trf('Connection lost, reconnecting ({0}/6)...', this.lost).replace('/6', '/4')); setTimeout(() => { if (my === this.token) this.live(urls, cb, true); }, 3000 * this.lost); }
          else cb.error && cb.error(tr('The stream stopped.'));
          return;
        }
        settled = true; clearTimeout(timer); v.removeEventListener('playing', onPlaying);
        this.stopEngines();
        step(k + 1);
      };
      const refused = (code) => {              // 401 / 403: the server counts the old connection, so more tries at once only make it worse: wait, then ask again
        if (my !== this.token) return;
        if (settled) return fail();             // it was playing and then got refused: the normal reconnect handles it
        settled = true; clearTimeout(timer); v.removeEventListener('playing', onPlaying);
        this.stopEngines();
        AppLog.add('live', 'HTTP ' + code + ' | ' + type + ' | try ' + (this.deny + 1));
        if (++this.deny <= 4) {
          const wait = 4 * this.deny;
          cb.status && cb.status(trf('The server refused the connection (HTTP {0}), trying again in {1} s...', code, wait));
          setTimeout(() => { if (my === this.token) this.live(urls, cb, true); }, wait * 1000);
        } else cb.error && cb.error(trf('The server refused the connection (HTTP {0}).', code) + ' ' + tr('The account may be in use on another device, or a VPN address is blocked.'));
      };
      const timer = setTimeout(() => { diag.push(type.toUpperCase() + ': no answer in 12 s'); fail(); }, 12000);
      v.addEventListener('playing', onPlaying);
      cb.status && cb.status(type === 'hls' ? 'Connecting...' : 'Connecting (TS)...');
      try {
        if (type === 'hls') {
          if (!window.Hls || !Hls.isSupported()) return fail();
          const hls = new Hls({ maxBufferLength: 20, liveSyncDurationCount: 3, manifestLoadingMaxRetry: 1, levelLoadingMaxRetry: 2, fragLoadingMaxRetry: 3, enableWorker: true });
          this.hls = hls;
          hls.on(Hls.Events.ERROR, (_e, d) => {
            if (!d.fatal) return;
            diag.push('HLS: ' + (d.response && d.response.code ? 'HTTP ' + d.response.code : d.details));
            if (d.response && (d.response.code === 401 || d.response.code === 403)) return refused(d.response.code);
            if (d.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) { recovered = true; hls.recoverMediaError(); return; }
            fail();
          });
          hls.on(Hls.Events.MANIFEST_PARSED, () => { v.play().catch(() => { }); });
          hls.loadSource(url);
          hls.attachMedia(v);
        } else if (type === 'ts') {
          if (!window.mpegts || !mpegts.getFeatureList().mseLivePlayback) return fail();
          const p = mpegts.createPlayer({ type: 'mpegts', isLive: true, url }, { enableWorker: true, liveBufferLatencyChasing: true, lazyLoadMaxDuration: 30 });
          this.ts = p;
          p.attachMediaElement(v);
          p.on(mpegts.Events.ERROR, (t, detail, info) => { diag.push('TS: ' + (info && info.code ? 'HTTP ' + info.code : detail || t)); if (info && (info.code === 401 || info.code === 403)) return refused(info.code); fail(); });
          p.load();
          const pr = p.play();
          if (pr && pr.catch) pr.catch(() => { });
        } else {
          v.onerror = () => fail();
          v.src = url;
          v.play().catch(() => { });
        }
      } catch (e) { fail(); }
    };
    step(0);
  }
  /** Plain file (movie / episode). cb = {error(msg), ready(), status(msg)}. A dropped connection reconnects at the same minute. */
  vod(url, startAt, cb) {
    const my = ++this.token;
    this.stopEngines();
    clearTimeout(this.stallT);
    const v = this.v;
    let tries = 0, resumeAt = startAt || 0;
    const ok = () => my === this.token;
    const start = (at) => {
      resumeAt = at;
      v.src = url;
      const onMeta = () => {
        v.removeEventListener('loadedmetadata', onMeta);
        if (!ok()) return;
        if (resumeAt > 20 && v.duration && resumeAt < v.duration - 45) v.currentTime = resumeAt;
        cb.ready && cb.ready();
      };
      v.addEventListener('loadedmetadata', onMeta);
      v.play().catch(() => { });
    };
    const reconnect = async (why) => {
      if (!ok()) return;
      const at = v.currentTime || resumeAt;
      const code = await this.probe(url);
      if (!ok()) return;
      if (code >= 400 && code < 500) {            // the server refused: asking again only makes it worse
        AppLog.add('vod', 'HTTP ' + code + ' | ' + url);
        cb.error && cb.error(trf('The server refused the link (HTTP {0}).', code) + (code === 401 || code === 403 ? ' ' + tr('The account may be in use on another device, a VPN may be blocked, or this title is not in your package.') : ''));
        return;
      }
      if (tries >= 6) { AppLog.add('vod', 'gave up after 6 reconnects | ' + url); cb.error && cb.error(tr('Connection lost. Go back and open it again.')); return; }
      tries++;
      AppLog.add('vod', 'reconnect #' + tries + ' (' + why + ') at ' + Math.floor(at) + ' s');
      cb.status && cb.status(trf('Connection lost, reconnecting ({0}/6)...', tries));
      setTimeout(() => { if (ok()) start(at); }, 1500 * tries);
    };
    v.onerror = () => { if (ok()) reconnect('error ' + ((v.error && v.error.code) || '')); };
    v.onwaiting = () => {                          // frozen for 20 s without an error: reconnect too
      clearTimeout(this.stallT);
      this.stallT = setTimeout(() => { if (ok() && v.readyState < 3 && !v.paused) reconnect('stalled'); }, 20000);
    };
    v.onplaying = () => { clearTimeout(this.stallT); tries = 0; };
    start(resumeAt);
  }
}
