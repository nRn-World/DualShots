/* ==========================================================================
   DualShots — interactive web preview
   A browser replica of the native Android app (Kotlin + Compose + CameraX):
   same flows, same three recording modes, real encoded clips.

   The Kotlin implementation lives in app/src/main/java/com/nrworld/duelshot/.
   ========================================================================== */
'use strict';

/* ------------------------------------------------------------------ model */

const MODE_ORDER = ['DUAL_VIEW', 'SEPARATE', 'BOTH'];

const MODES = {
  DUAL_VIEW: {
    title: 'Dual View',
    sub: 'Both cameras in one video',
    desc: 'Both cameras composed into one video — back feed full frame, front feed inset.',
  },
  SEPARATE: {
    title: 'Separate Videos',
    sub: 'Two files, one for each',
    desc: 'Two files, one per camera. Trim and post each angle independently.',
  },
  BOTH: {
    title: 'Both Options',
    sub: 'Combined + two separate files',
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
  HD720: { label: '720p', premium: false, long: 1280 },
  FHD1080: { label: '1080p', premium: false, long: 1920 },
  UHD4K: { label: '4K', premium: true, long: 3840 },
};

const CODEC_ORDER = ['MP4', 'HEVC'];

const CODECS = {
  MP4: { label: 'MP4', premium: false },
  HEVC: { label: 'HEVC', premium: true },
};

const STORE_KEY = 'duelshot_prefs';

const state = {
  page: 'HOME',
  record: false,
  mode: 'DUAL_VIEW',
  frontAspect: 'R_9_16',
  backAspect: 'R_16_9',
  quality: 'FHD1080',
  codec: 'MP4',
  premium: false,
  audio: true,
  zoom: { main: 1, inset: 1 },
  stack: 'front-first',
  pip: { cx: 0.74, cy: 0.24, scale: 0.34 },
  pipOff: false,
  pipPaused: false,
  swap: false,
  clips: [],
};

/* --------------------------------------------------------------- elements */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const screenEl = $('#screen');
const sinkEl = $('#media-sink');
const modalRoot = $('#modal-root');
const deviceEl = $('#device');

/* ------------------------------------------------------------------ icons */

const svg = (d, extra = '') =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" ${extra}>${d}</svg>`;

const ICONS = {
  home: svg('<path d="M3 10.5 12 3.5l9 7V20h-6v-5.5H9V20H3z"/>'),
  gallery: svg('<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10 9.5 15.5 12 10 14.5z" fill="currentColor" stroke="none"/>'),
  settings: svg('<path d="M3 6h18M3 12h18M3 18h18"/><circle cx="8" cy="6" r="2.6" fill="currentColor" stroke="none"/><circle cx="16" cy="12" r="2.6" fill="currentColor" stroke="none"/><circle cx="11" cy="18" r="2.6" fill="currentColor" stroke="none"/>'),
  camera: svg('<rect x="3" y="7" width="18" height="13" rx="3"/><path d="M8 7l1.5-2.5h5L16 7"/><circle cx="12" cy="13.5" r="3.2"/>'),
  video: svg('<rect x="2.5" y="6.5" width="13" height="11" rx="2.5"/><path d="M15.5 10.5 21.5 7v10l-6-3.5z"/>'),
  crown: svg('<path d="M3 8l4.5 4.5L12 5l4.5 7.5L21 8l-2 11H5z"/>'),
  info: svg('<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1.2" fill="currentColor" stroke="none"/>'),
  check: svg('<path d="M5 12.5 10 17.5 19 7"/>'),
  chevron: svg('<path d="M9 5l7 7-7 7"/>'),
  close: svg('<path d="M6 6l12 12M18 6 6 18"/>'),
  play: svg('<path d="M8 5.5 19 12 8 18.5z" fill="currentColor" stroke="none"/>'),
  bolt: svg('<path d="M13 2.5 5.5 13.5H11l-1 8 7.5-11H12z"/>'),
  flipCam: svg('<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20.4 4.6v5.2h-5.2"/><circle cx="12" cy="12" r="2.4"/>'),
  swap: svg('<path d="M4.5 8.5h13l-3-3"/><path d="M19.5 15.5h-13l3 3"/>'),
  download: svg('<path d="M12 4v11"/><path d="m7.8 11.4 4.2 4.2 4.2-4.2"/><path d="M4.8 19.4h14.4"/>'),
  pause: svg('<rect x="7.5" y="5" width="3.6" height="14" rx="1.2" fill="currentColor" stroke="none"/><rect x="12.9" y="5" width="3.6" height="14" rx="1.2" fill="currentColor" stroke="none"/>'),
  pip: svg('<rect x="3" y="5" width="18" height="14" rx="3"/><rect x="11.5" y="10.5" width="7.5" height="6.5" rx="1.6" fill="currentColor" stroke="none"/>'),
  copy: svg('<rect x="9" y="3" width="12" height="12" rx="2.5"/><rect x="3" y="9" width="12" height="12" rx="2.5"/>'),
  grid: svg('<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M9 3v18M15 3v18M3 9h18M3 15h18"/>'),
  share: svg('<path d="M12 3.5v11"/><path d="m8 7.5 4-4 4 4"/><path d="M6 12v8h12v-8"/>'),
  trash: svg('<path d="M4 7h16"/><path d="M9.5 7V4.5h5V7"/><path d="M6.5 7l1.1 13h8.8L17.5 7"/><path d="M10.5 10.5 11 16.5M13.5 10.5 13 16.5"/>'),
};

/* Samma logotyp som appens launcher-ikon: orange squircle + svart kamera. */
const LOGO_MARK = `
  <svg class="logo-mark" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <defs>
      <linearGradient id="logoGrad" x1="6" y1="4" x2="102" y2="106" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#ff4d00" />
        <stop offset="1" stop-color="#ff9a2e" />
      </linearGradient>
    </defs>
    <rect width="108" height="108" rx="26" fill="url(#logoGrad)" />
    <g fill="none" stroke="#0a0a0a" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round">
      <rect x="25" y="42" width="58" height="32" rx="9" />
      <path d="M44 42v-2a7 7 0 0 1 7-7h6a7 7 0 0 1 7 7v2" />
      <circle cx="54" cy="58" r="10.5" />
    </g>
    <circle cx="70" cy="51" r="1.8" fill="#0a0a0a" />
  </svg>`;

/* Samma markering som launcher-ikonen: två linser på navy-botten. */
const LENS_MARK = `
  <svg class="logo-mark" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect width="108" height="108" rx="26" fill="#0e1626" />
    <path fill-rule="evenodd" fill="#e4552b" d="M40 54m-20 0a20 20 0 1 0 40 0a20 20 0 1 0-40 0M40 54m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0" />
    <path fill-rule="evenodd" fill="#f2a03f" stroke="#0e1626" stroke-width="4.5" d="M66 54m-20 0a20 20 0 1 0 40 0a20 20 0 1 0-40 0M66 54m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0" />
  </svg>`;

const logoTile = () => `
  <div class="logo-tile">
    ${LENS_MARK}
  </div>`;

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

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

/* overlay geometry */

const PIP_MIN = 0.16;
const PIP_MAX = 0.62;
const PIP_DEFAULT = { cx: 0.74, cy: 0.24, scale: 0.34 };
const ZOOM_MIN = 1;
const ZOOM_MAX = 3;
const clampZoom = (z) => clamp(Number.isFinite(z) ? z : 1, ZOOM_MIN, ZOOM_MAX);
const fmtZoom = (z) => `${Math.round(z * 10) / 10}×`;

const currentPip = () => state.pip || PIP_DEFAULT;

function pipRect(w, h, aspectKey, pip) {
  const a = ASPECTS[aspectKey];
  let pw = w * clamp(pip.scale, PIP_MIN, PIP_MAX);
  let ph = pw * (a.h / a.w);

  // keep the inset inside the frame with a margin, preserving the camera aspect
  const maxH = h * 0.72;
  const maxW = w * 0.46;
  if (ph > maxH) {
    ph = maxH;
    pw = ph * (a.w / a.h);
  }
  if (pw > maxW) {
    pw = maxW;
    ph = pw * (a.h / a.w);
  }

  const mx = w * 0.035;
  const my = h * 0.035;
  const x = clamp(pip.cx * w - pw / 2, mx, Math.max(mx, w - pw - mx));
  const y = clamp(pip.cy * h - ph / 2, my, Math.max(my, h - ph - my));
  return { x, y, w: pw, h: ph };
}

function clampPipToStage(pip, el, aspectKey) {
  const w = el.clientWidth;
  const h = el.clientHeight;
  if (!w || !h) return pip;
  const a = ASPECTS[aspectKey];
  const pw = w * pip.scale;
  const ph = pw * (a.h / a.w);
  const halfX = Math.min(0.5, (pw / 2) / w);
  const halfY = Math.min(0.5, (ph / 2) / h);
  return {
    ...pip,
    cx: clamp(pip.cx, halfX, 1 - halfX),
    cy: clamp(pip.cy, halfY, 1 - halfY),
  };
}

/* prefs */

function savePrefs() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({
      mode: state.mode,
      frontAspect: state.frontAspect,
      backAspect: state.backAspect,
      quality: state.quality,
      codec: state.codec,
      premium: state.premium,
      audio: state.audio,
      pip: state.pip,
      swap: state.swap,
      onboarded: !!state.onboarded,
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
    state.swap = !!p.swap;
    state.onboarded = !!p.onboarded;
    if (typeof p.audio === 'boolean') state.audio = p.audio;
    if (p.pip && Number.isFinite(p.pip.cx) && Number.isFinite(p.pip.cy) && Number.isFinite(p.pip.scale)) {
      state.pip = {
        cx: clamp(p.pip.cx, 0, 1),
        cy: clamp(p.pip.cy, 0, 1),
        scale: clamp(p.pip.scale, PIP_MIN, PIP_MAX),
      };
    }
  } catch (_) { /* ignore */ }
}

async function copyText(text) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) { /* faller tillbaka nedan */ }

  try {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand('copy');
    area.remove();
    return ok;
  } catch (_) {
    return false;
  }
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
  modalRoot.innerHTML = `<div class="modal sheet-wrap"><div class="sheet">${html}</div></div>`;
  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  wireSheet();
  bind && bind();
}

