'use strict';
/** Virtual list: only the rows in view exist in the page, so 150 000 items scroll smoothly. */
class VList {
  constructor(el, rowH, renderRow) {
    this.el = el; this.rowH = rowH; this.renderRow = renderRow; this.count = 0; this.pool = new Map(); this.raf = 0;
    el.classList.add('vlist');
    this.sizer = h('div', { class: 'vsizer' });
    el.append(this.sizer);
    el.addEventListener('scroll', () => this.schedule(false));
    new ResizeObserver(() => this.schedule(true)).observe(el);
  }
  setCount(n, keepScroll) {
    this.count = n;
    this.sizer.style.height = (n * this.rowH) + 'px';
    if (!keepScroll) this.el.scrollTop = 0;
    this.refresh(true);
  }
  schedule(force) {
    if (force) this.force = true;
    if (this.raf) return;
    this.raf = requestAnimationFrame(() => { this.raf = 0; const f = this.force; this.force = false; this.refresh(f); });
  }
  refresh(force) {
    const top = this.el.scrollTop, hgt = this.el.clientHeight || 600;
    const first = Math.max(0, Math.floor(top / this.rowH) - 3);
    const last = Math.min(this.count - 1, Math.ceil((top + hgt) / this.rowH) + 3);
    if (force) { for (const n of this.pool.values()) n.remove(); this.pool.clear(); }
    else for (const [i, n] of [...this.pool]) if (i < first || i > last) { n.remove(); this.pool.delete(i); }
    for (let i = first; i <= last; i++) {
      if (this.pool.has(i)) continue;
      const n = this.renderRow(i);
      if (!n) continue;
      n.style.position = 'absolute'; n.style.top = (i * this.rowH) + 'px';
      n.style.left = '0'; n.style.right = '0'; n.style.height = this.rowH + 'px';
      this.sizer.append(n); this.pool.set(i, n);
    }
  }
  scrollTo(i) { this.el.scrollTop = Math.max(0, i * this.rowH - this.el.clientHeight / 3); this.refresh(false); }
  ensureVisible(i) {
    const t = i * this.rowH, b = t + this.rowH;
    if (t < this.el.scrollTop) this.el.scrollTop = t;
    else if (b > this.el.scrollTop + this.el.clientHeight) this.el.scrollTop = b - this.el.clientHeight;
    this.refresh(false);
  }
}
