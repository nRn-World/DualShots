/* ==========================================================================
   DuelShots — interactive web preview
   Mirrors the native Android app (Kotlin + Compose + CameraX) in the browser:
   same screens, same design tokens, same three recording modes.

   The real app lives in app/src/main/java/com/nrworld/duelshot/.
   ========================================================================== */
'use strict';

/* ------------------------------------------------------------------ model */

const MODE_ORDER = ['DUAL_VIEW', 'SEPARATE', 'BOTH'];

const MODES = {
  DUAL_VIEW: { title: 'Dual View', subtitle: 'Both cameras in one video', icon: '▢' },
  SEPARATE: { title: 'Separate Videos', subtitle: 'Two files, one for each', icon: '▯' },
  BOTH: { title: 'Both Options', subtitle: 'Combined + two separate files', icon: '⇄' },
};

const ASPECT_ORDER = ['R_9_16', 'R_16_9', 'R_1_1', 'R_4_3'];

const ASPECTS = {
  R_9_16: { label: '9:16 Portrait', w: 9, h: 16 },
  R_16_9: { label: '16:9 Landscape', w: 16, h: 9 },
  R_1_1: { label: '1:1 Square', w: 1, h: 1 },
  R_4_3: { label: '4:3', w: 4, h: 3 },
};

const QUALITY_ORDER = ['HD720', 'FHD1080', 'UHD4K'];

const QUALITIES = {
  HD720: { label: '720p', seg: '720p', premium: false, long: 1280 },
  FHD1080: { label: '1080p Full HD', seg: '1080p', premium: false, long: 1920 },
  UHD4K: { label: '4K Ultra HD', seg: '4K★', premium: true, long: 3840 },
};

const CODEC_ORDER = ['MP4', 'HEVC'];

const CODECS = {
  MP4: { label: 'MP4', seg: 'MP4', premium: false },
  HEVC: { label: 'HEVC', seg: 'HEVC★', premium: true },
};

const STORE_KEY = 'duelshot_prefs';

const state = {
  tab: 'HOME',
  record: false,
  mode: 'SEPARATE',
  frontAspect: 'R_9_16',
  backAspect: 'R_16_9',
  quality: 'FHD1080',
  codec: 'MP4',
  premium: false,
  clips: [],
};

/* --------------------------------------------------------------- elements */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const screenEl = $('#screen');
const tabbarEl = $('#tabbar');
const sinkEl = $('#media-sink');
const modalRoot = $('#modal-root');
const deviceEl = $('#device');

/* ------------------------------------------------------------------ icons */

const ICONS = {
  home: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round" stroke-linecap="round"><path d="M3 10.6 12 3.2l9 7.4"/><path d="M5.4 9.6V20.8h13.2V9.6"/></svg>`,
  gallery: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="4"/><path d="M10.4 8.9v6.2l5.4-3.1z" fill="currentColor" stroke="none"/></svg>`,
  settings: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"><circle cx="12" cy="12" r="3.1"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.56V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.56 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.65 8.9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.56V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 9v.09a1.7 1.7 0 0 0 1.56 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z"/></svg>`,
  close: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/></svg>`,
  switch: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M20.2 12a8.2 8.2 0 1 1-2.4-5.8"/><path d="M20.6 4.6v5.2h-5.2"/></svg>`,
  play: `<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M8 5.5v13l11-6.5z"/></svg>`,
  trash: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 7h14M10 7V5.5h4V7M6.5 7l.8 12.2A1.8 1.8 0 0 0 9.1 21h5.8a1.8 1.8 0 0 0 1.8-1.8L17.5 7"/></svg>`,
};

function logoMark(size = 44) {
  return `
  <svg class="logomark" viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true">
    <defs>
      <linearGradient id="duelGrad" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#22D3EE"/>
        <stop offset=".38" stop-color="#2E7DFF"/>
        <stop offset=".7" stop-color="#7C3AED"/>
        <stop offset="1" stop-color="#E94FD0"/>
      </linearGradient>
    </defs>
    <ellipse cx="50" cy="50" rx="44" ry="29" fill="none" stroke="url(#duelGrad)" stroke-width="3"
             stroke-linecap="round" stroke-dasharray="205 40" stroke-dashoffset="-13"/>
    <circle cx="30" cy="50" r="23" fill="#05070D"/>
    <circle cx="30" cy="50" r="17.5" fill="none" stroke="url(#duelGrad)" stroke-width="3.4"/>
    <circle cx="30" cy="50" r="15" fill="#0B1024"/>
    <circle cx="26.5" cy="46.5" r="6" fill="#22D3EE"/>
    <circle cx="70" cy="50" r="23" fill="#05070D"/>
    <circle cx="70" cy="50" r="17.5" fill="none" stroke="url(#duelGrad)" stroke-width="3.4"/>
    <circle cx="70" cy="50" r="15" fill="#0B1024"/>
    <circle cx="66.5" cy="46.5" r="6" fill="#E94FD0"/>
  </svg>`;
}

/* ------------------------------------------------------------- utilities */

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function stampNow(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function hms(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const p = (n) => String(n).padStart(2, '0');
  return `${p(Math.floor(total / 3600))}:${p(Math.floor((total % 3600) / 60))}:${p(total % 60)}`;
}

function mb(bytes) {
  return Math.max(1, Math.round(bytes / (1024 * 1024)));
}

function savePrefs() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({
      mode: state.mode,
      frontAspect: state.frontAspect,
      backAspect: state.backAspect,
      quality: state.quality,
      codec: state.codec,
      premium: state.premium,
    }));
  } catch (_) { /* private mode – ignore */ }
}