function wireSheet() {
  $$('[data-close]', modalRoot).forEach((b) => b.addEventListener('click', closeSheet));
}

const PREMIUM_LINES = [
  '4K Ultra HD recording',
  'HEVC codec',
  '60fps & custom bitrate',
  'Per-camera aspect control',
  'Watermark-free export',
];

/* PremiumDialog from Components.kt: crown tile, feature list, orange CTA. */
function showPremium(pending = null) {
  const feature = pending ? pending.label : 'DualShots Premium';

  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog premium-dialog">
        <div class="dlg-mark">${ICONS.crown}</div>
        <div class="dlg-title">DualShots PRO</div>
        <div class="dlg-sub">${esc(String(feature).toUpperCase())}</div>
        <div class="dlg-list">
          ${PREMIUM_LINES.map((line) => `
            <div class="dlg-item"><span class="dlg-check">${ICONS.check}</span><span>${line}</span></div>`).join('')}
        </div>
        <button class="dlg-btn" data-upgrade type="button" ${state.premium ? 'disabled' : ''}>
          ${state.premium ? 'PRO ACTIVE' : 'UNLOCK PRO'}
        </button>
        <button class="dlg-close" data-close type="button">Close</button>
      </div>
    </div>`;

  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  wireSheet();

  const up = $('[data-upgrade]', modalRoot);
  if (up) up.addEventListener('click', () => {
    if (state.premium) return;
    state.premium = true;
    if (pending && pending.kind === 'quality' && QUALITIES[pending.key]) state.quality = pending.key;
    if (pending && pending.kind === 'codec' && CODECS[pending.key]) state.codec = pending.key;
    savePrefs();
    closeSheet();
    toast('Premium unlocked');
    if (state.record) refreshRecord(); else render();
  });
}

/* AlertDialog from HomeScreen.kt. */
function showAbout() {
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog about-dialog">
        <div class="about-title">DualShots</div>
        <div class="about-version">Version 1.0 · Dual lens recording engine</div>
        <button class="about-ok" data-close type="button">OK</button>
      </div>
    </div>`;

  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  wireSheet();
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

function drawDemo(canvas, label, hue, t) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;

  const bg = ctx.createLinearGradient(0, 0, w, h);
  bg.addColorStop(0, '#1a0f08');
  bg.addColorStop(0.55, '#22130a');
  bg.addColorStop(1, '#0a0705');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);

  for (let i = 0; i < 4; i++) {
    const x = w * (0.5 + 0.3 * Math.sin(t / 2700 + i * 1.7 + hue));
    const y = h * (0.5 + 0.28 * Math.cos(t / 3300 + i * 2.3 + hue));
    const r = Math.min(w, h) * (0.2 + 0.06 * Math.sin(t / 1900 + i));
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `hsla(${(28 + i * 14 + hue * 30) % 360}, 92%, 58%, .5)`);
    g.addColorStop(1, 'hsla(20, 92%, 50%, 0)');
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

  // Bara en av kamerorna gick att öppna medan en simulering redan körs:
  // behåll då den simulerade dubbelvyn istället för en halv blandning.
  if (!(back && front) && cam.demo) {
    [back, front].forEach((stream) => {
      if (stream) stream.getTracks().forEach((t) => t.stop());
    });
    startDemo();
    cam.ready = true;
    refreshRecord();
    return;
  }

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
   OVERLAY GESTURES (drag + pinch the front camera inside Dual View)
   ===================================================================== */

const gesture = { pointers: new Map(), drag: null, pinch: null, lastTap: 0, moved: false, multi: false };
let pipHintTimer = 0;

function pointerDistance() {
  const pts = [...gesture.pointers.values()];
  if (pts.length < 2) return 0;
  return Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
}

const pipAspectKey = () => recDom.pipAspect || state.frontAspect;

function onPreviewPointerDown(e) {
  if (!recDom.feeds) return;
  const onPip = !!(recDom.pip && e.target.closest && e.target.closest('.pip'));
  gesture.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, onPip });

  if (gesture.pointers.size === 1) {
    gesture.moved = false;
    gesture.multi = false;
    gesture.pinch = null;
    gesture.drag = null;
    if (!onPip) return;
    const p = currentPip();
    gesture.drag = { id: e.pointerId, x: e.clientX, y: e.clientY, cx: p.cx, cy: p.cy };
    recDom.pip.classList.add('active');
    hidePipHint();
    try { recDom.preview.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
  } else if (gesture.pointers.size === 2) {
    // Nyp på stora bilden = stora kameran, nyp på lilla rutan = lilla kameran.
    const target = [...gesture.pointers.values()].some((p) => p.onPip) ? 'inset' : 'main';
    if (target === 'inset' && !recDom.pip) return;
    gesture.pinch = { dist: pointerDistance(), zoom: state.zoom[target], target };
    gesture.drag = null;
    gesture.multi = true;
    if (target === 'inset') recDom.pip.classList.add('active');
    hidePipHint();
  }
}

