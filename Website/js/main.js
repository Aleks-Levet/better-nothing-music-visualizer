class Component {
  constructor() {
    this.props = { accent: '#FA2828', idleMotion: true };
    this.state = {
      playing: false,
      audioBlocked: false,
      haptics: false,
      compareExpanded: false,
    };
  }

  setState(next) {
    Object.assign(this.state, next);
    this.render();
  }

  render() {
    const v = this.renderVals();
    document.querySelectorAll('[data-when]').forEach((el) => { el.hidden = !v[el.getAttribute('data-when')]; });
    document.querySelectorAll('[data-pressed]').forEach((el) => { el.setAttribute('aria-pressed', v[el.getAttribute('data-pressed')] ? 'true' : 'false'); });
    document.querySelectorAll('[data-expanded-state]').forEach((el) => { el.setAttribute('aria-expanded', v[el.getAttribute('data-expanded-state')] ? 'true' : 'false'); });
    document.querySelectorAll('[data-expand-panel]').forEach((el) => { el.setAttribute('data-expanded', v[el.getAttribute('data-expand-panel')] ? 'true' : 'false'); });
  }

  renderVals() {
    if (!this._bound) {
      this._bound = true;
      this.setRoot = (el) => { if (el) { this.rootEl = el; } };
      this.toggle = () => { this.togglePlay(); };
      this.toggleHap = () => { this.toggleHaptics(); };
      this.toggleCompare = () => { this.setState({ compareExpanded: !this.state.compareExpanded }); };
      this.padKick = () => { this.hit('kick'); };
      this.padBass = () => { this.hit('bass'); };
      this.padSnare = () => { this.hit('snare'); };
      this.padChord = () => { this.hit('chord'); };
      this.padHat = () => { this.hit('hat'); };
      this.hapticsOk = false;
      try {
        this.hapticsOk = typeof navigator.vibrate === 'function' && window.matchMedia('(pointer: coarse)').matches;
      } catch (e) {}
    }
    const st = this.state || {};
    const playing = !!st.playing;
    return {
      accent: this.props.accent ?? '#FA2828',
      setRoot: this.setRoot,
      toggle: this.toggle,
      toggleHap: this.toggleHap,
      toggleCompare: this.toggleCompare,
      padKick: this.padKick,
      padBass: this.padBass,
      padSnare: this.padSnare,
      padChord: this.padChord,
      padHat: this.padHat,
      hapticsOk: this.hapticsOk,
      hapticsOn: !!st.haptics,
      compareExpanded: !!st.compareExpanded,
      playing: playing,
      paused: !playing,
      audioBlocked: !!st.audioBlocked
    };
  }

  componentDidMount() {
    this.t0 = performance.now();
    this.raw = new Float32Array(36);
    this.lv = new Float32Array(36);
    this.peak = new Float32Array(36);
    this.abv = new Float32Array(12);
    for (let i = 0; i < 36; i++) { this.peak[i] = 0.3; }
    this.frame = 0;
    this.lastStock = 0;
    this.heroOn = true;
    this.abOn = true;
    this.rot = [0, 0, 0];
    this.ripples = [];
    this.manual = new Float32Array(36);
    this.reduced = false;
    try { this.reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) {}
    if (this.collect()) {
      this.rest();
      this.atRest = true;
      this.startComparisonIdle();
    }
  }

  componentWillUnmount() {
    if (this.raf) { cancelAnimationFrame(this.raf); }
    if (this.compareIdleRaf) { cancelAnimationFrame(this.compareIdleRaf); }
    if (this.compareObserver) { this.compareObserver.disconnect(); }
    if (this.compareVisibilityChange) { document.removeEventListener('visibilitychange', this.compareVisibilityChange); }
    if (this._ptr) { try { window.removeEventListener('pointermove', this._ptr); } catch (e) {} }
    this.stopAudio();
    if (this.audio) {
      try { this.audio.ctx.close(); } catch (e) {}
      this.audio = null;
    }
  }

  /* Find the animated elements. They are all literal markup; the script only changes their opacity and height. */
  collect() {
    let r = this.rootEl;
    if (!r || !r.isConnected) { r = document.querySelector('.bnmv-root'); }
    if (!r) { return false; }
    const q = (s) => r.querySelector(s);
    const qa = (s) => Array.prototype.slice.call(r.querySelectorAll(s));
    const els = {
      segs: qa('.bn-seg'), bars: qa('.bn-bar'), live: qa('.bn-live'), stock: qa('.bn-stock'),
      disc: q('.bn-disc'), bloom: q('.bn-bloom'), hero: q('.bn-hero'), heroTitle: q('h1'), ab: q('.bn-ab'),
      rings: qa('.bn-ring'), tilt: q('.bn-tilt'), field: q('.bn-field'), halo: q('.bn-halo'),
      wave: q('.bn-wave'), spec: q('.bn-spec'), modes: q('.bn-modes'), dawn: q('.bn-dawn-glow'),
      rays: q('.bn-rays'), gl: qa('.bn-gl'), flip: q('.bn-flip'), prog: q('.bn-prog')
    };
    if (!els.disc || els.segs.length === 0) { return false; }
    els.rest = {
      segs: els.segs.map((e) => e.style.opacity),
      live: els.live.map((e) => e.style.opacity),
      stock: els.stock.map((e) => e.style.opacity),
      bars: els.bars.map((e) => [e.getAttribute('y'), e.getAttribute('height')]),
      bloom: els.bloom ? els.bloom.style.opacity : '',
      rays: els.rays ? els.rays.style.opacity : '',
      gl: els.gl.map((e) => e.style.opacity)
    };
    this.els = els;
    this.stockOrder = els.stock.map((_, i) => i);
    for (let i = this.stockOrder.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [this.stockOrder[i], this.stockOrder[j]] = [this.stockOrder[j], this.stockOrder[i]];
    }
    this.stockLevels = new Array(els.stock.length).fill(0);
    this.stockHoldUntil = new Array(els.stock.length).fill(0);
    this.stockDecayAt = new Array(els.stock.length).fill(0);
    this.atRest = false;
    if (!this._ptr) {
      this._ptr = (ev) => {
        this.ptr = { x: ev.clientX, y: ev.clientY, mouse: ev.pointerType === 'mouse', at: performance.now() };
      };
      try { window.addEventListener('pointermove', this._ptr, { passive: true }); } catch (e) {}
    }
    return true;
  }

  rest() {
    const E = this.els;
    const R = E.rest;
    E.segs.forEach((e, i) => { e.style.opacity = R.segs[i]; });
    E.live.forEach((e, i) => { e.style.opacity = R.live[i]; });
    E.stock.forEach((e, i) => { e.style.opacity = R.stock[i]; });
    E.bars.forEach((e, i) => { e.setAttribute('y', R.bars[i][0]); e.setAttribute('height', R.bars[i][1]); });
    E.disc.style.transform = 'scale(1)';
    if (E.bloom) { E.bloom.style.opacity = R.bloom; }
    if (E.rays) { E.rays.style.opacity = R.rays; }
    E.gl.forEach((e, i) => { e.style.opacity = R.gl[i]; });
    this.vis = null;
    /* One still frame for the canvases, so nothing is blank when motion is off. */
    this.rot = [0, 0, 0];
    this.ripples = [];
    E.rings.forEach((g) => { g.style.transform = 'rotate(0deg)'; });
    if (E.tilt) { E.tilt.style.transform = ''; }
    this.synth(this.raw, 2.52);
    for (let z = 0; z < 36; z++) { this.lv[z] = this.raw[z]; }
    this.measure();
    this.drawField(0);
    this.drawHalo();
    this.drawScope(false, 2.52);
    for (let z = 0; z < 36; z++) { this.lv[z] = 0; }
  }

  /* Size the hero canvases and find the disc inside the dot field. */
  measure() {
    const E = this.els;
    this.geo = null;
    if (!E) { return; }
    if (E.halo) {
      const hw = E.halo.clientWidth;
      if (hw > 2) {
        const px = Math.round(hw * Math.min(window.devicePixelRatio || 1, 1.5));
        if (E.halo.width !== px || E.halo.height !== px) { E.halo.width = px; E.halo.height = px; }
      }
    }
    if (!E.field || !E.hero) { return; }
    const fr = E.field.getBoundingClientRect();
    const hr = E.hero.getBoundingClientRect();
    if (fr.width < 2 || fr.height < 2) { return; }
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const w = Math.round(fr.width * dpr);
    const h = Math.round(fr.height * dpr);
    if (E.field.width !== w || E.field.height !== h) { E.field.width = w; E.field.height = h; }
    this.geo = {
      w: fr.width, h: fr.height, dpr: dpr, left: fr.left, top: fr.top,
      cx: hr.left - fr.left + hr.width / 2, cy: hr.top - fr.top + hr.height / 2, s: hr.width / 400
    };
  }

  /* The dot field: ripples leave the disc on every kick and snare. */
  drawField(dt) {
    const E = this.els;
    const g = this.geo;
    if (!g || !E.field) { return; }
    const ctx = E.field.getContext('2d');
    if (!ctx) { return; }
    ctx.setTransform(g.dpr, 0, 0, g.dpr, 0, 0);
    ctx.clearRect(0, 0, g.w, g.h);
    const rp = this.ripples;
    for (let i = rp.length - 1; i >= 0; i--) {
      rp[i].age += dt;
      if (rp[i].age > 1.8) { rp.splice(i, 1); }
    }
    if (rp.length === 0) { return; }
    const edge = 100 * g.s;
    const sp = 26;
    const cols = Math.ceil(g.w / sp);
    const rows = Math.ceil(g.h / sp);
    ctx.fillStyle = '#FFFFFF';
    for (let j = 0; j < rows; j++) {
      const y = 13 + j * sp;
      const dy = y - g.cy;
      for (let i = 0; i < cols; i++) {
        const x = 13 + i * sp;
        const dx = x - g.cx;
        const d = Math.sqrt(dx * dx + dy * dy);
        let b = 0;
        for (let k = 0; k < rp.length; k++) {
          const q = (d - edge - rp[k].age * rp[k].speed) / (40 + rp[k].age * 36);
          if (q > -1.5 && q < 1.5) { b += rp[k].str * Math.exp(-2.4 * q * q) * (1 - rp[k].age / 1.8); }
        }
        if (b < 0.05) { continue; }
        if (b > 1) { b = 1; }
        ctx.globalAlpha = 0.12 + 0.88 * b;
        ctx.beginPath();
        ctx.arc(x, y, 1 + 1.5 * b, 0, 6.2832);
        ctx.fill();
      }
    }
    ctx.globalAlpha = 1;
  }

  /* Glow behind the rings: plain strokes on a canvas that CSS blurs. */
  drawHalo() {
    const cv = this.els.halo;
    if (!cv || cv.width < 4) { return; }
    const ctx = cv.getContext('2d');
    if (!ctx) { return; }
    const s = cv.width / 400;
    ctx.setTransform(s, 0, 0, s, 0, 0);
    ctx.clearRect(0, 0, 400, 400);
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineCap = 'round';
    const rings = [[120, 8, 10, 0], [140, 12, 8, 8], [158, 16, 6, 20]];
    const lv = this.vis || this.lv;
    for (let k = 0; k < 3; k++) {
      const rr = rings[k][0];
      const n = rings[k][1];
      const sw = rings[k][2];
      const gapA = (sw + 6) / rr;
      const rot = ((this.rot[k] || 0) * Math.PI) / 180;
      ctx.lineWidth = sw * 1.9;
      for (let i = 0; i < n; i++) {
        const v = lv[rings[k][3] + i];
        if (v < 0.12) { continue; }
        ctx.globalAlpha = 0.85 * v;
        ctx.beginPath();
        ctx.arc(200, 200, rr, -Math.PI / 2 + (i * 2 * Math.PI) / n + gapA / 2 + rot, -Math.PI / 2 + ((i + 1) * 2 * Math.PI) / n - gapA / 2 + rot);
        ctx.stroke();
      }
    }
    ctx.globalAlpha = 1;
  }

  /* The disc leans a few degrees toward a mouse pointer. */
  applyTilt() {
    const E = this.els;
    const g = this.geo;
    const p = this.ptr;
    if (!E.tilt || !g) { return; }
    let tx = 0;
    let ty = 0;
    if (p && p.mouse && performance.now() - p.at < 4000) {
      tx = Math.max(-1, Math.min(1, (p.x - g.left - g.cx) / 500));
      ty = Math.max(-1, Math.min(1, (p.y - g.top - g.cy) / 400));
    }
    if (!this.tl) { this.tl = { x: 0, y: 0 }; }
    this.tl.x += (tx - this.tl.x) * 0.07;
    this.tl.y += (ty - this.tl.y) * 0.07;
    if (Math.abs(this.tl.x) < 0.003 && Math.abs(this.tl.y) < 0.003) {
      if (this.tilted) { E.tilt.style.transform = ''; this.tilted = false; }
      return;
    }
    this.tilted = true;
    E.tilt.style.transform = 'perspective(900px) rotateY(' + (this.tl.x * 7).toFixed(2) + 'deg) rotateX(' + (-this.tl.y * 7).toFixed(2) + 'deg)';
  }

  pill(c, x, y, w, h, r) {
    window.BNMVCanvasProcessing.pill(c, x, y, w, h, r);
  }

  /* The scope in the "sound to light" section: waveform on top, zones and their lights below. */
  drawScope(live, t) {
    const E = this.els;
    const lv = this.lv;
    const raw = this.raw;
    const prep = (cv) => {
      const prepared = window.BNMVCanvasProcessing.prepareCanvas(cv, 2);
      if (!prepared) { return null; }
      return { ctx: prepared.context, w: prepared.width, h: prepared.height };
    };

    const wv = prep(E.wave);
    if (wv) {
      const c = wv.ctx;
      const mid = wv.h / 2;
      c.fillStyle = '#2A2827';
      c.fillRect(0, mid - 0.5, wv.w, 1);
      c.strokeStyle = '#FFFFFF';
      c.lineWidth = 1.6;
      c.lineJoin = 'round';
      c.lineCap = 'round';
      c.beginPath();
      const n = Math.max(40, Math.floor(wv.w / 3));
      const a = this.audio;
      const useLive = !!(live && a && a.time);
      const en = this.env || { kick: 0, bass: 0, snare: 0, hat: 0, arp: 0 };
      if (useLive) { a.analyser.getByteTimeDomainData(a.time); }
      for (let i = 0; i <= n; i++) {
        const x = i / n;
        let v = 0;
        if (useLive) {
          v = ((a.time[Math.floor(x * (a.time.length - 1))] - 128) / 128) * 1.7;
        } else {
          v = 0.62 * en.kick * Math.sin(x * 19 + t * 38)
            + 0.38 * en.bass * Math.sin(x * 44 + t * 26)
            + 0.22 * en.arp * Math.sin(x * 150 + t * 70)
            + (0.5 * en.snare + 0.28 * en.hat) * Math.sin(x * 917.3 + t * 313) * Math.sin(x * 371.1 - t * 197);
        }
        if (v > 1) { v = 1; } else if (v < -1) { v = -1; }
        const y = mid - v * (wv.h * 0.44);
        if (i === 0) { c.moveTo(0, y); } else { c.lineTo(x * wv.w, y); }
      }
      c.stroke();
    }

    const sp = prep(E.spec);
    if (sp) {
      const c = sp.ctx;
      const gap = 14;
      const slot = (sp.w - gap * 2) / 36;
      const bw = Math.max(2, slot * 0.62);
      const top = 6;
      const base = sp.h - 58;
      const span = base - top;
      const ly = sp.h - 46;
      const lh = 22;
      const accent = this.props.accent ?? '#FA2828';
      c.fillStyle = '#2A2827';
      c.fillRect(0, base + 0.5, sp.w, 1);
      for (let z = 0; z < 36; z++) {
        const grp = z < 8 ? 0 : (z < 20 ? 1 : 2);
        const x = z * slot + grp * gap + (slot - bw) / 2;
        const bh = Math.max(2, lv[z] * span);
        c.fillStyle = '#FFFFFF';
        c.globalAlpha = 0.92;
        this.pill(c, x, base - bh, bw, bh, Math.min(bw / 2, 4));
        c.globalAlpha = 0.08 + 0.92 * lv[z];
        this.pill(c, x, ly, bw, lh, Math.min(bw / 2, lh / 2));
        c.globalAlpha = 1;
        c.fillStyle = accent;
        c.fillRect(x, base - Math.max(2, raw[z] * span) - 3, bw, 2);
      }
      c.fillStyle = '#827E7B';
      c.font = "500 11px 'Google Sans Flex', system-ui, sans-serif";
      c.textBaseline = 'alphabetic';
      c.textAlign = 'left';
      c.fillText('30 Hz', 0, sp.h - 6);
      c.textAlign = 'center';
      c.fillText('250 Hz', 8 * slot + gap / 2, sp.h - 6);
      c.fillText('4 kHz', 20 * slot + gap * 1.5, sp.h - 6);
      c.textAlign = 'right';
      c.fillText('16 kHz', sp.w, sp.h - 6);
    }
  }

  /* 36 zones: 8 across the bass (30 to 250 Hz), 12 across the mids (250 Hz to 4 kHz), 16 across the highs (4 to 16 kHz). */
  edges() {
    return window.BNMVAudioProcessing.edges();
  }

  step(now) {
    if (!this.els || !this.els.disc || !this.els.disc.isConnected) {
      if (!this.collect()) { return; }
    }
    const E = this.els;
    const st = this.state || {};
    const a = this.audio;
    const dt = Math.min(0.1, Math.max(0.001, (now - (this.last || now)) / 1000));
    this.last = now;
    const running = !!(a && a.timer && a.ctx.state === 'running');
    const live = !!st.playing && running;
    if (st.playing && a && a.timer && !running && !st.audioBlocked) {
      this.wait = (this.wait || 0) + dt;
      if (this.wait > 1.5) { this.wait = 0; this.setState({ audioBlocked: true }); }
    } else {
      this.wait = 0;
    }
    if (!st.playing) { return; }
    this.atRest = false;

    const raw = this.raw;
    if (live) {
      this.readAnalyser(raw);
    } else {
      this.synth(raw, (now - this.t0) / 1000);
    }
    /* Pad hits: added on top of whatever is playing, then left to fade. */
    const man = this.manual;
    const mdec = Math.pow(0.84, dt * 60);
    for (let z = 0; z < 36; z++) {
      if (man[z] > raw[z]) { raw[z] = man[z]; }
      man[z] *= mdec;
    }

    /* Downward-only smoothing: jump up at once, fade down. 0.8 per frame at 60 fps, like the default decay-alpha. */
    const k = Math.pow(0.8, dt * 60);
    const lv = this.lv;
    for (let z = 0; z < 36; z++) {
      let r = raw[z];
      if (!(r >= 0)) { r = 0; }
      if (!(lv[z] >= 0)) { lv[z] = 0; }
      lv[z] = r >= lv[z] ? r : r + (lv[z] - r) * k;
    }

    if (this.frame % 12 === 0) {
      const vh = window.innerHeight || 900;
      const seen = (el) => {
        if (!el) { return false; }
        const b = el.getBoundingClientRect();
        return b.bottom > -80 && b.top < vh + 80;
      };
      this.heroOn = seen(E.hero);
      this.abOn = seen(E.ab);
      this.scopeOn = seen(E.spec);
      this.modesOn = seen(E.modes);
      this.dawnOn = seen(E.dawn);
      this.flipOn = seen(E.flip);
      this.measure();
    }
    this.frame++;

    const t = (now - this.t0) / 1000;
    const kickLv = (lv[0] + lv[1] + lv[2] + lv[3]) / 4;
    const snLv = Math.max(lv[12], lv[13], lv[14]);
    const hatLv = Math.max(lv[26], lv[30], lv[33]);
    const heroBeat = Math.max(kickLv, snLv * 0.72, hatLv * 0.28);
    this.heroPulse = (this.heroPulse || 0) + (heroBeat - (this.heroPulse || 0)) * (heroBeat > (this.heroPulse || 0) ? 0.34 : 0.12);
    if (E.heroTitle) {
      const pulse = this.reduced ? 0 : this.heroPulse;
      E.heroTitle.style.setProperty('--hero-wdth', (116 + pulse * 18).toFixed(1));
      E.heroTitle.style.setProperty('--hero-wght', (690 + pulse * 260).toFixed(1));
      E.heroTitle.style.setProperty('--hero-grad', (10 + pulse * 50).toFixed(1));
      E.heroTitle.style.setProperty('--hero-rond', (38 + pulse * 62).toFixed(1));
    }
    const kickRaw = Math.max(raw[0], raw[1], raw[2], raw[3]);
    const snRaw = Math.max(raw[12], raw[13], raw[14]);
    if (kickRaw - (this.prevKick || 0) > 0.28 && now - (this.lastRip || 0) > 110) {
      this.lastRip = now;
      this.ripples.push({ age: 0, str: 0.5 + 0.5 * kickRaw, speed: 560 });
      if (st.haptics) { try { navigator.vibrate(26); } catch (e) {} }
    } else if (snRaw - (this.prevSn || 0) > 0.3 && now - (this.lastSn || 0) > 110) {
      this.lastSn = now;
      this.ripples.push({ age: 0, str: 0.3, speed: 820 });
    }
    this.prevKick = kickRaw;
    this.prevSn = snRaw;
    if (this.ripples.length > 6) { this.ripples.shift(); }

    if (this.heroOn) {
      /* Ignition: on the first moving frames the rings light up segment by segment, then hand over to the music. */
      if (!this.introAt) { this.introAt = now; }
      const it = (now - this.introAt) / 1000;
      const intro = it < 2.4;
      if (!this.vis) { this.vis = new Float32Array(36); }
      const vis = this.vis;
      const nSeg = Math.min(36, E.segs.length);
      for (let z = 0; z < nSeg; z++) {
        let v = lv[z];
        let o = 0.1 + 0.9 * v;
        if (intro) {
          const ring = z < 8 ? 0 : (z < 20 ? 1 : 2);
          const idx = z - (ring === 0 ? 0 : (ring === 1 ? 8 : 20));
          const cnt = ring === 0 ? 8 : (ring === 1 ? 12 : 16);
          const tOn = 0.3 + ring * 0.14 + (idx / cnt) * 0.9;
          if (it < tOn) {
            v = 0;
            o = 0.03;
          } else {
            const fl = Math.exp(-(it - tOn) * 4.5);
            if (fl > v) { v = fl; }
            o = 0.1 + 0.9 * v;
          }
        }
        vis[z] = v;
        E.segs[z].style.opacity = o.toFixed(3);
      }
      const groups = [[0, 4], [4, 8], [8, 14], [14, 20], [20, 28]];
      const bars = [[69.1, 86.4], [44.2, 55.3], [59.0, 73.7], [85.4, 106.7], [62.1, 77.6], [77.6, 84.5]];
      let sum = 0;
      const nBar = Math.min(6, E.bars.length);
      for (let b = 0; b < nBar; b++) {
        let v = 0;
        if (b < 5) {
          for (let z = groups[b][0]; z < groups[b][1]; z++) { if (lv[z] > v) { v = lv[z]; } }
          sum += v;
        } else {
          v = Math.min(1, (sum / 5) * 1.3);
        }
        let h = bars[b][0] * (0.5 + 0.75 * v);
        if (h > bars[b][1]) { h = bars[b][1]; }
        if (intro) {
          const gp = (it - 0.2 - b * 0.1) / 0.55;
          const ez = gp <= 0 ? 0 : (gp >= 1 ? 1 : 1 - Math.pow(1 - gp, 3));
          h = 12 + (h - 12) * ez;
        }
        E.bars[b].setAttribute('y', (200 - h / 2).toFixed(2));
        E.bars[b].setAttribute('height', h.toFixed(2));
      }
      const kick = (lv[0] + lv[1] + lv[2] + lv[3]) / 4;
      E.disc.style.transform = 'scale(' + (1 + 0.022 * kick).toFixed(4) + ')';
      if (E.bloom) { E.bloom.style.opacity = (0.3 + 0.7 * kick).toFixed(3); }
      if (E.rays) { E.rays.style.opacity = (0.08 + 0.55 * kick).toFixed(3); }
      const speeds = [4.5, -3, 2.1];
      for (let k = 0; k < E.rings.length && k < 3; k++) {
        this.rot[k] = t * speeds[k];
        E.rings[k].style.transform = 'rotate(' + this.rot[k].toFixed(2) + 'deg)';
      }
      this.applyTilt();
      this.drawField(dt);
      this.drawHalo();
    }

    if (this.scopeOn) { this.drawScope(live, t); }
    if (this.modesOn && E.modes) {
      const ms = E.modes.style;
      ms.setProperty('--k', kickLv.toFixed(3));
      ms.setProperty('--s', snLv.toFixed(3));
      ms.setProperty('--h', hatLv.toFixed(3));
    }
    if (this.dawnOn && E.dawn) { E.dawn.style.opacity = (0.4 + 0.6 * kickLv).toFixed(3); }
    /* The phone in the closing scene: 17 lights in the shape of the logo. */
    if (this.flipOn && E.gl.length >= 17) {
      const band = (from, to) => {
        let m = 0;
        for (let z = from; z < to; z++) { if (lv[z] > m) { m = lv[z]; } }
        return m;
      };
      const vals = [band(10, 14), band(14, 18), kickLv, band(0, 4), band(4, 8), band(8, 14), band(14, 20), band(20, 28)];
      for (let i = 0; i < 8; i++) { vals.push(Math.max(lv[20 + i * 2], lv[21 + i * 2])); }
      vals.push(Math.min(1, ((vals[3] + vals[4] + vals[5] + vals[6] + vals[7]) / 5) * 1.3));
      for (let i = 0; i < 17; i++) { E.gl[i].style.opacity = (0.07 + 0.93 * vals[i]).toFixed(3); }
      if (E.prog && st.playing) { E.prog.style.width = (((t % 9.6) / 9.6) * 100).toFixed(1) + '%'; }
    }

    if (this.abOn) {
      const nLive = Math.min(12, E.live.length);
      for (let i = 0; i < nLive; i++) {
        const v = Math.max(lv[i * 3], lv[i * 3 + 1], lv[i * 3 + 2]);
        this.abv[i] = v;
        E.live[i].style.opacity = (0.08 + 0.92 * v).toFixed(3);
      }
      /* Slight noise keeps the stock-style quantized preview from looking perfectly regular. */
      if (now - this.lastStock >= 1000 / 15) {
        this.lastStock = now;
        const nStock = Math.min(12, E.stock.length);
        for (let i = 0; i < nStock; i++) {
          const noisy = Math.max(0, Math.min(1, this.abv[i] + (Math.random() - 0.5) * 0.42));
          const targetLevel = Math.round(noisy * 3);
          const glyph = this.stockOrder[i];
          let level = this.stockLevels[glyph];
          if (targetLevel >= level) {
            level = targetLevel;
            this.stockDecayAt[glyph] = now;
            if (level === 3) { this.stockHoldUntil[glyph] = now + 150; }
          } else if (level === 3 && now < this.stockHoldUntil[glyph]) {
            this.stockDecayAt[glyph] = now;
          } else if (now - this.stockDecayAt[glyph] >= 100) {
            level--;
            this.stockDecayAt[glyph] = now;
          }
          this.stockLevels[glyph] = level;
          E.stock[glyph].style.opacity = (0.08 + 0.92 * level / 3).toFixed(3);
        }
      }
    }
  }

  /* Live mode: the peak magnitude inside each zone's frequency range, from a real FFT of the demo audio. */
  readAnalyser(raw) {
    window.BNMVAudioProcessing.readAnalyser(this.audio, this.peak, raw);
  }

  /* Silent preview: the same pattern as the demo track, computed from the clock instead of from audio. */
  synth(raw, t) {
    this.env = window.BNMVAudioProcessing.synth(raw, t);
  }

  togglePlay() {
    const st = this.state || {};
    if (st.playing) {
      this.stopAudio();
      this.setState({ playing: false });
      this.stopRenderer();
      this.scheduleComparisonIdle();
      return;
    }
    let ok = true;
    try {
      const p = this.startAudio();
      if (p && p.catch) {
        p.catch(() => { this.stopAudio(); this.setState({ audioBlocked: true }); });
      }
    } catch (e) {
      ok = false;
    }
    this.setState({ playing: true, audioBlocked: !ok });
    this.startRenderer();
  }

  startRenderer() {
    if (this.raf) { return; }
    if (this.rootEl) { this.rootEl.classList.add('bn-js'); }
    const tick = (now) => {
      this.raf = requestAnimationFrame(tick);
      try { this.step(now); } catch (e) {}
    };
    this.raf = requestAnimationFrame(tick);
  }

  /* Keep the comparison meaningful before a visitor starts audio: the stock row
     is deliberately stepped at 15 fps in three brightness states, while BNMV
     follows the same sine signal smoothly at the display frame rate. */
  startComparisonIdle() {
    if (this.reduced || !this.els) { return; }
    if (!this.compareObserver && this.els.ab && 'IntersectionObserver' in window) {
      this.compareVisible = false;
      this.compareObserver = new IntersectionObserver((entries) => {
        this.compareVisible = entries.some((entry) => entry.isIntersecting);
        if (this.compareVisible) { this.scheduleComparisonIdle(); }
        else { this.stopComparisonIdle(); }
      }, { rootMargin: '120px 0px' });
      this.compareObserver.observe(this.els.ab);
    } else if (!this.compareObserver) {
      this.compareVisible = true;
    }
    if (!this.compareVisibilityChange) {
      this.compareVisibilityChange = () => {
        if (document.hidden) { this.stopComparisonIdle(); }
        else if (this.compareVisible) { this.scheduleComparisonIdle(); }
      };
      document.addEventListener('visibilitychange', this.compareVisibilityChange);
    }
    this.scheduleComparisonIdle();
  }

  scheduleComparisonIdle() {
    if (this.compareIdleRaf || this.state.playing || document.hidden || !this.compareVisible || !this.els) { return; }
    const tick = (now) => {
      this.compareIdleRaf = null;
      if (this.state.playing || document.hidden || !this.compareVisible || !this.els) { return; }
      const E = this.els;
      const t = now / 1000;
      const nLive = Math.min(12, E.live.length);
      for (let i = 0; i < nLive; i++) {
        const sine = (Math.sin(t * Math.PI * 1.1 + i * 0.74) + 1) / 2;
        E.live[i].style.opacity = (0.08 + 0.92 * sine).toFixed(3);
      }
      if (now - (this.compareLastStockFrame || 0) >= 1000 / 15) {
        this.compareLastStockFrame = now;
        const nStock = Math.min(12, E.stock.length);
        for (let i = 0; i < nStock; i++) {
          const sine = (Math.sin(t * Math.PI * 1.1 + i * 0.74) + 1) / 2;
          const level = Math.round(sine * 2);
          E.stock[i].style.opacity = (0.39 + 0.61 * level / 2).toFixed(3);
        }
      }
      this.compareIdleRaf = requestAnimationFrame(tick);
    };
    this.compareIdleRaf = requestAnimationFrame(tick);
  }

  stopComparisonIdle() {
    if (this.compareIdleRaf) {
      cancelAnimationFrame(this.compareIdleRaf);
      this.compareIdleRaf = null;
    }
  }

  stopRenderer() {
    if (this.raf) {
      cancelAnimationFrame(this.raf);
      this.raf = null;
    }
    if (this.rootEl) { this.rootEl.classList.remove('bn-js'); }
    if (this.els) { this.rest(); }
    if (this.els && this.els.heroTitle) {
      ['--hero-wdth', '--hero-wght', '--hero-grad', '--hero-rond'].forEach((property) => {
        this.els.heroTitle.style.removeProperty(property);
      });
    }
    this.heroPulse = 0;
  }

  toggleHaptics() {
    const on = !this.state.haptics;
    this.setState({ haptics: on });
    try { navigator.vibrate(on ? 30 : 0); } catch (e) {}
  }

  /* A pad: light its zones straight away and play the matching sound. */
  hit(kind) {
    const m = this.manual;
    if (!m) { return; }
    const bump = (from, to, peak) => {
      const mid = (from + to) / 2;
      const half = (to - from) / 2 + 0.8;
      for (let z = from; z <= to; z++) {
        const d = (z - mid) / half;
        const v = peak * Math.exp(-d * d);
        if (v > m[z]) { m[z] = v; }
      }
    };
    if (kind === 'kick') { bump(0, 4, 1); }
    else if (kind === 'bass') { bump(2, 7, 0.95); }
    else if (kind === 'snare') { bump(10, 16, 0.95); bump(20, 25, 0.6); }
    else if (kind === 'hat') { bump(24, 35, 0.9); }
    else { bump(9, 19, 0.9); }
    this.lastHit = performance.now();
    try {
      const a = this.ensureAudio();
      a.ctx.resume();
      const t = a.ctx.currentTime + 0.02;
      if (kind === 'kick') { this.kick(t); }
      else if (kind === 'bass') { this.bass(t, 55, 0.5); }
      else if (kind === 'snare') { this.snare(t); }
      else if (kind === 'hat') { this.hat(t, 0.16, 0.34); }
      else {
        this.pad(t, [220, 261.63, 329.63]);
        this.pluck(t, 220, 1);
        this.pluck(t + 0.09, 329.63, 0.8);
        this.pluck(t + 0.18, 440, 0.7);
      }
    } catch (e) {}
  }

  startAudio() {
    const a = this.ensureAudio();
    const p = a.ctx.resume();
    a.next = a.ctx.currentTime + 0.08;
    if (a.timer) { clearInterval(a.timer); }
    a.timer = setInterval(() => { this.schedule(); }, 25);
    return p;
  }

  ensureAudio() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) { throw new Error('Web Audio is not available'); }
    if (!this.audio) {
      const ctx = new AC();
      const master = ctx.createGain();
      master.gain.value = 0.6;
      const comp = ctx.createDynamicsCompressor();
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      analyser.smoothingTimeConstant = 0;
      analyser.minDecibels = -85;
      analyser.maxDecibels = -15;
      master.connect(comp);
      comp.connect(analyser);
      analyser.connect(ctx.destination);
      const noise = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
      const ch = noise.getChannelData(0);
      for (let i = 0; i < ch.length; i++) { ch[i] = Math.random() * 2 - 1; }
      const delay = ctx.createDelay(1);
      delay.delayTime.value = 0.45;
      const feedback = ctx.createGain();
      feedback.gain.value = 0.36;
      const wet = ctx.createGain();
      wet.gain.value = 0.32;
      delay.connect(feedback);
      feedback.connect(delay);
      delay.connect(wet);
      wet.connect(master);
      this.audio = {
        ctx: ctx, master: master, analyser: analyser, noise: noise, delay: delay,
        data: new Uint8Array(analyser.frequencyBinCount), time: new Uint8Array(analyser.fftSize), step: 0, next: 0, timer: null
      };
    }
    return this.audio;
  }

  stopAudio() {
    const a = this.audio;
    if (!a) { return; }
    if (a.timer) { clearInterval(a.timer); a.timer = null; }
    try { a.ctx.suspend(); } catch (e) {}
  }

  /* 100 bpm, sixteenth-note grid, four bars: A minor, F, C, G. */
  schedule() {
    const a = this.audio;
    if (!a || a.ctx.state !== 'running') { return; }
    if (a.next < a.ctx.currentTime) { a.next = a.ctx.currentTime + 0.05; }
    while (a.next < a.ctx.currentTime + 0.12) {
      this.playStep(a.step, a.next);
      a.step = (a.step + 1) % 64;
      a.next += 0.15;
    }
  }

  playStep(step, t) {
    const i = step % 16;
    const bar = Math.floor(step / 16) % 4;
    const roots = [55, 43.65, 65.41, 49];
    const chords = [
      [220, 261.63, 329.63, 440, 493.88],
      [174.61, 220, 261.63, 329.63, 440],
      [261.63, 329.63, 392, 523.25, 587.33],
      [196, 246.94, 293.66, 392, 493.88]
    ];
    const seq = [0, 2, 1, 3, 4, 3, 1, 2];
    if (i === 0 || i === 6 || i === 10) {
      this.kick(t);
      this.bass(t, roots[bar], i === 0 ? 0.5 : 0.28);
    }
    if (i === 4 || i === 12) { this.snare(t); }
    if (i % 2 === 0) {
      this.hat(t, i === 14 ? 0.18 : 0.045, i % 4 === 2 ? 0.34 : 0.22);
      this.pluck(t, chords[bar][seq[i / 2]], i % 4 === 0 ? 1 : 0.7);
    } else if (i === 3 || i === 11) {
      this.hat(t, 0.03, 0.1);
    }
    if (i === 0) { this.pad(t, chords[bar]); }
  }

  kick(t) {
    const a = this.audio;
    const c = a.ctx;
    const o = c.createOscillator();
    const g = c.createGain();
    o.type = 'sine';
    o.frequency.setValueAtTime(160, t);
    o.frequency.exponentialRampToValueAtTime(46, t + 0.11);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(1, t + 0.004);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.32);
    o.connect(g);
    g.connect(a.master);
    o.start(t);
    o.stop(t + 0.34);
  }

  snare(t) {
    const a = this.audio;
    const c = a.ctx;
    const n = c.createBufferSource();
    n.buffer = a.noise;
    const f = c.createBiquadFilter();
    f.type = 'bandpass';
    f.frequency.value = 2200;
    f.Q.value = 0.7;
    const g = c.createGain();
    g.gain.setValueAtTime(0.5, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.18);
    n.connect(f);
    f.connect(g);
    g.connect(a.master);
    n.start(t, Math.random() * 0.5, 0.2);
    const o = c.createOscillator();
    const og = c.createGain();
    o.type = 'triangle';
    o.frequency.setValueAtTime(210, t);
    o.frequency.exponentialRampToValueAtTime(150, t + 0.08);
    og.gain.setValueAtTime(0.35, t);
    og.gain.exponentialRampToValueAtTime(0.0001, t + 0.1);
    o.connect(og);
    og.connect(a.master);
    o.start(t);
    o.stop(t + 0.12);
  }

  hat(t, len, vol) {
    const a = this.audio;
    const c = a.ctx;
    const n = c.createBufferSource();
    n.buffer = a.noise;
    const f = c.createBiquadFilter();
    f.type = 'highpass';
    f.frequency.value = 7500;
    const g = c.createGain();
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + len);
    n.connect(f);
    f.connect(g);
    g.connect(a.master);
    n.start(t, Math.random() * 0.5, len + 0.02);
  }

  bass(t, hz, len) {
    const a = this.audio;
    const c = a.ctx;
    const sub = c.createOscillator();
    const saw = c.createOscillator();
    const f = c.createBiquadFilter();
    const sawGain = c.createGain();
    const g = c.createGain();
    sub.type = 'sine';
    sub.frequency.value = hz;
    saw.type = 'sawtooth';
    saw.frequency.value = hz * 2;
    f.type = 'lowpass';
    f.Q.value = 4;
    f.frequency.setValueAtTime(900, t);
    f.frequency.exponentialRampToValueAtTime(220, t + len);
    sawGain.gain.value = 0.3;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.55, t + 0.012);
    g.gain.setValueAtTime(0.55, t + len * 0.6);
    g.gain.exponentialRampToValueAtTime(0.0001, t + len);
    sub.connect(g);
    saw.connect(f);
    f.connect(sawGain);
    sawGain.connect(g);
    g.connect(a.master);
    sub.start(t);
    saw.start(t);
    sub.stop(t + len + 0.02);
    saw.stop(t + len + 0.02);
  }

  pluck(t, hz, vel) {
    const a = this.audio;
    const c = a.ctx;
    const o = c.createOscillator();
    const f = c.createBiquadFilter();
    const g = c.createGain();
    o.type = 'sawtooth';
    o.frequency.value = hz * 2;
    f.type = 'lowpass';
    f.Q.value = 6;
    f.frequency.setValueAtTime(5200, t);
    f.frequency.exponentialRampToValueAtTime(700, t + 0.22);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.16 * vel, t + 0.006);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.3);
    o.connect(f);
    f.connect(g);
    g.connect(a.master);
    g.connect(a.delay);
    o.start(t);
    o.stop(t + 0.32);
  }

  pad(t, notes) {
    const a = this.audio;
    const c = a.ctx;
    const f = c.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = 1500;
    f.Q.value = 0.5;
    const g = c.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.05, t + 0.5);
    g.gain.setValueAtTime(0.05, t + 1.9);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 2.5);
    f.connect(g);
    g.connect(a.master);
    for (let i = 0; i < 3; i++) {
      const o = c.createOscillator();
      o.type = 'sawtooth';
      o.frequency.value = notes[i];
      o.detune.value = (i - 1) * 7;
      o.connect(f);
      o.start(t);
      o.stop(t + 2.55);
    }
  }
}

