'use strict';
/** Plays live streams (HLS first, MPEG-TS fallback) and files (mp4 / mkv ...) in a <video> element. */
class Engine {
  constructor(video) {
    this.v = video; this.hls = null; this.ts = null; this.token = 0; this.lost = 0; this.last = null;
  }
  stopEngines() {
    if (this.hls) { try { this.hls.destroy(); } catch (e) { } this.hls = null; }
    if (this.ts) { try { this.ts.pause(); this.ts.unload(); this.ts.detachMediaElement(); this.ts.destroy(); } catch (e) { } this.ts = null; }
  }
  stop() {
    this.token++;
    this.stopEngines();
    const v = this.v;
    try { v.onerror = null; v.pause(); v.removeAttribute('src'); v.load(); } catch (e) { }
  }
  /** urls = {m3u8, ts} (Xtream) or {direct} (M3U). cb = {playing(type), error(msg), status(msg)} */
  live(urls, cb) {
    const my = ++this.token;
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

    const step = (k) => {
      if (my !== this.token) return;
      if (k >= plan.length) { cb.error && cb.error('Could not play this channel (the server did not answer or the format is not supported).'); return; }
      const [type, url] = plan[k];
      this.stopEngines();
      let settled = false, recovered = false;
      const onPlaying = () => {
        if (settled || my !== this.token) return;
        settled = true; clearTimeout(timer); v.removeEventListener('playing', onPlaying);
        this.lost = 0;
        cb.playing && cb.playing(type);
      };
      const fail = () => {
        if (my !== this.token) return;
        if (settled) {                       // stream dropped while watching: reconnect a few times
          if (this.lost++ < 3) setTimeout(() => { if (my === this.token) this.live(urls, cb); }, 1500);
          else cb.error && cb.error('The stream stopped.');
          return;
        }
        settled = true; clearTimeout(timer); v.removeEventListener('playing', onPlaying);
        this.stopEngines();
        step(k + 1);
      };
      const timer = setTimeout(fail, 12000);
      v.addEventListener('playing', onPlaying);
      cb.status && cb.status(type === 'hls' ? 'Connecting...' : 'Connecting (TS)...');
      try {
        if (type === 'hls') {
          if (!window.Hls || !Hls.isSupported()) return fail();
          const hls = new Hls({ maxBufferLength: 20, liveSyncDurationCount: 3, manifestLoadingMaxRetry: 1, levelLoadingMaxRetry: 2, fragLoadingMaxRetry: 3, enableWorker: true });
          this.hls = hls;
          hls.on(Hls.Events.ERROR, (_e, d) => {
            if (!d.fatal) return;
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
          p.on(mpegts.Events.ERROR, () => fail());
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
  /** Plain file (movie / episode). cb = {error(msg), ready(), ended()} */
  vod(url, startAt, cb) {
    const my = ++this.token;
    this.stopEngines();
    const v = this.v;
    v.onerror = null;
    v.src = url;
    const onMeta = () => {
      v.removeEventListener('loadedmetadata', onMeta);
      if (my !== this.token) return;
      if (startAt > 20 && v.duration && startAt < v.duration - 45) v.currentTime = startAt;
      cb.ready && cb.ready();
    };
    v.addEventListener('loadedmetadata', onMeta);
    v.onerror = () => {
      if (my !== this.token) return;
      const e = v.error;
      const why = e && e.code === 4 ? 'Format not supported or the server refused the link (HTTP 401 / 403 / 404).' : (e && e.message) || 'Playback error';
      cb.error && cb.error(why);
    };
    v.play().catch(() => { });
  }
}