function onPreviewPointerMove(e) {
  if (!gesture.pointers.has(e.pointerId) || !recDom.feeds) return;
  gesture.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

  if (gesture.pointers.size >= 2 && gesture.pinch && gesture.pinch.dist > 0) {
    const zoom = clampZoom(gesture.pinch.zoom * (pointerDistance() / gesture.pinch.dist));
    if (Math.abs(zoom - state.zoom[gesture.pinch.target]) > 0.001) {
      state.zoom[gesture.pinch.target] = zoom;
      applyZoom();
    }
    return;
  }

  if (!recDom.pip) return;
  const tile = recDom.pip.parentElement;
  if (!tile) return;
  const rect = tile.getBoundingClientRect();
  if (!rect.width || !rect.height) return;

  if (gesture.drag && gesture.drag.id === e.pointerId) {
    const dx = e.clientX - gesture.drag.x;
    const dy = e.clientY - gesture.drag.y;
    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) gesture.moved = true;
    const cx = gesture.drag.cx + dx / rect.width;
    const cy = gesture.drag.cy + dy / rect.height;
    state.pip = clampPipToStage({ ...currentPip(), cx, cy }, tile, pipAspectKey());
    applyPipStyle();
  }
}

function onPreviewPointerUp(e) {
  if (!gesture.pointers.has(e.pointerId)) return;

  const wasMulti = gesture.pointers.size > 1 || gesture.multi;
  const dragged = gesture.moved;
  const onPip = !!(recDom.pip && e.target.closest && e.target.closest('.pip'));

  gesture.pointers.delete(e.pointerId);

  if (gesture.pointers.size === 1) {
    const [id, point] = [...gesture.pointers.entries()][0];
    gesture.pinch = null;
    if (point.onPip && recDom.pip) {
      const cur = currentPip();
      gesture.drag = { id, x: point.x, y: point.y, cx: cur.cx, cy: cur.cy };
    } else {
      gesture.drag = null;
    }
    return;
  }

  if (gesture.pointers.size === 0) {
    gesture.drag = null;
    gesture.pinch = null;
    gesture.moved = false;
    gesture.multi = false;
    if (recDom.pip) recDom.pip.classList.remove('active');
    savePrefs();
  }

  if (onPip && !wasMulti && !dragged) {
    const now = Date.now();
    if (now - gesture.lastTap < 320) {
      resetPip();
      gesture.lastTap = 0;
    } else {
      gesture.lastTap = now;
    }
  } else {
    gesture.lastTap = 0;
  }
}

function onPreviewWheel(e) {
  if (!recDom.feeds) return;
  const target = (recDom.pip && e.target.closest && e.target.closest('.pip')) ? 'inset' : 'main';
  if (target === 'inset' && !recDom.pip) return;
  e.preventDefault();
  state.zoom[target] = clampZoom(state.zoom[target] * (1 - e.deltaY * 0.0012));
  applyZoom();
}