const bnmv = new Component();
bnmv.rootEl = document.querySelector('.bnmv-root');
document.querySelectorAll('[data-act]').forEach((el) => {
  el.addEventListener('click', () => {
    const fn = bnmv.renderVals()[el.getAttribute('data-act')];
    if (typeof fn === 'function') { fn(); }
  });
});
bnmv.render();
bnmv.componentDidMount();

/* The gallery deliberately lists the repository directory instead of naming images here.
   Put any supported image file in Website/assets/screenshots and it becomes a slide automatically. */
(() => {
  const gallery = document.querySelector('[data-github-path]');
  if (!gallery) { return; }

  const track = gallery.querySelector('[data-screenshot-track]');
  const status = gallery.querySelector('[data-screenshot-status]');
  const previous = gallery.querySelector('[data-screenshot-prev]');
  const next = gallery.querySelector('[data-screenshot-next]');
  const current = gallery.querySelector('[data-screenshot-current]');
  const total = gallery.querySelector('[data-screenshot-total]');
  const dots = gallery.querySelector('[data-screenshot-dots]');
  const { githubOwner: owner, githubRepo: repo, githubRef: ref, githubPath: path } = gallery.dataset;
  const imageFile = /\.(avif|gif|jpe?g|png|webp)$/i;
  let slides = [];
  let active = 0;
  let autoplayTimer = null;
  let autoplayDirection = 1;
  let galleryVisible = false;
  let userControlled = false;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  const stopAutoplay = () => {
    if (autoplayTimer !== null) {
      window.clearInterval(autoplayTimer);
      autoplayTimer = null;
    }
  };

  const takeControl = () => {
    userControlled = true;
    stopAutoplay();
  };

  const startAutoplay = () => {
    if (userControlled || !galleryVisible || autoplayTimer !== null || slides.length < 2 || reducedMotion.matches) { return; }
    autoplayTimer = window.setInterval(() => {
      if (document.hidden) { return; }
      if (active + autoplayDirection < 0 || active + autoplayDirection >= slides.length) {
        autoplayDirection *= -1;
      }
      moveTo(active + autoplayDirection);
    }, 4000);
  };

  if ('IntersectionObserver' in window) {
    new IntersectionObserver((entries) => {
      galleryVisible = entries.some((entry) => entry.isIntersecting);
      if (galleryVisible) { startAutoplay(); }
      else { stopAutoplay(); }
    }).observe(gallery);
  } else {
    galleryVisible = true;
  }

  const setActive = (index) => {
    if (!slides.length) { return; }
    active = (index + slides.length) % slides.length;
    current.textContent = String(active + 1);
    dots.querySelectorAll('button').forEach((dot, dotIndex) => {
      dot.setAttribute('aria-current', dotIndex === active ? 'true' : 'false');
    });
  };

  const updateActive = () => {
    if (!slides.length) { return; }
    let nearest = 0;
    let distance = Infinity;
    slides.forEach((slide, index) => {
      /* Slides snap to their leading edge; using the viewport centre made the
         desktop controls target a different visible card than the one selected. */
      const nextDistance = Math.abs(slide.offsetLeft - track.scrollLeft);
      if (nextDistance < distance) { distance = nextDistance; nearest = index; }
    });
    setActive(nearest);
  };

  const moveTo = (index) => {
    if (!slides.length) { return; }
    setActive(index);
    /* scrollIntoView reliably targets the scroll-snap container in desktop browsers. */
    slides[active].scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' });
  };

  const render = (files) => {
    track.textContent = '';
    const fragment = document.createDocumentFragment();
    files.forEach((file, index) => {
      const figure = document.createElement('figure');
      figure.className = 'bn-carousel-slide';
      const image = new Image();
      image.src = `https://raw.githubusercontent.com/${owner}/${repo}/${ref}/${file.path.split('/').map(encodeURIComponent).join('/')}`;
      image.alt = `BNMV app screenshot ${index + 1}`;
      image.decoding = 'async';
      if (index === 0) { image.fetchPriority = 'high'; } else { image.loading = 'lazy'; }
      figure.appendChild(image);
      fragment.appendChild(figure);
    });
    track.appendChild(fragment);
    slides = Array.from(track.children);
    total.textContent = String(slides.length);
    setActive(0);
    previous.disabled = false;
    next.disabled = false;
    dots.textContent = '';
    slides.forEach((_, index) => {
      const dot = document.createElement('button');
      dot.type = 'button';
      dot.className = 'bn-carousel-dot';
      dot.setAttribute('aria-label', `Go to screenshot ${index + 1}`);
      dot.setAttribute('aria-current', index === 0 ? 'true' : 'false');
      dot.addEventListener('click', () => {
        takeControl();
        moveTo(index);
      });
      dots.appendChild(dot);
    });
    track.setAttribute('aria-busy', 'false');
    startAutoplay();
  };

  previous.addEventListener('click', () => {
    takeControl();
    moveTo(active - 1);
  });
  next.addEventListener('click', () => {
    takeControl();
    moveTo(active + 1);
  });
  track.addEventListener('pointerdown', takeControl, { passive: true });
  track.addEventListener('wheel', (event) => {
    takeControl();
    /* A horizontally-scrollable element has no vertical range. Some touchpads
       and browsers still keep vertical wheel input on it, so forward that input
       to the document instead of leaving the page stuck. */
    if (event.ctrlKey || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) { return; }
    const unit = event.deltaMode === WheelEvent.DOM_DELTA_LINE
      ? parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
      : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? window.innerHeight : 1;
    event.preventDefault();
    window.scrollBy({ top: event.deltaY * unit, behavior: 'auto' });
  }, { passive: false });
  track.addEventListener('scroll', () => requestAnimationFrame(updateActive), { passive: true });
  track.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowLeft') { event.preventDefault(); takeControl(); moveTo(active - 1); }
    if (event.key === 'ArrowRight') { event.preventDefault(); takeControl(); moveTo(active + 1); }
  });

  fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${path}?ref=${encodeURIComponent(ref)}`, { headers: { Accept: 'application/vnd.github+json' } })
    .then((response) => {
      if (!response.ok) { throw new Error(`GitHub returned ${response.status}`); }
      return response.json();
    })
    .then((entries) => {
      const files = entries.filter((entry) => entry.type === 'file' && imageFile.test(entry.name)).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
      if (!files.length) { throw new Error('No screenshots found'); }
      render(files);
    })
    .catch(() => {
      track.setAttribute('aria-busy', 'false');
      status.textContent = 'Screenshots are temporarily unavailable. Please try again shortly.';
    });
})();