function loadPrefs() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return;
    const p = JSON.parse(raw);
    if (MODES[p.mode]) state.mode = p.mode;
    if (ASPECTS[p.frontAspect]) state.frontAspect = p.frontAspect;
    if (ASPECTS[p.backAspect]) state.backAspect = p.backAspect;
    if (QUALITIES[p.quality]) state.quality = p.quality;
    if (CODECS[p.codec]) state.codec = p.codec;
    state.premium = !!p.premium;
  } catch (_) { /* ignore */ }
}

let toastTimer = 0;
function toast(msg) {
  $$('.toast', deviceEl).forEach((t) => t.remove());
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  deviceEl.appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 2600);
}

/* --------------------------------------------------------------- overlays */

function closeModal() {
  modalRoot.innerHTML = '';
}

function openModal(html, bind) {
  modalRoot.innerHTML = `<div class="modal">${html}</div>`;
  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeModal(); });
  $$('[data-close]', modalRoot).forEach((b) => b.addEventListener('click', closeModal));
  bind && bind();
}

function showPremium(feature = 'DuelShots Premium') {
  openModal(`
    <div class="modal-card">
      <h3 class="modal-title">♛ ${esc(feature)}</h3>
      <p class="modal-body">This is a Premium feature. Upgrade for 4K, 60fps, HEVC, per-camera format
      control and watermark-free export.</p>
      <div class="modal-actions">
        <button class="modal-btn" data-close type="button">Later</button>
        <button class="modal-btn primary" data-upgrade type="button">Upgrade</button>
      </div>
    </div>`, () => {
    $('[data-upgrade]', modalRoot).addEventListener('click', () => {
      state.premium = true;
      savePrefs();
      closeModal();
      toast('Premium unlocked — mock purchase, no billing');
      if (state.record) refreshRecord(); else renderTab();
    });
  });
}

function showAbout() {
  const wrap = document.createElement('div');
  wrap.className = 'about-modal';
  wrap.innerHTML = `
    <div class="about-card">
      <h2>DuelShots · web preview</h2>
      <p>This is a browser replica of the native Android app in this repository. The screens,
      design tokens, recording modes and premium gating come straight from the Kotlin/Compose
      source in <code>app/src/main/java/com/nrworld/duelshot/</code>.</p>

      <h3>What actually works here</h3>
      <ul>
        <li>All three recording modes: composed PiP, two separate files, or both.</li>
        <li>Recordings are encoded live in the browser and land in the Gallery tab, playable and downloadable.</li>
        <li>Settings persist in <code>localStorage</code> (the app uses SharedPreferences).</li>
        <li>4K / HEVC are gated behind the mock premium dialog — same as the app's <code>grantPremium()</code>.</li>
      </ul>

      <h3>Cameras</h3>
      <p>Browsers usually allow only one camera stream at a time. When both the front and the back
      camera can be opened at once you get a real dual feed; otherwise the preview falls back to
      single-camera mode — exactly like the Android fallback. If camera access is blocked
      (permissions, iframe policy or plain HTTP) an animated demo feed is used so the recording
      pipeline still works.</p>

      <h3>The real app</h3>
      <p>Build the APK locally with JDK 17/21 and the Android SDK:</p>
      <p><code>./gradlew :app:assembleDebug</code></p>
      <p>True concurrent dual-camera capture needs a device whose camera HAL supports it.</p>

      <div class="close-row"><button class="about-cta" type="button">Got it</button></div>
    </div>`;
  document.body.appendChild(wrap);
  const close = () => wrap.remove();
  wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
  $('.about-cta', wrap).addEventListener('click', close);
}

/* =====================================================================
   CAMERA
   ===================================================================== */

const cam = {
  started: false,
  ready: false,
  demo: false,
  singleFallback: false,
  feeds: { back: null, front: null },
  active: 'back',
  demoRaf: 0,
};

const videoEl = () => {
  const v = document.createElement('video');
  v.autoplay = true;
  v.muted = true;
  v.playsInline = true;
  v.setAttribute('playsinline', '');
  v.setAttribute('webkit-playsinline', '');
  return v;
};

const deviceIdOf = (stream) => {
  const t = stream && stream.getVideoTracks()[0];
  return t ? (t.getSettings().deviceId || '') : '';
};

async function openStream(facing, deviceId) {
  const video = deviceId
    ? { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1080 } }
    : { facingMode: { ideal: facing }, width: { ideal: 1920 }, height: { ideal: 1080 } };
  return navigator.mediaDevices.getUserMedia({ video, audio: false });
}

function camErrorMessage(err) {
  const name = (err && err.name) || '';
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'Camera permission blocked — demo feed active.';
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'No camera found — demo feed active.';
  if (name === 'NotReadableError') return 'Camera is busy in another app — demo feed active.';
  return 'Camera unavailable — demo feed active.';
}