/* Hörnhandtaget: dra utåt för större liten kamera, inåt för mindre. */
function wirePipResize(handle) {
  let start = null;

  handle.addEventListener('pointerdown', (e) => {
    e.stopPropagation();
    e.preventDefault();
    const tile = recDom.pip && recDom.pip.parentElement;
    if (!tile || !tile.clientWidth) return;
    start = { id: e.pointerId, x: e.clientX, y: e.clientY, scale: currentPip().scale, w: tile.clientWidth };
    recDom.pip.classList.add('active', 'resizing');
    hidePipHint();
    try { handle.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
  });

  handle.addEventListener('pointermove', (e) => {
    if (!start || e.pointerId !== start.id) return;
    e.preventDefault();
    const delta = ((e.clientX - start.x) + (e.clientY - start.y)) / 2;
    const scale = clamp(start.scale + delta / start.w, PIP_MIN, PIP_MAX);
    state.pip = clampPipToStage({ ...currentPip(), scale }, recDom.pip.parentElement, pipAspectKey());
    applyPipStyle();
  });

  const end = (e) => {
    if (!start || e.pointerId !== start.id) return;
    start = null;
    recDom.pip.classList.remove('active', 'resizing');
    savePrefs();
  };
  handle.addEventListener('pointerup', end);
  handle.addEventListener('pointercancel', end);
}

/* Två fingrar zoomar, ett finger flyttar, hörnet ändrar storlek. */
function attachPreviewGestures() {
  const el = recDom.preview;
  if (!el) return;
  el.addEventListener('pointerdown', onPreviewPointerDown);
  el.addEventListener('pointermove', onPreviewPointerMove);
  el.addEventListener('pointerup', onPreviewPointerUp);
  el.addEventListener('pointercancel', onPreviewPointerUp);
  el.addEventListener('wheel', onPreviewWheel, { passive: false });
  el.addEventListener('dblclick', (e) => {
    if (e.target.closest && e.target.closest('.pip')) resetPip();
  });
}

function applyPipStyle() {
  if (!recDom.pip) return;
  const p = currentPip();
  recDom.pip.style.width = `${(p.scale * 100).toFixed(2)}%`;
  recDom.pip.style.left = `${(p.cx * 100).toFixed(2)}%`;
  recDom.pip.style.top = `${(p.cy * 100).toFixed(2)}%`;
}

function hidePipHint(delay = 0) {
  clearTimeout(pipHintTimer);
  const hide = () => { if (recDom.hint) recDom.hint.classList.add('hide'); };
  if (delay) pipHintTimer = setTimeout(hide, delay);
  else hide();
}

function resetPip() {
  state.pip = { ...PIP_DEFAULT };
  savePrefs();
  applyPipStyle();
  toast('Overlay reset');
}

/* =====================================================================
   RECORDING
   ===================================================================== */

const rec = {
  active: false,
  starting: false,
  take: null,
  startedAt: 0,
  timer: 0,
  raf: 0,
  outs: [],
  audio: null,
};

const recDom = {
  root: null, preview: null, feeds: null, status: null, timer: null,
  btn: null, label: null, flip: null, swap: null, thumb: null, pip: null, hint: null, zoom: null,
  pipAspect: null,
  pipBtn: null,
};
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

function drawCover(ctx, src, w, h, mirror, zoom = 1) {
  const sw = src.videoWidth || src.width;
  const sh = src.videoHeight || src.height;
  if (!sw || !sh) return;
  const scale = Math.max(w / sw, h / sh) * zoom;
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
  const quality = (rec.take && rec.take.quality) || state.quality;

  if (!stream) {
    const { width, height } = outputSize(aspectKey, quality);
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
      ? { mimeType: mime, videoBitsPerSecond: quality === 'UHD4K' ? 12_000_000 : 6_000_000 }
      : undefined);
  } catch (_) {
    try { recorder = new MediaRecorder(stream); } catch (__) { return null; }
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
  const zoom = state.zoom || { main: 1, inset: 1 };
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);

  if (o.key === 'composed') {
    const swap = rec.take ? rec.take.swap : state.swap;
    const bigKey = swap ? 'front' : 'back';
    const smallKey = swap ? 'back' : 'front';
    const big = cam.feeds[bigKey];
    const small = cam.feeds[smallKey];

    if (big) drawCover(ctx, big.el, w, h, big.mirror, zoom.main);
    if (small && !state.pipOff) {
      const aspectKey = smallKey === 'back'
        ? ((rec.take && rec.take.backAspect) || state.backAspect)
        : ((rec.take && rec.take.frontAspect) || state.frontAspect);
      const g = pipRect(w, h, aspectKey, currentPip());
      const radius = Math.round(Math.min(g.w, g.h) * 0.07);
      ctx.save();
      ctx.translate(g.x, g.y);
      roundRect(ctx, 0, 0, g.w, g.h, radius);
      ctx.clip();
      ctx.fillStyle = '#000';
      ctx.fillRect(0, 0, g.w, g.h);
      if (state.pipPaused) {
        ctx.fillStyle = 'rgba(8,10,16,.94)';
        ctx.fillRect(0, 0, g.w, g.h);
        ctx.fillStyle = 'rgba(255,255,255,.55)';
        ctx.font = `700 ${Math.round(g.w * 0.1)}px ui-monospace, monospace`;
        ctx.textAlign = 'center';
        ctx.fillText('PAUSED', g.w / 2, g.h / 2);
      } else {
        drawCover(ctx, small.el, g.w, g.h, small.mirror, zoom.inset);
      }
      ctx.restore();
      ctx.strokeStyle = 'rgba(255,255,255,.55)';
      ctx.lineWidth = Math.max(2, Math.round(w / 340));
      roundRect(ctx, g.x, g.y, g.w, g.h, radius);
      ctx.stroke();
    }
    return;
  }

  const feed = cam.feeds[o.key];
  if (feed) drawCover(ctx, feed.el, w, h, feed.mirror, zoom.main);
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
  if (state.audio) {
    try {
      rec.audio = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (_) { rec.audio = null; }
  }

  const take = {
    mode: state.mode,
    frontAspect: state.frontAspect,
    backAspect: state.backAspect,
    quality: state.quality,
    swap: state.swap,
  };
  rec.take = take;

  const stamp = stampNow();
  const single = !cam.feeds.back || !cam.feeds.front;
  const outs = [];

  if (single) {
    const key = cam.active;
    const o = makeOutput(key, `dualshot_${key}_${stamp}`,
      key === 'back' ? take.backAspect : take.frontAspect, true);
    if (o) outs.push(o);
  } else if (take.mode === 'DUAL_VIEW') {
    const o = makeOutput('composed', `dualshot_combined_${stamp}`, take.backAspect, true);
    if (o) outs.push(o);
  } else if (take.mode === 'SEPARATE') {
    const b = makeOutput('back', `dualshot_back_${stamp}`, take.backAspect, true);
    const f = makeOutput('front', `dualshot_front_${stamp}`, take.frontAspect, false);
    if (b) outs.push(b);
    if (f) outs.push(f);
  } else {
    const c = makeOutput('composed', `dualshot_combined_${stamp}`, take.backAspect, true);
    const b = makeOutput('back', `dualshot_back_${stamp}`, take.backAspect, false);
    const f = makeOutput('front', `dualshot_front_${stamp}`, take.frontAspect, false);
    [c, b, f].forEach((o) => o && outs.push(o));
  }

  if (!outs.length) {
    const feed = cam.feeds[cam.active] || cam.feeds.back || cam.feeds.front;
    const src = feed && feed.el && feed.el.srcObject;
    if (src instanceof MediaStream) {
      const o = makeOutput(cam.active, `dualshot_${cam.active}_${stamp}`,
        cam.active === 'back' ? take.backAspect : take.frontAspect, false, src);
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
  rec.paused = false;
  rec.pausedTotal = 0;
  rec.pausedAt = 0;

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
    if (recDom.timer && rec.active) recDom.timer.textContent = hms(takeElapsed());
  }, 250);

  recordMessage = `Recording · ${MODES[take.mode].title.toLowerCase()}${single ? ' · single camera' : ''}`;
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

  const durationMs = takeElapsed();
  rec.paused = false;
  rec.pausedTotal = 0;
  rec.pausedAt = 0;
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
      mode: o.key === 'composed'
        ? ((rec.take && rec.take.mode === 'BOTH') ? 'BOTH' : 'DUAL_VIEW')
        : ((rec.take && rec.take.mode) || state.mode),
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

  rec.take = null;
  mountedKey = null;
  recordMessage = newClips.length === 1 ? 'Saved to Gallery' : `Saved ${newClips.length} clips to Gallery`;
  refreshRecord();
  toast(newClips.length === 1 ? 'Saved to Gallery' : `Saved ${newClips.length} clips`);
}

/* Paus/återuppta: MediaRecorder skriver vidare i samma fil. */
function takeElapsed() {
  if (!rec.startedAt) return 0;
  const pausedNow = rec.pausedAt ? Date.now() - rec.pausedAt : 0;
  return Date.now() - rec.startedAt - (rec.pausedTotal || 0) - pausedNow;
}

function pauseTake() {
  if (!rec.active || rec.paused) return;
  rec.paused = true;
  rec.pausedAt = Date.now();
  rec.outs.forEach((o) => { try { o.recorder.pause(); } catch (_) { /* unsupported */ } });
  recordMessage = 'Paused — resume to continue the same file';
  refreshRecord();
  toast('Paused');
}

function resumeTake() {
  if (!rec.active || !rec.paused) return;
  rec.pausedTotal = (rec.pausedTotal || 0) + (Date.now() - (rec.pausedAt || Date.now()));
  rec.pausedAt = 0;
  rec.paused = false;
  rec.outs.forEach((o) => { try { o.recorder.resume(); } catch (_) { /* unsupported */ } });
  recordMessage = 'Recording · ' + MODES[rec.take ? rec.take.mode : state.mode].title.toLowerCase();
  refreshRecord();
  toast('Recording');
}

function applyGrid() {
  if (recDom.gridLayer) recDom.gridLayer.classList.toggle('on', !!state.grid);
  if (recDom.gridBtn) recDom.gridBtn.classList.toggle('on', !!state.grid);
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
    <div class="record" id="record-root">
      <div class="rec-top">
        <button class="icon-chip" id="rec-close" type="button" aria-label="Close">${ICONS.close}</button>
        <span class="timer"><span class="dot"></span><span id="rec-timer">00:00:00</span></span>
        <button class="icon-chip" id="rec-pause" type="button" aria-label="Pause recording" hidden>${ICONS.pause}</button>
        <span class="spacer"></span>
        <button class="icon-chip" id="rec-grid" type="button" aria-label="Grid">${ICONS.grid}</button>
        <button class="icon-chip" id="rec-mode" type="button" aria-label="Camera setup">${ICONS.settings}</button>
      </div>

      <div class="rec-preview" id="rec-preview">
        <div class="feeds" id="rec-feeds"></div>
        <div class="rec-grid" id="rec-grid-layer"></div>
      </div>

      <div class="rec-status" id="rec-status">
        <span id="rec-line"></span>
        <span class="formats" id="rec-formats"></span>
        <span class="saved" id="rec-saved"></span>
      </div>

      <div class="rec-controls">
        <button class="zoom-chip" id="rec-zoom" type="button" aria-label="Zoom">1×</button>
        <button class="shutter" id="rec-btn" type="button" aria-label="Record"><i></i></button>
        <button class="dock-btn" id="rec-flip" type="button" aria-label="Switch camera">${ICONS.flipCam}</button>
        <button class="dock-btn" id="rec-swap" type="button" aria-label="Swap large and small camera">${ICONS.swap}</button>
        <button class="dock-btn" id="rec-pip" type="button" aria-label="Hide small camera">${ICONS.pip}</button>
      </div>
    </div>`;

  recDom.root = $('#record-root');
  recDom.preview = $('#rec-preview');
  recDom.feeds = $('#rec-feeds');
  recDom.status = $('#rec-status');
  recDom.line = $('#rec-line');
  recDom.formats = $('#rec-formats');
  recDom.saved = $('#rec-saved');
  recDom.timer = $('#rec-timer');
  recDom.pauseBtn = $('#rec-pause');
  recDom.gridBtn = $('#rec-grid');
  recDom.gridLayer = $('#rec-grid-layer');
  recDom.pauseBtn.addEventListener('click', () => (rec.paused ? resumeTake() : pauseTake()));
  recDom.gridBtn.addEventListener('click', () => {
    state.grid = !state.grid;
    applyGrid();
    toast(state.grid ? 'Grid on' : 'Grid off');
  });
  recDom.btn = $('#rec-btn');
  recDom.flip = $('#rec-flip');
  recDom.swap = $('#rec-swap');
  recDom.zoom = $('#rec-zoom');
  recDom.pipBtn = $('#rec-pip');
  recDom.pipBtn.addEventListener('click', () => {
    state.pipOff = !state.pipOff;
    if (!state.pipOff) state.pipPaused = false;
    mountFeeds();
    refreshRecord();
    toast(state.pipOff ? 'Small camera off — main camera only' : 'Small camera on');
  });

  $('#rec-close').addEventListener('click', async () => {
    if (rec.active) await stopRecording();
    closeRecord();
  });
  recDom.flip.addEventListener('click', switchCamera);
  recDom.swap.addEventListener('click', () => {
    state.swap = !state.swap;
    savePrefs();
    mountFeeds();
    toast(state.swap ? 'Front camera large' : 'Back camera large');
  });
  $('#rec-mode').addEventListener('click', mountCameraSheet);
  recDom.btn.addEventListener('click', () => {
    if (rec.active) stopRecording();
    else startRecording();
  });
  recDom.zoom.addEventListener('click', () => {
    state.zoom.main = state.zoom.main >= ZOOM_MAX - 0.5
      ? ZOOM_MIN
      : Math.min(ZOOM_MAX, state.zoom.main + 1);
    applyZoom();
    toast(`Zoom ${fmtZoom(state.zoom.main)}`);
  });

  attachPreviewGestures();
  applyZoom();
}

function setZoomTag(el, value) {
  const tag = el && el.querySelector('.zoom-tag');
  if (!tag) return;
  const on = value > 1.01;
  tag.textContent = on ? fmtZoom(value) : '';
  tag.classList.toggle('on', on);
}

/* Zoom per ruta: stora kameran ('main') och lilla rutan ('inset'). */
function applyZoom() {
  const z = state.zoom;
  if (recDom.feeds) {
    $$('.tile', recDom.feeds).forEach((tile) => {
      tile.style.setProperty('--zoom', z.main);
      setZoomTag(tile, z.main);
    });
  }
  if (recDom.pip) {
    recDom.pip.style.setProperty('--zoom', z.inset);
    setZoomTag(recDom.pip, z.inset);
  }
  if (recDom.zoom) {
    recDom.zoom.textContent = fmtZoom(z.main);
    recDom.zoom.classList.toggle('active', z.main > 1.01);
  }
}

function makeTile(label) {
  const tile = document.createElement('div');
  tile.className = 'tile';
  tile.style.setProperty('--zoom', state.zoom.main);
  const tag = document.createElement('span');
  tag.className = 'zoom-tag';
  tile.appendChild(tag);
  setZoomTag(tile, state.zoom.main);
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
  d.innerHTML = `${ICONS.camera}<span>${esc(text)}</span>`;
  return d;
}

/* Pausa eller stäng av lilla kameran medan den stora spelar in. */
function buildPipControls() {
  const wrap = document.createElement('div');
  wrap.className = 'pip-controls';

  const pause = document.createElement('button');
  pause.type = 'button';
  pause.className = 'pip-ctl';
  pause.setAttribute('aria-label', state.pipPaused ? 'Resume small camera' : 'Pause small camera');
  pause.innerHTML = state.pipPaused ? ICONS.play : ICONS.pause;
  pause.addEventListener('pointerdown', (e) => e.stopPropagation());
  pause.addEventListener('click', (e) => {
    e.stopPropagation();
    state.pipPaused = !state.pipPaused;
    mountFeeds();
    refreshRecord();
    toast(state.pipPaused ? 'Small camera paused' : 'Small camera running');
  });

  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'pip-ctl';
  close.setAttribute('aria-label', 'Turn off small camera');
  close.innerHTML = ICONS.close;
  close.addEventListener('pointerdown', (e) => e.stopPropagation());
  close.addEventListener('click', (e) => {
    e.stopPropagation();
    state.pipOff = true;
    state.pipPaused = false;
    mountFeeds();
    refreshRecord();
    toast('Small camera off — main camera only');
  });

  wrap.append(pause, close);
  return wrap;
}

function mountFeeds() {
  const stage = recDom.feeds;
  if (!stage) return;
  stage.innerHTML = '';
  recDom.pip = null;
  recDom.pipAspect = null;
  recDom.hint = null;
  mountedKey = layoutKey();

  if (!cam.ready) {
    const tile = makeTile(null);
    tile.appendChild(placeholder('Starting cameras'));
    stage.appendChild(tile);
    return;
  }

  const hasBoth = !!(cam.feeds.back && cam.feeds.front);
  const single = !hasBoth;
  const take = activeTake();

  if (single) {
    const key = cam.feeds[cam.active] ? cam.active : (cam.feeds.back ? 'back' : 'front');
    const feed = cam.feeds[key];
    const aspect = ASPECTS[key === 'back' ? take.backAspect : take.frontAspect].short;
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

  if (take.mode === 'DUAL_VIEW') {
    const bigKey = state.swap ? 'front' : 'back';
    const smallKey = state.swap ? 'back' : 'front';
    const big = cam.feeds[bigKey];
    const small = cam.feeds[smallKey];

    const tile = makeTile(bigKey.toUpperCase());
    stage.appendChild(tile);
    if (big) {
      if (big.mirror) tile.classList.add('mirror');
      tile.appendChild(big.el);
    }

    if (small && !state.pipOff) {
      const pip = document.createElement('div');
      pip.className = 'pip' + (small.mirror ? ' mirror' : '') + (state.pipPaused ? ' paused' : '');
      const a = ASPECTS[smallKey === 'back' ? take.backAspect : take.frontAspect];
      pip.style.setProperty('--pip-ratio', `${a.w} / ${a.h}`);
      pip.appendChild(small.el);

      const pipTag = document.createElement('span');
      pipTag.className = 'zoom-tag';
      pip.appendChild(pipTag);

      const handle = document.createElement('div');
      handle.className = 'pip-resize';
      handle.setAttribute('aria-hidden', 'true');
      pip.appendChild(handle);
      pip.appendChild(buildPipControls());
      if (state.pipPaused) {
        const note = document.createElement('span');
        note.className = 'pip-paused';
        note.textContent = 'Paused';
        pip.appendChild(note);
      }

      tile.appendChild(pip);
      recDom.pip = pip;
      recDom.pipAspect = smallKey === 'back' ? take.backAspect : take.frontAspect;
      applyPipStyle();
      wirePipResize(handle);
      applyZoom();

      const hint = document.createElement('div');
      hint.className = 'pip-hint';
      hint.textContent = 'Drag to move · pinch to zoom · corner to resize';
      tile.appendChild(hint);
      recDom.hint = hint;
      hidePipHint(6000);
    }
    return;
  }

  // stacked previews (Separate / Both), like the native app
  const order = state.stack === 'back-first' ? ['back', 'front'] : ['front', 'back'];
  order.forEach((key) => {
    const aspect = ASPECTS[key === 'back' ? take.backAspect : take.frontAspect].short;
    const tile = makeTile(`${key.toUpperCase()} · ${aspect}`);
    const feed = cam.feeds[key];
    if (feed) {
      if (feed.mirror) tile.classList.add('mirror');
      tile.appendChild(feed.el);
    } else {
      tile.appendChild(placeholder('Unavailable'));
    }
    stage.appendChild(tile);
  });

  const swap = document.createElement('button');
  swap.className = 'stack-swap';
  swap.type = 'button';
  swap.setAttribute('aria-label', 'Swap camera order');
  swap.innerHTML = `${ICONS.swap}<span>Swap</span>`;
  swap.addEventListener('click', () => {
    state.stack = state.stack === 'back-first' ? 'front-first' : 'back-first';
    mountFeeds();
  });
  stage.appendChild(swap);
}

function activeTake() {
  return rec.active && rec.take ? rec.take : {
    mode: state.mode,
    frontAspect: state.frontAspect,
    backAspect: state.backAspect,
    quality: state.quality,
  };
}

function layoutKey() {
  const t = activeTake();
  return [cam.ready, cam.singleFallback, cam.active, t.mode, t.frontAspect, t.backAspect, state.stack, state.swap].join('|');
}

function unmountFeeds() {
  Object.values(cam.feeds).forEach((f) => { if (f && f.el) sinkEl.appendChild(f.el); });
}

function recordStatusText() {
  if (recordMessage) return recordMessage;
  if (!cam.ready) return 'Starting cameras…';
  if (cam.demo) return 'Demo feed — camera unavailable here';
  if (!cam.feeds.back || !cam.feeds.front) return `Single camera · ${cam.active}`;
  return `${MODES[activeTake().mode].title} · front + back`;
}

function formatSummary() {
  const t = activeTake();
  const quality = QUALITIES[t.quality].label;
  const audio = state.audio ? 'audio' : 'muted';
  if (!cam.feeds.back || !cam.feeds.front) {
    const short = ASPECTS[cam.active === 'back' ? t.backAspect : t.frontAspect].short;
    return `${cam.active === 'back' ? 'Back' : 'Front'} ${short} · ${quality} · ${audio}`;
  }
  const pipState = state.pipOff ? ' · small off' : state.pipPaused ? ' · small paused' : '';
  return `Front ${ASPECTS[t.frontAspect].short} · Back ${ASPECTS[t.backAspect].short} · ${quality} · ${audio}${pipState}`;
}

function refreshRecord() {
  if (!state.record || !recDom.root) return;

  if (layoutKey() !== mountedKey) mountFeeds();

  if (recDom.label) recDom.label.textContent = MODES[activeTake().mode].title;
  if (recDom.preview) recDom.preview.classList.toggle('recording', rec.active);
  if (recDom.root) recDom.root.classList.toggle('recording', rec.active);
  const dual = !!(cam.feeds.back && cam.feeds.front);
  if (recDom.swap) {
    recDom.swap.hidden = !dual;
    recDom.swap.classList.toggle('swapped', state.swap);
  }
  if (recDom.flip) recDom.flip.hidden = dual;
  if (recDom.pipBtn) {
    recDom.pipBtn.hidden = !dual;
    recDom.pipBtn.classList.toggle('off', state.pipOff);
    recDom.pipBtn.setAttribute('aria-label', state.pipOff ? 'Show small camera' : 'Hide small camera');
  }
  if (recDom.btn) {
    recDom.btn.classList.toggle('on', rec.active);
    recDom.btn.disabled = !cam.ready;
  }
  if (recDom.timer) {
    recDom.timer.textContent = rec.active ? hms(takeElapsed()) : '00:00:00';
  }
  if (recDom.pauseBtn) {
    recDom.pauseBtn.hidden = !rec.active;
    recDom.pauseBtn.classList.toggle('on', !!rec.paused);
    recDom.pauseBtn.innerHTML = rec.paused ? ICONS.play : ICONS.pause;
    recDom.pauseBtn.setAttribute('aria-label', rec.paused ? 'Resume recording' : 'Pause recording');
  }
  applyGrid();
  if (recDom.line) recDom.line.textContent = recordStatusText();
  if (recDom.formats) recDom.formats.textContent = formatSummary();
  if (recDom.saved) {
    recDom.saved.textContent = recordSaved.length ? `Saved ${recordSaved.join(' · ')}` : '';
  }
  applyZoom();
}

function openRecord() {
  state.record = true;
  document.body.dataset.screen = 'record';
  recordMessage = '';
  recordSaved = [];
  mountedKey = null;

  buildRecordShell();
  mountFeeds();
  refreshRecord();

  renderNav();

  if (!cam.started) startCameras();
}

function closeRecord() {
  state.record = false;
  Object.keys(recDom).forEach((k) => { recDom[k] = null; });
  mountedKey = null;
  recordMessage = '';
  recordSaved = [];
  unmountFeeds();
  document.body.dataset.screen = 'page';
  render();
}

/* =====================================================================
   SHEET: CAMERA SETUP (reachable while filming)
   ===================================================================== */

function cameraSheetHTML() {
  const locked = rec.active;
  return `
    <div class="grabber"></div>
    <div class="sheet-title">Camera setup</div>
    <p class="sheet-sub">Frame each camera the way you want it. Both formats are used for the take.</p>
    ${locked ? '<div class="sheet-note">One take = one file · changes apply to the next take</div>' : ''}

    <div class="section-title">Mode</div>
    <div class="option-list">
      ${MODE_ORDER.map((m) => `
        <button class="option ${state.mode === m ? 'active' : ''}" data-recmode="${m}" type="button">
          <span>
            <span class="t">${MODES[m].title}</span>
            <span class="d">${MODES[m].desc}</span>
          </span>
          <span class="tick">${ICONS.check}</span>
        </button>
      `).join('')}
    </div>

    <div class="section-title">Front camera</div>
    <div class="chips">
      ${ASPECT_ORDER.map((k) => `
        <button class="chip ${k === state.frontAspect ? 'active' : ''}" data-recfront="${k}" type="button">${ASPECTS[k].short}</button>
      `).join('')}
    </div>

    <div class="section-title">Back camera</div>
    <div class="chips">
      ${ASPECT_ORDER.map((k) => `
        <button class="chip ${k === state.backAspect ? 'active' : ''}" data-recback="${k}" type="button">${ASPECTS[k].short}</button>
      `).join('')}
    </div>

    <button class="mini-btn" data-swap type="button">Swap front and back</button>
    ${state.mode === 'DUAL_VIEW'
      ? '<button class="mini-btn" data-reset-pip type="button">Reset overlay position</button>'
      : ''}

    <button class="btn-primary" data-close type="button">Done</button>`;
}

function mountCameraSheet() {
  const html = cameraSheetHTML();
  const existing = $('.sheet', modalRoot);
  if (existing) existing.innerHTML = html;
  else openSheet(html);
  wireCameraSheet();
}

function wireCameraSheet() {
  wireSheet();

  $$('[data-recmode]', modalRoot).forEach((b) => b.addEventListener('click', () => {
    state.mode = b.dataset.recmode;
    savePrefs();
    mountCameraSheet();
    if (rec.active) toast('Mode applies to the next take');
    else refreshRecord();
  }));

  const pick = (which, key) => {
    const prop = which === 'front' ? 'frontAspect' : 'backAspect';
    state[prop] = key;
    savePrefs();
    if (rec.active) {
      toast(`${which === 'front' ? 'Front' : 'Back'} camera ${ASPECTS[key].short} · starts with the next take`);
    }
    mountCameraSheet();
    if (!rec.active) refreshRecord();
  };

  $$('[data-recfront]', modalRoot).forEach((b) => b.addEventListener('click', () => pick('front', b.dataset.recfront)));
  $$('[data-recback]', modalRoot).forEach((b) => b.addEventListener('click', () => pick('back', b.dataset.recback)));

  const swap = $('[data-swap]', modalRoot);
  if (swap) swap.addEventListener('click', () => {
    const front = state.frontAspect;
    state.frontAspect = state.backAspect;
    state.backAspect = front;
    savePrefs();
    if (rec.active) toast('Swapped · starts with the next take');
    mountCameraSheet();
    if (!rec.active) refreshRecord();
  });

  const resetBtn = $('[data-reset-pip]', modalRoot);
  if (resetBtn) resetBtn.addEventListener('click', () => {
    resetPip();
    mountCameraSheet();
  });
}

/* =====================================================================
   PAGES
   ===================================================================== */

function render() {
  if (state.record) { renderNav(); return; }
  document.body.dataset.screen = 'page';
  if (!state.onboarded) { renderOnboarding(); renderNav(); return; }
  if (state.page === 'SETTINGS') renderSettings();
  else if (state.page === 'GALLERY') renderGallery();
  else renderHome();
  renderNav();
}

/* Bottom navigation from DualShotsApp.BottomBar(). */
function renderNav() {
  const nav = $('#nav');
  if (!nav) return;
  nav.hidden = state.record || !state.onboarded;
  const tabs = [
    ['HOME', 'Home', ICONS.home],
    ['GALLERY', 'Gallery', ICONS.gallery],
    ['SETTINGS', 'Settings', ICONS.settings],
  ];
  nav.innerHTML = tabs.map(([key, label, icon]) => `
    <button class="tab ${state.page === key ? 'active' : ''}" data-tab="${key}" type="button">
      <span class="tab-icon">${icon}</span>
      <span class="tab-label">${label}</span>
    </button>`).join('');
  $$('[data-tab]', nav).forEach((b) => b.addEventListener('click', () => go(b.dataset.tab)));
}

/* HomeScreen.kt: logo tile, wordmark, tagline, then the four menu rows. */
function renderHome() {
  screenEl.innerHTML = `
    <div class="home">
      ${logoTile()}
      <h1 class="wordmark">Dual<span>Shots</span></h1>
      <p class="tagline">RECORD BOTH SIDES. AT ONCE.</p>

      <div class="menu-stack">
        <button class="btn-start" id="start-btn" type="button">
          <span class="start-ico">${ICONS.video}</span>
          <span class="start-label">START RECORDING</span>
          <span class="start-chev">${ICONS.chevron}</span>
        </button>

        <button class="menu-btn" id="home-settings" type="button">
          <span class="menu-ico">${ICONS.settings}</span>
          <span class="menu-label">Settings</span>
          <span class="menu-chev">${ICONS.chevron}</span>
        </button>

        <button class="menu-btn premium" id="menu-premium" type="button">
          <span class="menu-ico">${ICONS.crown}</span>
          <span class="menu-label">${state.premium ? 'Premium active' : 'Premium Features'}</span>
          <span class="menu-chev">${ICONS.chevron}</span>
        </button>

        <button class="menu-btn" id="menu-about" type="button">
          <span class="menu-ico">${ICONS.info}</span>
          <span class="menu-label">About</span>
          <span class="menu-chev">${ICONS.chevron}</span>
        </button>
      </div>

      <div class="made-by">
        <span class="credit-name">Made by nRnWorld</span>
        <a class="credit-link" href="https://nrnworld.one" target="_blank" rel="noopener noreferrer">nrnworld.one</a>
        <span class="credit-mail">
          <a href="mailto:bynrnworld@gmail.com">bynrnworld@gmail.com</a>
          <button class="copy-mail" id="copy-mail" type="button" aria-label="Copy email address">${ICONS.copy}</button>
        </span>
      </div>
    </div>`;

  $('#start-btn').addEventListener('click', openRecord);
  $('#home-settings').addEventListener('click', () => go('SETTINGS'));
  $('#menu-premium').addEventListener('click', () => showPremium());
  $('#menu-about').addEventListener('click', showAbout);

  const copyMail = $('#copy-mail');
  if (copyMail) {
    copyMail.addEventListener('click', async () => {
      const ok = await copyText('bynrnworld@gmail.com');
      toast(ok ? 'Email copied' : 'bynrnworld@gmail.com');
    });
  }
}

/* SettingsScreen.kt: mode radios, per-camera aspect chips, inline
   segmented quality/codec and the premium card. */
function renderSettings() {
  screenEl.innerHTML = `
    <div class="page settings">
      <div class="studio-head">
        <span class="studio-ico">${ICONS.settings}</span>
        <span class="studio-title">SETTINGS STUDIO</span>
      </div>

      <div class="section-label">RECORDING MODE</div>
      <div class="card-stack">
        ${MODE_ORDER.map((m) => `
          <button class="card radio-row ${state.mode === m ? 'selected' : ''}" data-mode="${m}" type="button">
            <span class="radio-text">
              <span class="radio-title">${MODES[m].title}</span>
              <span class="radio-sub">${MODES[m].sub}</span>
            </span>
            <span class="radio-dot"></span>
          </button>`).join('')}
      </div>

      <div class="section-label">CAMERA FORMATS</div>
      <div class="card-stack">
        <div class="card">
          <div class="card-title">Front Camera</div>
          <div class="chips">
            ${ASPECT_ORDER.map((k) => `
              <button class="chip ${state.frontAspect === k ? 'active' : ''}" data-front="${k}" type="button">${ASPECTS[k].label}</button>`).join('')}
          </div>
        </div>
        <div class="card">
          <div class="card-title">Back Camera</div>
          <div class="chips">
            ${ASPECT_ORDER.map((k) => `
              <button class="chip ${state.backAspect === k ? 'active' : ''}" data-back="${k}" type="button">${ASPECTS[k].label}</button>`).join('')}
          </div>
        </div>
      </div>

      <div class="section-label">VIDEO QUALITY</div>
      <div class="card row-between">
        <span class="row-label">Resolution</span>
        <div class="seg">
          ${QUALITY_ORDER.map((k) => `
            <button class="seg-btn ${state.quality === k ? 'active' : ''}" data-quality="${k}" type="button">${QUALITIES[k].label}</button>`).join('')}
        </div>
      </div>

      <div class="section-label">CONTAINER / CODEC</div>
      <div class="card row-between">
        <span class="row-label">Format</span>
        <div class="seg">
          ${CODEC_ORDER.map((k) => `
            <button class="seg-btn ${state.codec === k ? 'active' : ''}" data-codec="${k}" type="button">${CODECS[k].label}</button>`).join('')}
        </div>
      </div>

      <div class="card premium-card">
        <div class="premium-head">${ICONS.crown}<span>PREMIUM FEATURES</span></div>
        <div class="premium-list">
          ${['More format options (4K, HEVC)', '60fps & custom bitrate', 'Per-camera aspect control', 'No watermark']
            .map((f) => `<div class="premium-item">${ICONS.check}<span>${f}</span></div>`).join('')}
        </div>
        ${state.premium
          ? '<div class="premium-active">Premium active</div>'
          : `<button class="gold-btn" id="settings-premium" type="button">${ICONS.crown}<span>GO PREMIUM</span></button>`}
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
    if (QUALITIES[key].premium && !state.premium) {
      showPremium({ kind: 'quality', key, label: QUALITIES[key].label });
      return;
    }
    state.quality = key;
    savePrefs();
    renderSettings();
  }));

  $$('[data-codec]', screenEl).forEach((b) => b.addEventListener('click', () => {
    const key = b.dataset.codec;
    if (CODECS[key].premium && !state.premium) {
      showPremium({ kind: 'codec', key, label: CODECS[key].label });
      return;
    }
    state.codec = key;
    savePrefs();
    renderSettings();
  }));

  const premiumBtn = $('#settings-premium');
  if (premiumBtn) premiumBtn.addEventListener('click', () => showPremium());
}

