/* ==========================================================================
   DuelShots — interactive web preview
   A browser replica of the native Android app (Kotlin + Compose + CameraX):
   same screens, same three recording modes, real encoded clips.

   The Kotlin implementation lives in app/src/main/java/com/nrworld/duelshot/.
   ========================================================================== */
'use strict';

/* ------------------------------------------------------------------ model */

const MODE_ORDER = ['DUAL_VIEW', 'SEPARATE', 'BOTH'];

const MODES = {
  DUAL_VIEW: {
    title: 'Dual View',
    short: 'Dual',
    desc: 'Both cameras composed into one video — back feed full frame, front feed inset.',
  },
  SEPARATE: {
    title: 'Separate',
    short: 'Separate',
    desc: 'Two files, one per camera. Trim and post each angle independently.',
  },
  BOTH: {
    title: 'Both',
    short: 'Both',
    desc: 'The composed video plus both individual files — three clips in one take.',
  },
};

const ASPECT_ORDER = ['R_9_16', 'R_16_9', 'R_1_1', 'R_4_3'];

const ASPECTS = {
  R_9_16: { label: '9:16 Portrait', short: '9:16', w: 9, h: 16 },
  R_16_9: { label: '16:9 Landscape', short: '16:9', w: 16, h: 9 },
  R_1_1: { label: '1:1 Square', short: '1:1', w: 1, h: 1 },
  R_4_3: { label: '4:3', short: '4:3', w: 4, h: 3 },
};

const QUALITY_ORDER = ['HD720', 'FHD1080', 'UHD4K'];

const QUALITIES = {
  HD720: { label: '720p', seg: '720p', premium: false, long: 1280 },
  FHD1080: { label: '1080p', seg: '1080p', premium: false, long: 1920 },
  UHD4K: { label: '4K', seg: '4K', premium: true, long: 3840 },
};

const CODEC_ORDER = ['MP4', 'HEVC'];