/* animated synthetic feed so the recorder always has something to draw */
function drawDemo(canvas, label, hue, t) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;

  const bg = ctx.createLinearGradient(0, 0, w, h);
  bg.addColorStop(0, '#0b1024');
  bg.addColorStop(0.5, '#131b3a');
  bg.addColorStop(1, '#070a12');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  for (let i = 0; i < 4; i++) {
    const x = w * (0.5 + 0.32 * Math.sin(t / 2600 + i * 1.7 + hue));
    const y = h * (0.5 + 0.3 * Math.cos(t / 3100 + i * 2.3 + hue));
    const r = Math.min(w, h) * (0.2 + 0.06 * Math.sin(t / 1800 + i));
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `hsla(${(188 + i * 42 + hue * 40) % 360}, 88%, 62%, .5)`);
    g.addColorStop(1, 'hsla(220, 88%, 55%, 0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  const step = Math.max(40, Math.round(w / 9));
  const off = (t / 45) % step;
  ctx.strokeStyle = 'rgba(255,255,255,.05)';
  ctx.lineWidth = Math.max(1, w / 720);
  for (let x = -off; x < w; x += step) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
  }
  for (let y = -off; y < h; y += step) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
  }

  // focus brackets
  const m = w * 0.09;
  const len = w * 0.1;
  ctx.strokeStyle = 'rgba(255,255,255,.4)';
  ctx.lineWidth = Math.max(2, w / 260);
  [[m, m, 1, 1], [w - m, m, -1, 1], [m, h - m, 1, -1], [w - m, h - m, -1, -1]].forEach(([x, y, dx, dy]) => {
    ctx.beginPath();
    ctx.moveTo(x + dx * len, y);
    ctx.lineTo(x, y);
    ctx.lineTo(x, y + dy * len);
    ctx.stroke();
  });

  ctx.fillStyle = 'rgba(255,255,255,.9)';
  ctx.font = `700 ${Math.round(w * 0.105)}px system-ui, sans-serif`;
  ctx.textAlign = 'center';
  ctx.fillText(label, w / 2, h / 2 - w * 0.02);

  ctx.fillStyle = 'rgba(255,255,255,.45)';
  ctx.font = `600 ${Math.round(w * 0.036)}px ui-monospace, monospace`;
  ctx.fillText('SIMULATED FEED', w / 2, h / 2 + w * 0.05);
  ctx.fillText(new Date().toLocaleTimeString(), w / 2, h / 2 + w * 0.11);
}

function startDemo() {
  cam.demo = true;
  cam.ready = true;
  cam.singleFallback = false;
  const back = document.createElement('canvas');
  back.width = 720; back.height = 1280;
  const front = document.createElement('canvas');
  front.width = 720; front.height = 1280;
  cam.feeds.back = { el: back, kind: 'demo', mirror: false, label: 'BACK' };
  cam.feeds.front = { el: front, kind: 'demo', mirror: false, label: 'FRONT' };
  sinkEl.appendChild(back);
  sinkEl.appendChild(front);

  const loop = (t) => {
    drawDemo(back, 'BACK', 0, t);
    drawDemo(front, 'FRONT', 0.55, t);
    cam.demoRaf = requestAnimationFrame(loop);
  };
  if (!cam.demoRaf) cam.demoRaf = requestAnimationFrame(loop);
}

function enableDemo(message) {
  if (!cam.demo) startDemo();
  cam.ready = true;
  refreshRecord(message);
}

async function startCameras() {
  cam.started = true;
  recordMessage = '';
  refreshRecord();

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    enableDemo('Camera API unavailable (needs HTTPS) — demo feed active.');
    return;
  }

  let back = null;
  let front = null;

  try {
    back = await openStream('environment');
  } catch (err) {
    enableDemo(camErrorMessage(err));
    return;
  }

  const backId = deviceIdOf(back);
  try {
    front = await openStream('user');
  } catch (_) {
    front = null;
  }

  // Some browsers return the very same camera for both facing modes.
  if (front && backId && deviceIdOf(front) === backId) {
    front.getTracks().forEach((t) => t.stop());
    front = null;
    try {
      const devs = (await navigator.mediaDevices.enumerateDevices())
        .filter((d) => d.kind === 'videoinput' && d.deviceId && d.deviceId !== backId);
      if (devs.length) front = await openStream(null, devs[0].deviceId);
    } catch (_) { front = null; }
  }

  const mk = (stream, mirror) => {
    const v = videoEl();
    v.srcObject = stream;
    v.play().catch(() => {});
    sinkEl.appendChild(v);
    return { el: v, kind: 'camera', mirror };
  };

  if (back) cam.feeds.back = mk(back, false);
  if (front) cam.feeds.front = mk(front, true);

  cam.ready = true;
  cam.singleFallback = !(back && front);
  cam.active = back ? 'back' : 'front';
  refreshRecord();
}

async function switchCamera() {
  if (cam.demo) { toast('Demo feed has no physical cameras'); return; }
  const other = cam.active === 'back' ? 'front' : 'back';
  if (cam.feeds[other]) {
    cam.active = other;
    cam.singleFallback = true;
    refreshRecord();
    return;
  }
  // Only one physical camera: reopen it with the other facing mode.
  const current = cam.feeds[cam.active];
  if (current && current.el.srcObject) current.el.srcObject.getTracks().forEach((t) => t.stop());
  try {
    const stream = await openStream(other === 'back' ? 'environment' : 'user');
    const v = videoEl();
    v.srcObject = stream;
    v.play().catch(() => {});
    cam.feeds[other] = { el: v, kind: 'camera', mirror: other === 'front' };
    sinkEl.appendChild(v);
    delete cam.feeds[cam.active];
    cam.active = other;
    cam.singleFallback = true;
    refreshRecord();
  } catch (_) {
    toast('Could not switch camera');
  }
}