/* GalleryScreen.kt: large title, clip rows with the gradient play tile. */
function renderGallery() {
  screenEl.innerHTML = `
    <div class="page gallery">
      <header class="screen-header">
        <h1>Gallery</h1>
        <p>Saved to Movies/DualShots</p>
      </header>

      ${state.clips.length
        ? `<div class="clip-list">${state.clips.map(clipCardHTML).join('')}</div>`
        : `<div class="empty">
             <span class="empty-ico">${ICONS.gallery}</span>
             <span class="empty-title">No recordings yet</span>
             <span class="empty-sub">Start a recording from Home</span>
           </div>`}
    </div>`;

  $$('.clip', screenEl).forEach((el) => el.addEventListener('click', () => {
    const clip = state.clips.find((c) => c.id === el.dataset.clip);
    if (clip) playClip(clip);
  }));
}

function clipMetaLine(c) {
  const when = c.when instanceof Date && !Number.isNaN(c.when.getTime())
    ? c.when.toLocaleString(undefined, {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    })
    : '';
  return `${c.sizeMb} MB${when ? ` · ${when}` : ''}`;
}

function clipCardHTML(c) {
  return `
    <button class="clip" data-clip="${c.id}" type="button">
      <span class="clip-tile">${c.poster ? `<img src="${c.poster}" alt="" />` : ICONS.play}</span>
      <span class="clip-body">
        <span class="clip-name">${esc(c.name)}</span>
        <span class="clip-meta">${esc(clipMetaLine(c))}</span>
      </span>
      <span class="clip-chev">${ICONS.chevron}</span>
    </button>`;
}