const CODECS = {
  MP4: { label: 'MP4', seg: 'MP4', premium: false },
  HEVC: { label: 'HEVC', seg: 'HEVC', premium: true },
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

const svg = (d, extra = '') =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" ${extra}>${d}</svg>`;

const ICONS = {
  home: svg('<path d="M3.5 10.7 12 3.6l8.5 7.1"/><path d="M5.8 9.7v10.2h12.4V9.7"/>'),
  gallery: svg('<rect x="3.2" y="4.2" width="17.6" height="15.6" rx="4"/><path d="M10.4 9.1v5.8l4.9-2.9z" fill="currentColor" stroke="none"/>'),
  settings: svg('<circle cx="12" cy="12" r="3"/><path d="M19.3 14.9a1.6 1.6 0 0 0 .3 1.8l.06.06a2 2 0 1 1-2.8 2.8l-.06-.06a1.6 1.6 0 0 0-1.8-.32 1.6 1.6 0 0 0-.96 1.5V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1.06-1.5 1.6 1.6 0 0 0-1.8.32l-.06.06a2 2 0 1 1-2.8-2.8l.06-.06a1.6 1.6 0 0 0 .32-1.8 1.6 1.6 0 0 0-1.5-.96H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9.1a1.6 1.6 0 0 0-.32-1.8l-.06-.06a2 2 0 1 1 2.8-2.8l.06.06a1.6 1.6 0 0 0 1.8.32H9a1.6 1.6 0 0 0 .96-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 .96 1.5 1.6 1.6 0 0 0 1.8-.32l.06-.06a2 2 0 1 1 2.8 2.8l-.06.06a1.6 1.6 0 0 0-.32 1.8V9a1.6 1.6 0 0 0 1.5.96H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.6.94z"/>'),
  close: svg('<path d="M6.8 6.8l10.4 10.4M17.2 6.8 6.8 17.2"/>'),
  flip: svg('<path d="M20 12a8 8 0 1 1-2.4-5.7"/><path d="M20.4 4.7v5.1h-5.1"/><circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/>'),
  chevron: svg('<path d="M9.5 6.5 15 12l-5.5 5.5"/>', 'stroke-width="2"'),
  check: svg('<path d="M4.5 12.6 9.4 17.5 19.5 6.9"/>', 'stroke-width="2.1"'),
  play: svg('<path d="M8.4 5.6v12.8L19 12z" fill="currentColor" stroke="none"/>', 'stroke-width="0"'),
  download: svg('<path d="M12 4v11"/><path d="m7.8 11.4 4.2 4.2 4.2-4.2"/><path d="M4.8 19.4h14.4"/>'),
  trash: svg('<path d="M5 7h14M9.5 7V5.4h5V7M6.6 7l.8 12.1A1.9 1.9 0 0 0 9.3 21h5.4a1.9 1.9 0 0 0 1.9-1.9L17.4 7"/>'),
  camera: svg('<rect x="2.8" y="6.4" width="12.8" height="11.2" rx="3.2"/><path d="m15.6 12 5.6-3.4v6.8L15.6 12z"/>'),
  crown: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4 18.2h16l1-10.5-5 3.2L12 4.3 8 10.9l-5-3.2z"/></svg>',
};

function logoMark(size = 26, cls = 'brand-mark') {
  return `
    <svg class="${cls}" viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true">
      <defs>
        <linearGradient id="duelGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#22D3EE"/>
          <stop offset=".45" stop-color="#6366F1"/>
          <stop offset="1" stop-color="#D946EF"/>
        </linearGradient>
      </defs>
      <ellipse cx="50" cy="50" rx="44" ry="29" fill="none" stroke="url(#duelGrad)" stroke-width="3"
               stroke-linecap="round" stroke-dasharray="205 40" stroke-dashoffset="-13" opacity=".9"/>
      <circle cx="30" cy="50" r="23" fill="#08090c"/>
      <circle cx="30" cy="50" r="17.5" fill="none" stroke="url(#duelGrad)" stroke-width="3.4"/>
      <circle cx="30" cy="50" r="15" fill="#0b1024"/>
      <circle cx="26.5" cy="46.5" r="6" fill="#22d3ee"/>
      <circle cx="70" cy="50" r="23" fill="#08090c"/>
      <circle cx="70" cy="50" r="17.5" fill="none" stroke="url(#duelGrad)" stroke-width="3.4"/>
      <circle cx="70" cy="50" r="15" fill="#0b1024"/>
      <circle cx="66.5" cy="46.5" r="6" fill="#d946ef"/>
    </svg>`;
}

/* -------------------------------------------------------------- utilities */

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

function stampNow(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function hms(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const p = (n) => String(n).padStart(2, '0');
  return `${p(Math.floor(total / 3600))}:${p(Math.floor((total % 3600) / 60))}:${p(total % 60)}`;
}

function shortTime(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

const mb = (bytes) => Math.max(1, Math.round(bytes / (1024 * 1024)));

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
  } catch (_) { /* private mode */ }
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
  toastTimer = setTimeout(() => el.remove(), 2800);
}

/* ---------------------------------------------------------------- sheets */

function closeSheet() {
  modalRoot.innerHTML = '';
}

function openSheet(html, bind) {
  modalRoot.innerHTML = `<div class="modal"><div class="sheet">${html}</div></div>`;
  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  $$('[data-close]', modalRoot).forEach((b) => b.addEventListener('click', closeSheet));
  bind && bind();
}

function proFeatures() {
  return [
    '4K capture and HEVC encoding',
    '60 fps and custom bitrate',
    'Per-camera aspect control',
    'Watermark-free export',
  ];
}

function showPro(pending = null) {
  const body = state.premium
    ? `<p class="sheet-sub">Pro is active. 4K, HEVC and every format control are unlocked.</p>
       <div class="sheet-actions"><button class="btn-primary" data-close type="button" style="margin-top:0">Close</button></div>`
    : `<p class="sheet-sub">One-time unlock. Everything below stays on this device — no account, no subscription.</p>
       <ul class="pro-list">${proFeatures().map((f) => `<li class="pro-item">${ICONS.check}<span>${f}</span></li>`).join('')}</ul>
       <button class="btn-primary" data-upgrade type="button">Unlock Pro</button>
       <button class="btn-ghost" data-close type="button">Not now</button>`;

  openSheet(`
    <div class="grabber"></div>
    <div class="pro-sheet-head">
      <span class="pro-sheet-mark">♛</span>
      <div>
        <div class="sheet-title">DuelShots Pro</div>
        <div class="caption">Mock purchase in this preview</div>
      </div>
    </div>
    ${body}`, () => {
    const up = $('[data-upgrade]', modalRoot);
    if (up) up.addEventListener('click', () => {
      state.premium = true;
      // apply the option that was locked behind the upgrade
      if (pending && pending.kind === 'quality' && QUALITIES[pending.key]) state.quality = pending.key;
      if (pending && pending.kind === 'codec' && CODECS[pending.key]) state.codec = pending.key;
      savePrefs();
      closeSheet();
      toast('Pro unlocked');
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
      <p>This is a browser build of the native Android app in this repository. The screens,
      design system, recording modes and premium gating come from the Kotlin/Compose source in
      <code>app/src/main/java/com/nrworld/duelshot/</code>.</p>

      <h3>What works here</h3>
      <ul>
        <li>All three recording modes: composed picture-in-picture, two separate files, or both.</li>
        <li>Clips are encoded live in the browser and land in the Gallery tab, playable and downloadable.</li>
        <li>Settings persist in <code>localStorage</code> (SharedPreferences in the app).</li>
        <li>4K and HEVC are gated behind the mock Pro purchase — same as <code>grantPremium()</code>.</li>
      </ul>

      <h3>Cameras</h3>
      <p>Browsers usually allow only one camera stream at a time. If both the front and back camera
      can be opened at once you get a real dual feed; otherwise the preview falls back to
      single-camera mode, exactly like the Android fallback. If camera access is blocked
      (permission, iframe policy or plain HTTP) an animated demo feed keeps the recorder usable.</p>

      <h3>The real app</h3>
      <p>Build the APK locally with JDK 17/21 and the Android SDK:</p>
      <p><code>./gradlew :app:assembleDebug</code></p>

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
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'Camera blocked — demo feed';
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'No camera found — demo feed';
  if (name === 'NotReadableError') return 'Camera busy — demo feed';
  return 'Camera unavailable — demo feed';
}

/* animated synthetic feed so recording still has a source */
function drawDemo(canvas, label, hue, t) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;

  const bg = ctx.createLinearGradient(0, 0, w, h);
  bg.addColorStop(0, '#0a0f1e');
  bg.addColorStop(0.55, '#131b36');
  bg.addColorStop(1, '#070910');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  for (let i = 0; i < 4; i++) {
    const x = w * (0.5 + 0.3 * Math.sin(t / 2700 + i * 1.7 + hue));
    const y = h * (0.5 + 0.28 * Math.cos(t / 3300 + i * 2.3 + hue));
    const r = Math.min(w, h) * (0.2 + 0.06 * Math.sin(t / 1900 + i));
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `hsla(${(195 + i * 38 + hue * 50) % 360}, 85%, 62%, .45)`);
    g.addColorStop(1, 'hsla(220, 85%, 55%, 0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  const step = Math.max(44, Math.round(w / 9));
  const off = (t / 48) % step;
  ctx.strokeStyle = 'rgba(255,255,255,.045)';
  ctx.lineWidth = Math.max(1, w / 760);
  for (let x = -off; x < w; x += step) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
  }
  for (let y = -off; y < h; y += step) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
  }

  const m = w * 0.1;
  const len = w * 0.09;
  ctx.strokeStyle = 'rgba(255,255,255,.32)';
  ctx.lineWidth = Math.max(2, w / 300);
  [[m, m, 1, 1], [w - m, m, -1, 1], [m, h - m, 1, -1], [w - m, h - m, -1, -1]].forEach(([x, y, dx, dy]) => {
    ctx.beginPath();
    ctx.moveTo(x + dx * len, y);
    ctx.lineTo(x, y);
    ctx.lineTo(x, y + dy * len);
    ctx.stroke();
  });

  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(255,255,255,.92)';
  ctx.font = `700 ${Math.round(w * 0.095)}px -apple-system, system-ui, sans-serif`;
  ctx.fillText(label, w / 2, h / 2 - w * 0.01);

  ctx.fillStyle = 'rgba(255,255,255,.4)';
  ctx.font = `600 ${Math.round(w * 0.032)}px ui-monospace, monospace`;
  ctx.fillText('SIMULATED FEED', w / 2, h / 2 + w * 0.055);
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

  cam.feeds.back = { el: back, kind: 'demo', mirror: false };
  cam.feeds.front = { el: front, kind: 'demo', mirror: false };
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
  recordMessage = message;
  refreshRecord();
}

async function startCameras() {
  cam.started = true;
  recordMessage = '';
  refreshRecord();

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    enableDemo('Camera API unavailable — demo feed');
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

  // Some browsers hand back the same physical camera for both facing modes.
  if (front && backId && deviceIdOf(front) === backId) {
    front.getTracks().forEach((t) => t.stop());
    front = null;
    try {
      const others = (await navigator.mediaDevices.enumerateDevices())
        .filter((d) => d.kind === 'videoinput' && d.deviceId && d.deviceId !== backId);
      if (others.length) front = await openStream(null, others[0].deviceId);
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

  const current = cam.feeds[cam.active];
  if (current && current.el.srcObject) {
    current.el.srcObject.getTracks().forEach((t) => t.stop());
  }
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
};

const recDom = { root: null, preview: null, feeds: null, status: null, timer: null, btn: null, mode: null, flip: null, thumb: null };
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
  const candidates = [
    'video/mp4;codecs=avc1.42E01E',
    'video/mp4',
    'video/webm;codecs=vp9',
    'video/webm;codecs=vp8',
    'video/webm',
  ];
  return candidates.find((m) => window.MediaRecorder
    && MediaRecorder.isTypeSupported
    && MediaRecorder.isTypeSupported(m)) || '';
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
    } catch (_) {
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
  } catch (_) {
    try { recorder = new MediaRecorder(stream); } catch (__) { return null; }
  }

  const chunks = [];
  recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
  const done = new Promise((resolve) => { recorder.onstop = resolve; });
  recorder.start(200);

  return {
    key, name, canvas, ctx, recorder, chunks, done, mime, stream,
    ownsStream: !existingStream,
  };
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
      roundRect(ctx, x, y, pipW, pipH, Math.round(w * 0.022));
      ctx.clip();
      ctx.fillStyle = '#000';
      ctx.fillRect(x, y, pipW, pipH);
      drawCover(ctx, front.el, pipW, pipH, front.mirror);
      ctx.restore();
      ctx.strokeStyle = 'rgba(255,255,255,.55)';
      ctx.lineWidth = Math.max(2, Math.round(w / 340));
      roundRect(ctx, x, y, pipW, pipH, Math.round(w * 0.022));
      ctx.stroke();
    }
    return;
  }

  const feed = cam.feeds[o.key];
  if (feed) drawCover(ctx, feed.el, w, h, feed.mirror);
}

function posterFor(output) {
  try {
    const src = output.canvas || (cam.feeds[output.key] && cam.feeds[output.key].el);
    if (!src) return '';
    const sw = src.videoWidth || src.width;
    const sh = src.videoHeight || src.height;
    if (!sw || !sh) return '';
    const w = 300;
    const h = Math.max(2, Math.round((w * sh) / sw));
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const ctx = c.getContext('2d');
    ctx.drawImage(src, 0, 0, w, h);
    return c.toDataURL('image/jpeg', 0.62);
  } catch (_) {
    return '';
  }
}

async function startRecording() {
  if (rec.active || rec.starting) return;
  rec.starting = true;

  if (!cam.ready) {
    rec.starting = false;
    setRecordMessage('Cameras are not ready yet');
    return;
  }

  recordSaved = [];
  setRecordMessage('');

  rec.audio = null;
  try {
    rec.audio = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (_) { rec.audio = null; }

  const stamp = stampNow();
  const single = cam.singleFallback || !cam.feeds.back || !cam.feeds.front;
  const outs = [];

  if (single) {
    const key = cam.active;
    const o = makeOutput(key, `duelshot_${key}_${stamp}`,
      key === 'back' ? state.backAspect : state.frontAspect, true);
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
    // Older browsers without canvas.captureStream: record the camera stream directly.
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
    setRecordMessage('This browser cannot encode video');
    return;
  }

  rec.outs = outs;
  rec.active = true;
  rec.starting = false;
  rec.startedAt = Date.now();

  let lastDraw = 0;
  const loop = (now) => {
    if (!rec.active) return;
    if (now - lastDraw >= 30) {
      lastDraw = now;
      rec.outs.forEach(drawOutput);
    }
    rec.raf = requestAnimationFrame(loop);
  };
  rec.raf = requestAnimationFrame(loop);

  rec.timer = setInterval(() => {
    if (recDom.timer && rec.active) recDom.timer.textContent = shortTime(Date.now() - rec.startedAt);
  }, 250);

  recordSaved = [];
  recordMessage = `Recording · ${MODES[state.mode].title.toLowerCase()}${single ? ' · single camera' : ''}`;
  refreshRecord();
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

  const durationMs = Date.now() - rec.startedAt;
  const newClips = outs.map((o) => {
    const type = o.recorder.mimeType || o.mime || 'video/webm';
    const ext = type.includes('mp4') ? 'mp4' : 'webm';
    const blob = new Blob(o.chunks, { type });
    return {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      name: `${o.name}.${ext}`,
      sizeMb: mb(blob.size),
      url: URL.createObjectURL(blob),
      mime: type,
      duration: hms(durationMs),
      when: new Date(),
      mode: o.key === 'composed' ? (state.mode === 'BOTH' ? 'BOTH' : 'DUAL_VIEW') : state.mode,
      poster: posterFor(o),
    };
  });

  outs.forEach((o) => { if (o.ownsStream) o.stream.getTracks().forEach((t) => t.stop()); });
  if (rec.audio) {
    rec.audio.getTracks().forEach((t) => t.stop());
    rec.audio = null;
  }

  state.clips.unshift(...newClips);
  recordSaved = newClips.map((c) => c.name);

  recordMessage = newClips.length === 1 ? 'Saved to Gallery' : `Saved ${newClips.length} clips to Gallery`;
  refreshRecord();
  toast(newClips.length === 1 ? 'Saved to Gallery' : `Saved ${newClips.length} clips`);
}

function setRecordMessage(msg) {
  recordMessage = msg;
  refreshRecord();
}

/* =====================================================================
   SCREEN: RECORD
   ===================================================================== */

function buildRecordShell() {
  screenEl.innerHTML = `
    <div class="record">
      <div class="rec-preview" id="rec-preview">
        <div class="feeds" id="rec-feeds"></div>
        <div class="rec-scrim"></div>
        <div class="rec-top">
          <button class="glass" id="rec-close" type="button" aria-label="Close">${ICONS.close}</button>
          <span class="pill-glass mode-pill" id="rec-mode">Dual View</span>
          <span class="pill-glass rec-timer"><span class="dot"></span><span id="rec-timer">00:00</span></span>
        </div>
      </div>

      <div class="rec-dock">
        <div class="rec-status" id="rec-status"></div>
        <div class="rec-controls">
          <button class="dock-btn" id="rec-thumb" type="button" aria-label="Open gallery">${ICONS.gallery}</button>
          <button class="shutter" id="rec-btn" type="button" aria-label="Record"><i></i></button>
          <button class="dock-btn" id="rec-flip" type="button" aria-label="Switch camera">${ICONS.flip}</button>
        </div>
      </div>
    </div>`;

  recDom.root = $('.record');
  recDom.preview = $('#rec-preview');
  recDom.feeds = $('#rec-feeds');
  recDom.status = $('#rec-status');
  recDom.timer = $('#rec-timer');
  recDom.btn = $('#rec-btn');
  recDom.mode = $('#rec-mode');
  recDom.flip = $('#rec-flip');
  recDom.thumb = $('#rec-thumb');

  $('#rec-close').addEventListener('click', async () => {
    if (rec.active) await stopRecording();
    closeRecord();
  });
  recDom.flip.addEventListener('click', switchCamera);
  recDom.thumb.addEventListener('click', () => {
    if (rec.active) { toast('Stop recording first'); return; }
    closeRecord();
    switchTab('GALLERY');
  });
  recDom.btn.addEventListener('click', () => {
    if (rec.active) stopRecording();
    else startRecording();
  });
}

function makeTile(label) {
  const tile = document.createElement('div');
  tile.className = 'tile';
  if (label) {
    const badge = document.createElement('span');
    badge.className = 'badge';
    badge.textContent = label;
    tile.appendChild(badge);
  }
  return tile;
}

function placeholder(text) {
  const d = document.createElement('div');
  d.className = 'tile-placeholder';
  d.innerHTML = `<span class="big">${ICONS.camera}</span><span>${esc(text)}</span>`;
  return d;
}

function mountFeeds() {
  const stage = recDom.feeds;
  if (!stage) return;
  stage.innerHTML = '';
  mountedKey = layoutKey();

  if (!cam.ready) {
    const tile = makeTile(null);
    tile.appendChild(placeholder('Starting cameras'));
    stage.appendChild(tile);
    return;
  }

  const hasBoth = !!(cam.feeds.back && cam.feeds.front);
  const single = cam.singleFallback || !hasBoth;

  if (single) {
    const key = cam.feeds[cam.active] ? cam.active : (cam.feeds.back ? 'back' : 'front');
    const feed = cam.feeds[key];
    const aspect = ASPECTS[key === 'back' ? state.backAspect : state.frontAspect].short;
    const tile = makeTile(`CAMERA · ${aspect}`);
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
    const tile = makeTile(null);
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

  [
    { key: 'back', label: 'BACK', aspect: state.backAspect },
    { key: 'front', label: 'FRONT', aspect: state.frontAspect },
  ].forEach(({ key, label, aspect }) => {
    const tile = makeTile(`${label} · ${ASPECTS[aspect].short}`);
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
  return [cam.ready, cam.singleFallback, cam.active, state.mode].join('|');
}

function unmountFeeds() {
  Object.values(cam.feeds).forEach((f) => { if (f && f.el) sinkEl.appendChild(f.el); });
}

function recordStatusText() {
  if (recordMessage) return recordMessage;
  if (!cam.ready) return 'Starting cameras…';
  if (cam.demo) return 'Demo feed — camera unavailable here';
  if (cam.singleFallback) return `Single camera · ${cam.active} · one file`;
  return `${MODES[state.mode].title} · front + back`;
}

function updateDockThumb() {
  if (!recDom.thumb) return;
  const last = state.clips[0];
  if (last && last.poster) {
    recDom.thumb.style.backgroundImage = `url(${last.poster})`;
    recDom.thumb.innerHTML = '';
  } else {
    recDom.thumb.style.backgroundImage = '';
    recDom.thumb.innerHTML = ICONS.gallery;
  }
}

function refreshRecord() {
  if (!state.record || !recDom.root) return;

  if (layoutKey() !== mountedKey) mountFeeds();

  if (recDom.mode) recDom.mode.textContent = MODES[state.mode].title;
  if (recDom.preview) recDom.preview.classList.toggle('recording', rec.active);
  if (recDom.btn) {
    recDom.btn.classList.toggle('on', rec.active);
    recDom.btn.disabled = !cam.ready;
  }
  if (recDom.timer) {
    recDom.timer.textContent = rec.active ? shortTime(Date.now() - rec.startedAt) : '00:00';
  }
  if (recDom.status) {
    recDom.status.innerHTML = esc(recordStatusText()) + (recordSaved.length
      ? `<span class="saved">${esc(recordSaved.join(' · '))}</span>`
      : '');
  }
  updateDockThumb();
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
  Object.keys(recDom).forEach((k) => { recDom[k] = null; });
  mountedKey = null;
  recordMessage = '';
  recordSaved = [];
  unmountFeeds();
  document.body.dataset.screen = state.tab.toLowerCase();
  tabbarEl.hidden = false;
  renderTab(true);
}

/* =====================================================================
   SEGMENTED CONTROL
   ===================================================================== */

function segmentedHTML(items, activeKey, opts = {}) {
  const n = items.length;
  const idx = Math.max(0, items.findIndex((i) => i.key === activeKey));
  return `
    <div class="segmented ${opts.small ? 'small' : ''}" data-seg="${opts.name}" style="--n:${n}">
      <span class="ind" style="--i:${idx}"></span>
      ${items.map((i) => `
        <button type="button" data-${opts.attr}="${i.key}" class="${i.key === activeKey ? 'active' : ''}">${i.label}</button>
      `).join('')}
    </div>`;
}

function wireSegmented(root, opts, onPick) {
  $$(`[data-${opts.attr}]`, root).forEach((btn) => {
    btn.addEventListener('click', () => onPick(btn.dataset[opts.attr]));
  });
}

const modeItems = () => MODE_ORDER.map((m) => ({ key: m, label: MODES[m].short }));

/* =====================================================================
   SCREEN: HOME
   ===================================================================== */

function renderHome() {
  const modeItemsList = modeItems();
  const quality = QUALITIES[state.quality];

  screenEl.innerHTML = `
    <div class="scroll">
      <header class="topbar">
        <div class="brand">
          ${logoMark(26)}
          <span class="brand-name">Duel<span>Shots</span></span>
        </div>
        <button class="chip-btn" id="pro-btn" type="button">
          <span class="spark"></span>${state.premium ? 'Pro' : 'Free'}
        </button>
      </header>

      <h1 class="display">Two angles.<br><span class="grad-text">One take.</span></h1>
      <p class="lede">Front and back camera, recorded at the same moment.</p>

      <div class="hero" aria-hidden="true">
        <div class="hero-art"></div>
        <span class="hero-badge">DUAL CAMERA</span>
        <div class="hero-pip"><span>FRONT</span></div>
        <div class="hero-tag"><i></i><span>BACK · ${ASPECTS[state.backAspect].short}</span></div>
      </div>

      <div class="label">Mode</div>
      ${segmentedHTML(modeItemsList, state.mode, { name: 'mode', attr: 'mode' })}
      <p class="mode-desc">${MODES[state.mode].desc}</p>

      <button class="btn-primary" id="start-btn" type="button">
        <span class="rec-dot"></span>Start recording
      </button>
      <p class="cta-meta">${MODES[state.mode].title} · ${quality.label} · ${ASPECTS[state.frontAspect].short} + ${ASPECTS[state.backAspect].short}</p>

      <div class="label">Camera</div>
      <div class="group">
        <button class="row" data-go="SETTINGS" type="button">
          <span class="row-label">Front camera</span>
          <span class="row-val">${ASPECTS[state.frontAspect].label}</span>
          <span class="chev">${ICONS.chevron}</span>
        </button>
        <button class="row" data-go="SETTINGS" type="button">
          <span class="row-label">Back camera</span>
          <span class="row-val">${ASPECTS[state.backAspect].label}</span>
          <span class="chev">${ICONS.chevron}</span>
        </button>
        <button class="row" data-go="SETTINGS" type="button">
          <span class="row-label">Quality</span>
          <span class="row-val">${quality.label} · ${CODECS[state.codec].label}</span>
          <span class="chev">${ICONS.chevron}</span>
        </button>
      </div>

      ${state.premium ? '' : `
        <button class="pro-row" id="pro-row" type="button">
          <span class="pro-mark">♛</span>
          <span class="pro-copy">
            <span class="pro-title">Unlock Pro</span>
            <span class="pro-sub">4K · HEVC · 60 fps · watermark-free</span>
          </span>
          <span class="chev">${ICONS.chevron}</span>
        </button>`}
    </div>`;

  $('#start-btn').addEventListener('click', openRecord);
  $('#pro-btn').addEventListener('click', showPro);
  const proRow = $('#pro-row');
  if (proRow) proRow.addEventListener('click', showPro);
  $$('[data-go]', screenEl).forEach((b) => b.addEventListener('click', () => switchTab(b.dataset.go)));

  wireSegmented(screenEl, { attr: 'mode' }, (key) => {
    state.mode = key;
    savePrefs();
    renderHome();
  });
}

/* =====================================================================
   SCREEN: SETTINGS
   ===================================================================== */

function renderSettings() {
  screenEl.innerHTML = `
    <div class="scroll">
      <h1 class="h1">Settings</h1>
      <p class="sub">How DuelShots records and what it writes.</p>

      <div class="label">Recording mode</div>
      ${segmentedHTML(modeItems(), state.mode, { name: 'mode', attr: 'mode' })}
      <p class="mode-desc">${MODES[state.mode].desc}</p>

      <div class="label">Camera formats</div>
      <div class="group">
        <div class="row stack">
          <div class="row-head">
            <span class="row-label">Front camera</span>
            <span class="row-val">${ASPECTS[state.frontAspect].short}</span>
          </div>
          <div class="chips">
            ${ASPECT_ORDER.map((k) => `
              <button class="chip ${k === state.frontAspect ? 'active' : ''}" data-front="${k}" type="button">${ASPECTS[k].label}</button>
            `).join('')}
          </div>
        </div>
        <div class="row stack">
          <div class="row-head">
            <span class="row-label">Back camera</span>
            <span class="row-val">${ASPECTS[state.backAspect].short}</span>
          </div>
          <div class="chips">
            ${ASPECT_ORDER.map((k) => `
              <button class="chip ${k === state.backAspect ? 'active' : ''}" data-back="${k}" type="button">${ASPECTS[k].label}</button>
            `).join('')}
          </div>
        </div>
      </div>

      <div class="label">Video</div>
      <div class="group">
        <div class="row">
          <span class="row-label">Resolution</span>
          <div class="row-val" style="margin-left:auto">
            ${segmentedHTML(QUALITY_ORDER.map((k) => ({ key: k, label: QUALITIES[k].seg })), state.quality, { name: 'quality', attr: 'quality', small: true })}
          </div>
        </div>
        <div class="row">
          <span class="row-label">Container</span>
          <div class="row-val" style="margin-left:auto">
            ${segmentedHTML(CODEC_ORDER.map((k) => ({ key: k, label: CODECS[k].seg })), state.codec, { name: 'codec', attr: 'codec', small: true })}
          </div>
        </div>
      </div>

      <div class="pro-card">
        <div class="pro-card-head">
          <span class="pro-mark">♛</span>
          <div>
            <div class="pro-card-title">DuelShots Pro</div>
            <div class="caption">${state.premium ? 'Active on this device' : 'One-time unlock'}</div>
          </div>
        </div>
        <div class="pro-list">
          ${proFeatures().map((f) => `<div class="pro-item">${ICONS.check}<span>${f}</span></div>`).join('')}
        </div>
        ${state.premium
      ? `<div class="pro-active">${ICONS.check}<span>Pro is active</span></div>`
      : `<button class="btn-primary" id="pro-btn-settings" type="button" style="margin-top:16px"><span class="spark"></span>Unlock Pro</button>`}
      </div>
    </div>`;

  wireSegmented(screenEl, { attr: 'mode' }, (key) => {
    state.mode = key;
    savePrefs();
    renderSettings();
  });

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
    if (QUALITIES[key].premium && !state.premium) { showPro({ kind: 'quality', key }); return; }
    state.quality = key;
    savePrefs();
    renderSettings();
  }));
  $$('[data-codec]', screenEl).forEach((b) => b.addEventListener('click', () => {
    const key = b.dataset.codec;
    if (CODECS[key].premium && !state.premium) { showPro({ kind: 'codec', key }); return; }
    state.codec = key;
    savePrefs();
    renderSettings();
  }));

  const proBtn = $('#pro-btn-settings');
  if (proBtn) proBtn.addEventListener('click', showPro);
}

/* =====================================================================
   SCREEN: GALLERY
   ===================================================================== */

function renderGallery() {
  screenEl.innerHTML = `
    <div class="scroll">
      <h1 class="h1">Gallery</h1>
      <p class="sub">${state.clips.length
      ? `${state.clips.length} clip${state.clips.length === 1 ? '' : 's'} this session · saved to Movies/DuelShots`
      : 'Saved to Movies/DuelShots'}</p>

      ${state.clips.length ? `<div class="grid">${state.clips.map(clipCardHTML).join('')}</div>` : `
        <div class="empty">
          <div class="empty-icon">${ICONS.camera}</div>
          <h3>No recordings yet</h3>
          <p>Your first take lands here — composed, separate, or both.</p>
          <button class="btn-primary" id="empty-start" type="button" style="margin-top:18px">
            <span class="rec-dot"></span>Start recording
          </button>
        </div>`}
    </div>`;

  $$('.clip', screenEl).forEach((el) => el.addEventListener('click', () => {
    const clip = state.clips.find((c) => c.id === el.dataset.clip);
    if (clip) playClip(clip);
  }));

  const empty = $('#empty-start');
  if (empty) empty.addEventListener('click', openRecord);
}

function clipCardHTML(c) {
  return `
    <button class="clip" data-clip="${c.id}" type="button">
      <div class="thumb" style="${c.poster ? `background-image:url(${c.poster})` : ''}">
        <span class="play">${ICONS.play}</span>
        <span class="dur">${c.duration.replace(/^00:/, '')}</span>
      </div>
      <div class="clip-name">${esc(c.name)}</div>
      <div class="clip-meta">${c.sizeMb} MB · ${MODES[c.mode] ? MODES[c.mode].title : 'Clip'}</div>
    </button>`;
}

function playClip(clip) {
  openSheet(`
    <div class="grabber"></div>
    <div class="player">
      <video src="${clip.url}" controls autoplay playsinline></video>
      <div class="player-meta">${esc(clip.name)}<br>${clip.sizeMb} MB · ${clip.duration} · ${esc(clip.mime)}</div>
      <div class="player-actions">
        <a class="btn-primary" href="${clip.url}" download="${esc(clip.name)}">${ICONS.download} Download</a>
        <button class="btn-secondary btn-danger" data-delete type="button">Delete</button>
        <button class="btn-secondary" data-close type="button">Close</button>
      </div>
    </div>`, () => {
    const del = $('[data-delete]', modalRoot);
    if (del) del.addEventListener('click', () => {
      URL.revokeObjectURL(clip.url);
      state.clips = state.clips.filter((c) => c.id !== clip.id);
      closeSheet();
      toast('Clip deleted');
      if (state.tab === 'GALLERY') renderGallery();
    });
  });
}

/* =====================================================================
   TABS
   ===================================================================== */

function updateTabs() {
  $$('.tab', tabbarEl).forEach((btn) => btn.classList.toggle('active', btn.dataset.tab === state.tab));
}

function renderTab(animate = false) {
  const prev = screenEl.querySelector('.scroll');
  const prevScroll = prev ? prev.scrollTop : 0;

  if (state.tab === 'SETTINGS') renderSettings();
  else if (state.tab === 'GALLERY') renderGallery();
  else renderHome();

  const next = screenEl.querySelector('.scroll');
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
  const h = now.getHours() % 12 || 12;
  $('#clock').textContent = `${h}:${String(now.getMinutes()).padStart(2, '0')}`;
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

  renderTab();
}

init();