/* =====================================================================
   RECORDING
   ===================================================================== */

const rec = {
  active: false,
  starting: false,
  startedAt: 0,
  timer: 0,
  raf: 0,
  outs: [],
  audio: null,
  stamp: '',
};

const recordDom = { shell: null, stage: null, status: null, saved: null, timer: null, btn: null, wrap: null };
let mountedKey = null;
let recordMessage = '';
let recordSaved = [];

function outputSize(aspectKey, qualityKey) {
  const a = ASPECTS[aspectKey];
  const cap = Math.min(QUALITIES[qualityKey].long, 1600);
  const ratio = a.w / a.h;
  let width;
  let height;
  if (ratio >= 1) { width = cap; height = Math.round(cap / ratio); }
  else { height = cap; width = Math.round(cap * ratio); }
  return { width: width - (width % 2), height: height - (height % 2) };
}

function pickMime() {
  const list = [
    'video/mp4;codecs=avc1.42E01E',
    'video/mp4',
    'video/webm;codecs=vp9',
    'video/webm;codecs=vp8',
    'video/webm',
  ];
  for (const m of list) {
    if (window.MediaRecorder && MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(m)) return m;
  }
  return '';
}

function drawCover(ctx, src, w, h, mirror) {
  const sw = src.videoWidth || src.width;
  const sh = src.videoHeight || src.height;
  if (!sw || !sh) return;
  const scale = Math.max(w / sw, h / sh);
  const dw = sw * scale;
  const dh = sh * scale;
  ctx.save();
  if (mirror) {
    ctx.translate(w, 0);
    ctx.scale(-1, 1);
  }
  ctx.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh);
  ctx.restore();
}

function roundRect(ctx, x, y, w, h, r) {
  if (ctx.roundRect) {
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, r);
    return;
  }
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function makeOutput(key, name, aspectKey, withAudio, existingStream) {
  let canvas = null;
  let ctx = null;
  let stream = existingStream || null;

  if (!stream) {
    const { width, height } = outputSize(aspectKey, state.quality);
    canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    ctx = canvas.getContext('2d');
    try {
      stream = canvas.captureStream(30);
    } catch (err) {
      return null;
    }
  }
  if (withAudio && rec.audio) {
    rec.audio.getAudioTracks().forEach((t) => stream.addTrack(t));
  }
  const mime = pickMime();
  let recorder;
  try {
    recorder = new MediaRecorder(stream, mime
      ? { mimeType: mime, videoBitsPerSecond: state.quality === 'UHD4K' ? 12_000_000 : 6_000_000 }
      : undefined);
  } catch (err) {
    try { recorder = new MediaRecorder(stream); } catch (e2) { return null; }
  }
  const chunks = [];
  recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
  const done = new Promise((resolve) => { recorder.onstop = resolve; });
  recorder.start(200);

  return { key, name, canvas, ctx, recorder, chunks, done, mime, stream, ownsStream: !existingStream };
}

function drawOutput(o) {
  if (!o.canvas) return;
  const ctx = o.ctx;
  const { width: w, height: h } = o.canvas;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);

  if (o.key === 'composed') {
    const back = cam.feeds.back;
    const front = cam.feeds.front;
    if (back) drawCover(ctx, back.el, w, h, back.mirror);
    if (front) {
      const pipW = Math.round(w * 0.34);
      const fa = ASPECTS[state.frontAspect];
      const pipH = Math.round(pipW * (fa.h / fa.w));
      const margin = Math.round(w * 0.04);
      const x = w - pipW - margin;
      const y = margin;
      ctx.save();
      roundRect(ctx, x, y, pipW, pipH, Math.round(w * 0.02));
      ctx.clip();
      ctx.fillStyle = '#000';
      ctx.fillRect(x, y, pipW, pipH);
      drawCover(ctx, front.el, pipW, pipH, front.mirror);
      ctx.restore();
      ctx.strokeStyle = 'rgba(255,255,255,.6)';
      ctx.lineWidth = Math.max(2, Math.round(w / 320));
      roundRect(ctx, x, y, pipW, pipH, Math.round(w * 0.02));
      ctx.stroke();
    }
    return;
  }

  const feed = cam.feeds[o.key];
  if (feed) drawCover(ctx, feed.el, w, h, feed.mirror);
}