/* GalleryScreen.kt: spelare i dialog med Share / Delete / Close. */
function playClip(clip) {
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog clip-dialog">
        <div class="clip-dialog-name">${esc(clip.name)}</div>
        <video class="clip-video" src="${clip.url}" controls autoplay playsinline></video>
        <div class="clip-dialog-meta">${esc(clipMetaLine(clip))} · ${esc(clip.duration || '')}</div>
        <div class="pill-row">
          <button class="pill" data-share type="button">${ICONS.share}<span>Share</span></button>
          <button class="pill danger" data-delete type="button">${ICONS.trash}<span>Delete</span></button>
          <button class="pill" data-close type="button">${ICONS.close}<span>Close</span></button>
        </div>
      </div>
    </div>`;

  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  wireSheet();
  $('[data-share]', modalRoot).addEventListener('click', () => shareClip(clip));
  $('[data-delete]', modalRoot).addEventListener('click', () => confirmDeleteClip(clip));
}

/* Samma bekräftelsedialog som GalleryScreen.kt. */
function confirmDeleteClip(clip) {
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog confirm-dialog">
        <div class="confirm-title">Delete clip?</div>
        <p class="confirm-text">${esc(clip.name)} is removed from Movies/DualShots.</p>
        <div class="confirm-actions">
          <button class="confirm-delete" data-confirm type="button">Delete</button>
          <button class="confirm-cancel" data-close type="button">Cancel</button>
        </div>
      </div>
    </div>`;

  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) closeSheet(); });
  wireSheet();
  $('[data-confirm]', modalRoot).addEventListener('click', () => {
    URL.revokeObjectURL(clip.url);
    state.clips = state.clips.filter((c) => c.id !== clip.id);
    closeSheet();
    toast('Clip deleted');
    if (state.page === 'GALLERY') renderGallery();
  });
}

/* Delar filen via systemets delningsark; nedladdning om det inte stöds. */
async function shareClip(clip) {
  try {
    if (navigator.share) {
      const blob = await (await fetch(clip.url)).blob();
      const file = new File([blob], clip.name, { type: clip.mime || blob.type || 'video/webm' });
      if (!navigator.canShare || navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: clip.name });
        return;
      }
    }
  } catch (_) { /* avbruten eller ostödd – faller tillbaka */ }

  const a = document.createElement('a');
  a.href = clip.url;
  a.download = clip.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  toast('Downloaded ' + clip.name);
}

/* OnboardingScreen.kt: tre steg, logotyp, prickar och gradient-CTA. */
const ONBOARD_STEPS = [
  {
    icon: 'camera',
    title: 'Two cameras, one take',
    body: 'Record the front and back camera at the same time – one composed video, two files, or both.',
  },
  {
    icon: 'pip',
    title: 'Make the shot yours',
    body: 'Drag, resize, pause or hide the small camera while you film. Pinch to zoom either camera.',
  },
  {
    icon: 'gallery',
    title: 'Ready to share',
    body: 'Clips land in Movies/DualShots. Preview, share or delete them without leaving the app.',
  },
];

function renderOnboarding() {
  const step = ONBOARD_STEPS[state.onboardStep || 0];
  const last = (state.onboardStep || 0) === ONBOARD_STEPS.length - 1;

  screenEl.innerHTML = `
    <div class="onboarding">
      <div class="onb-top">
        <div class="onb-tile">${LOGO_MARK}</div>
        <h1 class="onb-title">${step.title}</h1>
        <p class="onb-body">${step.body}</p>
      </div>
      <div class="onb-foot">
        <div class="onb-dots">
          ${ONBOARD_STEPS.map((_, n) => `<span class="onb-dot ${n === (state.onboardStep || 0) ? 'active' : ''}"></span>`).join('')}
        </div>
        <button class="onb-cta" id="onb-next" type="button">${last ? 'Start recording' : 'Next'}</button>
        <button class="onb-skip" id="onb-skip" type="button">Skip</button>
      </div>
    </div>`;

  $('#onb-next').addEventListener('click', () => {
    if (last) { finishOnboarding(); return; }
    state.onboardStep = (state.onboardStep || 0) + 1;
    renderOnboarding();
  });
  $('#onb-skip').addEventListener('click', finishOnboarding);
}

function finishOnboarding() {
  state.onboarded = true;
  state.onboardStep = 0;
  state.page = 'HOME';
  savePrefs();
  render();
  toast('Welcome to DualShots');
}

/* ------------------------------------------------------------------ nav */

function go(page) {
  state.page = page;
  state.record = false;
  render();
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
  render();
}

init();