async function startRecording() {
  if (rec.active || rec.starting) return;
  rec.starting = true;
  if (!cam.ready) { rec.starting = false; setRecordMessage('Cameras are not ready yet.'); return; }

  recordSaved = [];
  setRecordMessage('');

  // microphone (optional – recording works silently if blocked)
  rec.audio = null;
  try {
    rec.audio = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (_) { rec.audio = null; }

  const stamp = stampNow();
  rec.stamp = stamp;
  const single = cam.singleFallback || !cam.feeds.back || !cam.feeds.front;
  const outs = [];

  if (single) {
    const key = cam.active;
    const o = makeOutput(key, `duelshot_${key}_${stamp}`, key === 'back' ? state.backAspect : state.frontAspect, true);
    if (o) outs.push(o);
  } else if (state.mode === 'DUAL_VIEW') {
    const o = makeOutput('composed', `duelshot_combined_${stamp}`, state.backAspect, true);
    if (o) outs.push(o);
  } else if (state.mode === 'SEPARATE') {
    const b = makeOutput('back', `duelshot_back_${stamp}`, state.backAspect, true);
    const f = makeOutput('front', `duelshot_front_${stamp}`, state.frontAspect, false);
    if (b) outs.push(b);
    if (f) outs.push(f);
  } else {
    const c = makeOutput('composed', `duelshot_combined_${stamp}`, state.backAspect, true);
    const b = makeOutput('back', `duelshot_back_${stamp}`, state.backAspect, false);
    const f = makeOutput('front', `duelshot_front_${stamp}`, state.frontAspect, false);
    [c, b, f].forEach((o) => o && outs.push(o));
  }

  if (!outs.length) {
    // No canvas capture support (older Safari): record the camera stream itself.
    const feed = cam.feeds[cam.active] || cam.feeds.back || cam.feeds.front;
    const src = feed && feed.el && feed.el.srcObject;
    if (src instanceof MediaStream) {
      const o = makeOutput(cam.active, `duelshot_${cam.active}_${stamp}`,
        cam.active === 'back' ? state.backAspect : state.frontAspect, false, src);
      if (o) outs.push(o);
    }
  }

  if (!outs.length) {
    rec.starting = false;
    setRecordMessage('Recording did not start: MediaRecorder unavailable in this browser.');
    return;
  }

  rec.outs = outs;
  rec.active = true;
  rec.starting = false;
  rec.startedAt = Date.now();

  let lastDraw = 0;
  const loop = (now) => {
    if (!rec.active) return;
    if (now - lastDraw >= 30) { // ~30 fps composite, matches captureStream(30)
      lastDraw = now;
      rec.outs.forEach(drawOutput);
    }
    rec.raf = requestAnimationFrame(loop);
  };
  rec.raf = requestAnimationFrame(loop);
  rec.timer = setInterval(() => {
    if (recordDom.timer && rec.active) recordDom.timer.textContent = hms(Date.now() - rec.startedAt);
  }, 250);

  if (recordDom.wrap) recordDom.wrap.classList.add('recording');
  if (recordDom.btn) recordDom.btn.classList.add('on');
  deviceEl.querySelector('.flash')?.classList.add('on');
  setTimeout(() => deviceEl.querySelector('.flash')?.classList.remove('on'), 460);

  setRecordMessage(`Recording – ${MODES[state.mode].title.toLowerCase()}${single ? ' (single camera)' : ''}…`);
}

async function stopRecording() {
  if (!rec.active) return;
  rec.active = false;
  rec.starting = false;
  cancelAnimationFrame(rec.raf);
  clearInterval(rec.timer);
  rec.raf = 0;

  const outs = rec.outs.slice();
  rec.outs = [];
  outs.forEach((o) => { try { o.recorder.stop(); } catch (_) { /* already stopped */ } });
  await Promise.all(outs.map((o) => o.done));
  outs.forEach((o) => { if (o.ownsStream) o.stream.getTracks().forEach((t) => t.stop()); });

  if (rec.audio) {
    rec.audio.getTracks().forEach((t) => t.stop());
    rec.audio = null;
  }

  const duration = hms(Date.now() - rec.startedAt);
  const newClips = outs.map((o) => {
    const type = o.recorder.mimeType || o.mime || 'video/webm';
    const ext = type.includes('mp4') ? 'mp4' : 'webm';
    const blob = new Blob(o.chunks, { type });
    return {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      name: `${o.name}.${ext}`,
      sizeMb: mb(blob.size),
      bytes: blob.size,
      url: URL.createObjectURL(blob),
      mime: type,
      duration,
      when: new Date(),
      mode: state.mode,
    };
  });

  state.clips.unshift(...newClips);
  recordSaved = newClips.map((c) => c.name);

  if (recordDom.wrap) recordDom.wrap.classList.remove('recording');
  if (recordDom.btn) recordDom.btn.classList.remove('on');
  if (recordDom.timer) recordDom.timer.textContent = '00:00:00';

  setRecordMessage(`Stopped. Saved to Gallery → Movies/DuelShots`);
  refreshRecord();
}

function setRecordMessage(msg) {
  recordMessage = msg;
  if (recordDom.status) {
    recordDom.status.innerHTML = esc(msg) + (recordSaved.length
      ? `<span class="saved">Saved: ${esc(recordSaved.join(', '))}</span>`
      : '');
  }
}

/* =====================================================================
   SCREEN: RECORD
   ===================================================================== */

function buildRecordShell() {
  screenEl.innerHTML = `
    <div class="record" id="record-wrap">
      <div class="record-top">
        <button class="icon-chip" id="rec-close" type="button" aria-label="Close">${ICONS.close}</button>
        <div class="timer-pill"><span class="timer-dot"></span><span id="rec-timer">00:00:00</span></div>
        <span style="flex:1"></span>
        <button class="icon-chip" id="rec-switch" type="button" aria-label="Switch camera">${ICONS.switch}</button>
      </div>
      <div class="record-stage" id="record-stage"></div>
      <div class="record-status" id="record-status"></div>
      <div class="record-actions">
        <button class="rec-btn" id="rec-btn" type="button" aria-label="Record"><i></i></button>
      </div>
    </div>`;

  recordDom.wrap = $('#record-wrap');
  recordDom.stage = $('#record-stage');
  recordDom.status = $('#record-status');
  recordDom.timer = $('#rec-timer');
  recordDom.btn = $('#rec-btn');

  $('#rec-close').addEventListener('click', async () => {
    if (rec.active) await stopRecording();
    closeRecord();
  });
  $('#rec-switch').addEventListener('click', switchCamera);
  $('#rec-btn').addEventListener('click', () => {
    if (rec.active) stopRecording();
    else startRecording();
  });
}

function makeTile(label, ratioLabel) {
  const tile = document.createElement('div');
  tile.className = 'feed';
  if (label) {
    const tag = document.createElement('span');
    tag.className = 'tag tl';
    tag.textContent = label;
    tile.appendChild(tag);
  }
  if (ratioLabel) {
    const tag = document.createElement('span');
    tag.className = 'tag bl';
    tag.textContent = ratioLabel;
    tile.appendChild(tag);
  }
  return tile;
}

function placeholder(text) {
  const d = document.createElement('div');
  d.className = 'feed-placeholder';
  d.innerHTML = `<span class="big">◎</span><span>${esc(text)}</span>`;
  return d;
}

function mountFeeds() {
  const stage = recordDom.stage;
  if (!stage) return;
  stage.innerHTML = '';
  mountedKey = layoutKey();

  if (!cam.ready) {
    stage.appendChild(makeTile('SETUP', null)).appendChild(placeholder('Starting cameras…'));
    return;
  }

  const hasBoth = !!(cam.feeds.back && cam.feeds.front);
  const single = cam.singleFallback || !hasBoth;

  if (single) {
    const key = cam.feeds[cam.active] ? cam.active : (cam.feeds.back ? 'back' : 'front');
    const feed = cam.feeds[key];
    const ratioLabel = ASPECTS[key === 'back' ? state.backAspect : state.frontAspect].label;
    const tile = makeTile('CAMERA', ratioLabel);
    stage.appendChild(tile);
    if (feed) {
      if (feed.mirror) tile.classList.add('mirror');
      tile.appendChild(feed.el);
    } else {
      tile.appendChild(placeholder('No camera'));
    }
    return;
  }

  if (state.mode === 'DUAL_VIEW') {
    const tile = makeTile(null, null);
    tile.style.flex = '1 1 100%';
    stage.appendChild(tile);
    const back = cam.feeds.back;
    const front = cam.feeds.front;
    if (back) {
      if (back.mirror) tile.classList.add('mirror');
      tile.appendChild(back.el);
    }
    if (front) {
      const pip = document.createElement('div');
      pip.className = 'pip' + (front.mirror ? ' mirror' : '');
      const a = ASPECTS[state.frontAspect];
      pip.style.setProperty('--pip-ratio', `${a.w} / ${a.h}`);
      pip.appendChild(front.el);
      tile.appendChild(pip);
    }
    return;
  }

  const tiles = [
    { key: 'back', label: 'BACK', aspect: state.backAspect },
    { key: 'front', label: 'FRONT', aspect: state.frontAspect },
  ];
  tiles.forEach(({ key, label, aspect }) => {
    const tile = makeTile(label, ASPECTS[aspect].label);
    const feed = cam.feeds[key];
    if (feed) {
      if (feed.mirror) tile.classList.add('mirror');
      tile.appendChild(feed.el);
    } else {
      tile.appendChild(placeholder('Unavailable'));
    }
    stage.appendChild(tile);
  });
}

function layoutKey() {
  return [cam.ready, cam.singleFallback, cam.active, state.mode, state.premium].join('|');
}

function unmountFeeds() {
  Object.values(cam.feeds).forEach((f) => { if (f && f.el) sinkEl.appendChild(f.el); });
}

function recordStatusText() {
  if (rec.active && recordMessage) return recordMessage;
  if (!cam.ready) return 'Starting cameras…';
  if (recordMessage) return recordMessage;
  if (cam.demo) return 'Demo feed active — camera unavailable in this browser.';
  if (cam.singleFallback) return `Single camera (${cam.active.toUpperCase()}) — recording one file.`;
  return `Camera ready — ${MODES[state.mode].title}.`;
}

function refreshRecord(message) {
  if (!state.record || !recordDom.wrap) return;
  if (typeof message === 'string' && message) recordMessage = message;

  if (layoutKey() !== mountedKey) mountFeeds();

  recordDom.status.innerHTML = esc(recordStatusText()) + (recordSaved.length
    ? `<span class="saved">Saved: ${esc(recordSaved.join(', '))}</span>`
    : '');

  if (recordDom.btn) recordDom.btn.disabled = !cam.ready;
  if (recordDom.wrap) recordDom.wrap.classList.toggle('recording', rec.active);
  if (recordDom.btn) recordDom.btn.classList.toggle('on', rec.active);
}

function openRecord() {
  state.record = true;
  document.body.dataset.screen = 'record';
  tabbarEl.hidden = true;
  recordMessage = '';
  recordSaved = [];
  mountedKey = null;
  buildRecordShell();
  mountFeeds();
  refreshRecord();

  if (!cam.started) startCameras();
}

function closeRecord() {
  state.record = false;
  recordDom.wrap = recordDom.stage = recordDom.status = recordDom.timer = recordDom.btn = null;
  mountedKey = null;
  recordMessage = '';
  recordSaved = [];
  unmountFeeds();
  document.body.dataset.screen = state.tab.toLowerCase();
  tabbarEl.hidden = false;
  renderTab(true);
}

/* =====================================================================
   TABS
   ===================================================================== */

function updateTabs() {
  $$('.tab', tabbarEl).forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === state.tab));
}

function modeRowHTML(m) {
  const selected = state.mode === m;
  return `
    <button class="card mode-card ${selected ? 'selected' : ''}" data-mode="${m}" type="button">
      <span class="mode-icon">${MODES[m].icon}</span>
      <span class="mode-text">
        <span class="mode-title">${MODES[m].title}</span>
        <span class="mode-sub">${MODES[m].subtitle}</span>
      </span>
      <span class="check">${selected ? '✓' : ''}</span>
    </button>`;
}

function premiumCardHTML() {
  return `
    <button class="card premium-card" id="premium-card" type="button">
      <span class="premium-crown">♛</span>
      <span class="premium-text">
        <span class="premium-title">Go Premium</span>
        <span class="premium-sub">4K · 60fps · HEVC · custom formats · watermark-free</span>
      </span>
    </button>`;
}

function renderHome() {
  screenEl.innerHTML = `
    <div class="screen-scroll">
      <header class="home-head">
        ${logoMark(44)}
        <div class="wordmark">Duel<span>Shots</span></div>
        <span class="pill ${state.premium ? 'gold' : ''}">${state.premium ? '♛ Premium' : 'Free'}</span>
      </header>

      <p class="tagline">Capture More. From Every Angle.</p>

      <button class="cta" id="start-btn" type="button">
        <span class="cta-emoji">🎥</span>
        <span class="cta-text"><b>Start Recording</b><em>Choose your mode</em></span>
      </button>

      <div class="section-label">Recording Mode</div>
      <div class="mode-list">${MODE_ORDER.map(modeRowHTML).join('')}</div>

      <div class="section-label">Quick Settings</div>
      <button class="card quick" id="quick-btn" type="button">
        <span class="quick-text">
          <span class="quick-title">⚡ ${ASPECTS[state.frontAspect].label} · ${ASPECTS[state.backAspect].label}</span>
          <span class="quick-sub">${QUALITIES[state.quality].label} · ${CODECS[state.codec].label}</span>
        </span>
        <span class="chev">›</span>
      </button>

      ${state.premium ? '' : premiumCardHTML()}
    </div>`;

  $('#start-btn').addEventListener('click', openRecord);
  $('#quick-btn').addEventListener('click', () => switchTab('SETTINGS'));
  $$('[data-mode]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.mode = b.dataset.mode;
    savePrefs();
    renderHome();
  }));
  const pc = $('#premium-card');
  if (pc) pc.addEventListener('click', () => showPremium());
}

function radioRowHTML(m) {
  const selected = state.mode === m;
  return `
    <button class="card radio-row ${selected ? 'selected' : ''}" data-mode="${m}" type="button">
      <span class="radio-text">
        <span class="radio-title">${MODES[m].title}</span>
        <span class="radio-sub">${MODES[m].subtitle}</span>
      </span>
      <span class="radio-dot"><i></i></span>
    </button>`;
}

function chipsHTML(which, selected) {
  return `<div class="chips">${ASPECT_ORDER.map((k) => `
    <button class="chip ${k === selected ? 'selected' : ''}" data-${which}="${k}" type="button">${ASPECTS[k].label}</button>
  `).join('')}</div>`;
}

function segmentedHTML(which, order, defs, selected, premium) {
  return `<div class="segmented">${order.map((k) => `
    <button class="seg ${k === selected ? 'selected' : ''}" data-${which}="${k}" type="button">${defs[k].seg}</button>
  `).join('')}</div>`;
}

function renderSettings() {
  screenEl.innerHTML = `
    <div class="screen-scroll">
      <h1 class="page-title">Settings</h1>

      <div class="section-label">Recording Mode</div>
      ${MODE_ORDER.map(radioRowHTML).join('')}

      <div class="section-label">Camera Formats</div>
      <div class="card">
        <div class="card-title">Front Camera</div>
        ${chipsHTML('front', state.frontAspect)}
      </div>
      <div class="card">
        <div class="card-title">Back Camera</div>
        ${chipsHTML('back', state.backAspect)}
      </div>

      <div class="section-label">Video Quality</div>
      <div class="card">
        <div class="setting-row">
          <span>Resolution</span>
          ${segmentedHTML('quality', QUALITY_ORDER, QUALITIES, state.quality, state.premium)}
        </div>
      </div>

      <div class="section-label">Container / Codec</div>
      <div class="card">
        <div class="setting-row">
          <span>Format</span>
          ${segmentedHTML('codec', CODEC_ORDER, CODECS, state.codec, state.premium)}
        </div>
      </div>

      <div class="card premium-block gold-border">
        <div class="premium-head">♛ Premium</div>
        <div class="premium-list">
          ${['More format options (4K, HEVC)', '60fps & custom bitrate', 'Per-camera aspect control', 'No watermark']
      .map((t) => `<div class="premium-item"><b>✓</b><span>${t}</span></div>`).join('')}
        </div>
        ${state.premium
      ? `<div class="premium-active">Premium active</div>`
      : `<button class="gold-btn" id="gold-btn" type="button">♛ Go Premium</button>`}
      </div>
    </div>`;

  $$('[data-mode]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.mode = b.dataset.mode;
    savePrefs();
    renderSettings();
  }));

  $$('[data-front]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.frontAspect = b.dataset.front;
    savePrefs();
    renderSettings();
  }));
  $$('[data-back]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.backAspect = b.dataset.back;
    savePrefs();
    renderSettings();
  }));

  $$('[data-quality]', screenEl).forEach((b) => b.addEventListener('click', () => {
    const key = b.dataset.quality;
    if (QUALITIES[key].premium && !state.premium) { showPremium(QUALITIES[key].label); return; }
    state.quality = key;
    savePrefs();
    renderSettings();
  }));

  $$('[data-codec]', screenEl).forEach((b) => b.addEventListener('click', () => {
    const key = b.dataset.codec;
    if (CODECS[key].premium && !state.premium) { showPremium(CODECS[key].label); return; }
    state.codec = key;
    savePrefs();
    renderSettings();
  }));

  const gold = $('#gold-btn');
  if (gold) gold.addEventListener('click', () => {
    state.premium = true;
    savePrefs();
    toast('Premium unlocked — mock purchase, no billing');
    renderSettings();
  });
}

function clipRowHTML(c) {
  return `
    <div class="clip" data-clip="${c.id}">
      <div class="clip-thumb">${ICONS.play}</div>
      <div class="clip-text">
        <div class="clip-name">${esc(c.name)}</div>
        <div class="clip-meta">${c.sizeMb} MB · ${c.duration} · ${MODES[c.mode] ? MODES[c.mode].title : 'Video'}</div>
      </div>
      <button class="clip-del" data-del="${c.id}" type="button" aria-label="Delete recording">${ICONS.trash}</button>
    </div>`;
}

function renderGallery() {
  const clips = state.clips;
  screenEl.innerHTML = `
    <div class="screen-scroll">
      <h1 class="page-title">Gallery</h1>
      <p class="gallery-sub">Recordings are saved to Gallery → Movies/DuelShots</p>
      ${clips.length
      ? clips.map(clipRowHTML).join('')
      : `<div class="empty-state">No recordings yet.<br>Tap “Start Recording”.</div>`}
    </div>`;

  $$('.clip', screenEl).forEach((row) => row.addEventListener('click', (e) => {
    if (e.target.closest('[data-del]')) return;
    const clip = state.clips.find((c) => c.id === row.dataset.clip);
    if (clip) playClip(clip);
  }));

  $$('[data-del]', screenEl).forEach((btn) => btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const clip = state.clips.find((c) => c.id === btn.dataset.del);
    if (!clip) return;
    URL.revokeObjectURL(clip.url);
    state.clips = state.clips.filter((c) => c.id !== clip.id);
    toast(`Deleted ${clip.name}`);
    renderGallery();
  }));
}

function playClip(clip) {
  openModal(`
    <div class="modal-card player-card">
      <h3 class="modal-title" style="color:#fff;font-size:14px">${esc(clip.name)}</h3>
      <video src="${clip.url}" controls autoplay playsinline></video>
      <div class="player-meta">${clip.sizeMb} MB · ${clip.duration} · ${esc(clip.mime)}<br>
        ${MODES[clip.mode] ? MODES[clip.mode].title : ''}</div>
      <div class="modal-actions">
        <a class="modal-btn" href="${clip.url}" download="${esc(clip.name)}">Download</a>
        <button class="modal-btn primary" data-close type="button">Close</button>
      </div>
    </div>`);
}

function renderTab(animate = false) {
  const prev = screenEl.querySelector('.screen-scroll');
  const prevScroll = prev ? prev.scrollTop : 0;

  if (state.tab === 'SETTINGS') renderSettings();
  else if (state.tab === 'GALLERY') renderGallery();
  else renderHome();

  const next = screenEl.querySelector('.screen-scroll');
  if (next) {
    next.scrollTop = animate ? 0 : prevScroll;
    if (animate) next.classList.add('enter');
  }
  updateTabs();
}

function switchTab(tab) {
  const changed = tab !== state.tab;
  state.tab = tab;
  if (state.record) { closeRecord(); return; } // closeRecord renders the tab
  renderTab(changed);
}

/* ------------------------------------------------------------------ boot */

function tickClock() {
  const now = new Date();
  $('#clock').textContent = `${now.getHours() % 12 || 12}:${String(now.getMinutes()).padStart(2, '0')}`;
}

function init() {
  loadPrefs();
  tickClock();
  setInterval(tickClock, 15000);

  $$('.tab', tabbarEl).forEach((btn) => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });
  $$('.tab-icon', tabbarEl).forEach((span) => {
    span.innerHTML = ICONS[span.dataset.icon] || '';
  });
  $('#about-btn').addEventListener('click', showAbout);

  // flash overlay for the shutter effect
  const flash = document.createElement('div');
  flash.className = 'flash';
  deviceEl.querySelector('.device-inner').appendChild(flash);

  renderTab();
}

init();
