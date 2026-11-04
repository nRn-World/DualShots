/* ==========================================================================
   DuelShots — interactive web preview
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
    desc: 'Both cameras composed into one video — back feed full frame, front feed inset.',
  },
  SEPARATE: {
    title: 'Separate Videos',
    desc: 'Two files, one per camera. Trim and post each angle independently.',
  },
  BOTH: {
    title: 'Both Options',
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
  mode: 'BOTH',
  frontAspect: 'R_9_16',
  backAspect: 'R_16_9',
  quality: 'FHD1080',
  codec: 'MP4',
  premium: false,
  audio: true,
  zoom: 1,
  stack: 'front-first',
  pip: { cx: 0.74, cy: 0.24, scale: 0.34 },
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
  camera: svg('<rect x="2.8" y="6.6" width="12.4" height="10.8" rx="3.2"/><path d="m15.2 12 6-3.6v7.2l-6-3.6z"/>'),
  video: svg('<rect x="2.8" y="6.6" width="12.4" height="10.8" rx="3.2"/><path d="m15.2 12 6-3.6v7.2l-6-3.6z"/>'),
  gallery: svg('<rect x="3.2" y="4.4" width="17.6" height="15.2" rx="4"/><circle cx="8.6" cy="9.6" r="1.5" fill="currentColor" stroke="none"/><path d="m4.4 17.6 4.6-4.3a2 2 0 0 1 2.7 0l5 4.6"/><path d="m14.2 15.2 1.9-1.8a2 2 0 0 1 2.7 0l1.6 1.4"/>'),
  gear: svg('<circle cx="12" cy="12" r="3"/><path d="M19.3 14.9a1.6 1.6 0 0 0 .3 1.8l.06.06a2 2 0 1 1-2.8 2.8l-.06-.06a1.6 1.6 0 0 0-1.8-.32 1.6 1.6 0 0 0-.96 1.5V21a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1.06-1.5 1.6 1.6 0 0 0-1.8.32l-.06.06a2 2 0 1 1-2.8-2.8l.06-.06a1.6 1.6 0 0 0 .32-1.8 1.6 1.6 0 0 0-1.5-.96H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9.1a1.6 1.6 0 0 0-.32-1.8l-.06-.06a2 2 0 1 1 2.8-2.8l.06.06a1.6 1.6 0 0 0 1.8.32H9a1.6 1.6 0 0 0 .96-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 .96 1.5 1.6 1.6 0 0 0 1.8-.32l.06-.06a2 2 0 1 1 2.8 2.8l-.06.06a1.6 1.6 0 0 0-.32 1.8V9a1.6 1.6 0 0 0 1.5.96H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.6.94z"/>'),
  crown: svg('<path d="M3.4 18.4h17.2l1.1-11-5.4 3.4L12 4.6 7.7 10.8 2.3 7.4z"/>'),
  info: svg('<circle cx="12" cy="12" r="8.6"/><path d="M12 11v5.4"/><circle cx="12" cy="7.9" r="1" fill="currentColor" stroke="none"/>'),
  film: svg('<rect x="2.8" y="5" width="18.4" height="14" rx="3"/><path d="M7 5v14M17 5v14M2.8 12h18.4"/>'),
  mic: svg('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.6 11.4a6.4 6.4 0 0 0 12.8 0"/><path d="M12 17.8V21"/>'),
  folder: svg('<path d="M3 7.4a2 2 0 0 1 2-2h3.6l2 2.4H19a2 2 0 0 1 2 2v8.2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>'),
  close: svg('<path d="M6.8 6.8l10.4 10.4M17.2 6.8 6.8 17.2"/>', 'stroke-width="2"'),
  flip: svg('<path d="M20 12a8 8 0 1 1-2.4-5.7"/><path d="M20.4 4.7v5.1h-5.1"/><circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/>'),
  swap: svg('<path d="M4.5 8.5h13l-3-3"/><path d="M19.5 15.5h-13l3 3"/>'),
  chevronRight: svg('<path d="M9.4 6.2 15.2 12l-5.8 5.8"/>', 'stroke-width="2"'),
  chevronLeft: svg('<path d="M14.6 6.2 8.8 12l5.8 5.8"/>', 'stroke-width="2"'),
  chevronDown: svg('<path d="m6.5 9.5 5.5 5.5 5.5-5.5"/>', 'stroke-width="2.1"'),
  check: svg('<path d="M4.5 12.6 9.4 17.5 19.5 6.9"/>', 'stroke-width="2.2"'),
  play: svg('<path d="M8.4 5.6v12.8L19 12z" fill="currentColor" stroke="none"/>', 'stroke-width="0"'),
  download: svg('<path d="M12 4v11"/><path d="m7.8 11.4 4.2 4.2 4.2-4.2"/><path d="M4.8 19.4h14.4"/>'),
  trash: svg('<path d="M5 7h14M9.5 7V5.4h5V7M6.6 7l.8 12.1A1.9 1.9 0 0 0 9.3 21h5.4a1.9 1.9 0 0 0 1.9-1.9L17.4 7"/>'),
  bolt: svg('<path d="M13.4 2.6 5.6 13.4h5.2l-1.2 8 7.8-10.8h-5.2z"/>'),
  aperture: svg('<circle cx="12" cy="12" r="8.6"/><circle cx="12" cy="12" r="3.4"/><path d="M12 3.4 8 10M20 8.6l-7 1M17.6 19l-4-6.4M6.4 19l4-6.6M4 8.6l6.6 1"/>'),
};

function logoMark(size = 108, cls = 'logo-lg') {
  return `
    <svg class="${cls}" viewBox="0 0 120 120" width="${size}" height="${size}" aria-hidden="true">
      <defs>
        <linearGradient id="duelGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#FFC24B"/>
          <stop offset=".45" stop-color="#FF7A1A"/>
          <stop offset="1" stop-color="#FF4D00"/>
        </linearGradient>
        <linearGradient id="duelGradSoft" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#FFB020" stop-opacity=".85"/>
          <stop offset="1" stop-color="#FF4D00" stop-opacity=".2"/>
        </linearGradient>
      </defs>

      <!-- orbit -->
      <path d="M32 22.5A52 44 0 0 1 100 44" fill="none" stroke="url(#duelGrad)" stroke-width="5"
            stroke-linecap="round" opacity=".9"/>
      <path d="M88 96A52 44 0 0 1 22 74" fill="none" stroke="url(#duelGradSoft)" stroke-width="4"
            stroke-linecap="round"/>

      <!-- lens -->
      <circle cx="52" cy="60" r="30" fill="#0b0806" stroke="url(#duelGrad)" stroke-width="5"/>
      <circle cx="52" cy="60" r="20" fill="#1b1310"/>
      <circle cx="52" cy="60" r="20" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="1.5"/>
      <circle cx="45" cy="52" r="7" fill="#FFC24B" opacity=".9"/>
      <circle cx="59" cy="68" r="3.4" fill="#FF7A1A" opacity=".55"/>

      <!-- play -->
      <path d="M78 44.5 108 60 78 75.5z" fill="url(#duelGrad)"/>
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

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

/* overlay geometry */

const PIP_MIN = 0.16;
const PIP_MAX = 0.62;
const PIP_DEFAULT = { cx: 0.74, cy: 0.24, scale: 0.34 };

const currentPip = () => state.pip || PIP_DEFAULT;

function pipRect(w, h, aspectKey, pip) {
  const a = ASPECTS[aspectKey];
  let pw = w * clamp(pip.scale, PIP_MIN, PIP_MAX);
  let ph = pw * (a.h / a.w);
  const maxH = h * 0.82;
  if (ph > maxH) {
    ph = maxH;
    pw = ph * (a.w / a.h);
  }
  const x = clamp(pip.cx * w - pw / 2, 0, Math.max(0, w - pw));
  const y = clamp(pip.cy * h - ph / 2, 0, Math.max(0, h - ph));
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
  wireSheet();
  bind && bind();
}

function wireSheet() {
  $$('[data-close]', modalRoot).forEach((b) => b.addEventListener('click', closeSheet));
}

function proFeatures() {
  return ['Advanced formats', 'Custom resolution', 'HEVC encoding', 'No watermark'];
}

function showPro(pending = null) {
  const body = state.premium
    ? `<p class="sheet-sub">Premium is active. 4K, HEVC and every format control are unlocked.</p>
       <button class="btn-primary sole" data-close type="button">Close</button>`
    : `<p class="sheet-sub">One-time unlock. Everything below stays on this device — no account, no subscription.</p>
       <ul class="premium-list" style="margin-top:16px">
         ${proFeatures().map((f) => `<li class="premium-item">${ICONS.check}<span>${f}</span></li>`).join('')}
       </ul>
       <button class="btn-primary sole" data-upgrade type="button">Unlock Premium</button>
       <button class="btn-ghost" data-close type="button">Not now</button>`;

  openSheet(`
    <div class="grabber"></div>
    <div class="pro-sheet-head">
      <span class="pro-sheet-mark">${ICONS.crown}</span>
      <div>
        <div class="sheet-title">DuelShots Premium</div>
        <div class="caption">Mock purchase in this preview</div>
      </div>
    </div>
    ${body}`, () => {
    const up = $('[data-upgrade]', modalRoot);
    if (up) up.addEventListener('click', () => {
      state.premium = true;
      if (pending && pending.kind === 'quality' && QUALITIES[pending.key]) state.quality = pending.key;
      if (pending && pending.kind === 'codec' && CODECS[pending.key]) state.codec = pending.key;
      savePrefs();
      closeSheet();
      toast('Premium unlocked');
      if (state.record) refreshRecord(); else render();
    });
  });
}

function showFormatSheet(which) {
  const isFront = which === 'front';

  const body = () => `
    <div class="grabber"></div>
    <div class="sheet-title">${isFront ? 'Front' : 'Back'} Camera Format</div>
    <p class="sheet-sub">How this camera is framed in the recording.</p>
    <div class="chips">
      ${ASPECT_ORDER.map((k) => `
        <button class="chip ${k === (isFront ? state.frontAspect : state.backAspect) ? 'active' : ''}"
                data-aspect="${k}" type="button">${ASPECTS[k].label}</button>
      `).join('')}
    </div>
    <button class="btn-primary" data-close type="button">Done</button>`;

  const bind = () => {
    wireSheet();
    $$('[data-aspect]', modalRoot).forEach((b) => b.addEventListener('click', () => {
      if (isFront) state.frontAspect = b.dataset.aspect;
      else state.backAspect = b.dataset.aspect;
      savePrefs();
      $('.sheet', modalRoot).innerHTML = body();
      bind();
      if (!state.record) render();
    }));
  };

  openSheet(body(), bind);
}

function showModeSheet() {
  const html = `
    <div class="grabber"></div>
    <div class="sheet-title">Save as</div>
    <p class="sheet-sub">What each take writes to the gallery.</p>
    <div class="option-list">
      ${MODE_ORDER.map((m) => `
        <button class="option ${state.mode === m ? 'active' : ''}" data-mode="${m}" type="button">
          <span>
            <span class="t">${MODES[m].title}</span>
            <span class="d">${MODES[m].desc}</span>
          </span>
          <span class="tick">${ICONS.check}</span>
        </button>
      `).join('')}
    </div>
    <button class="btn-primary" data-close type="button">Done</button>`;

  openSheet(html);
  $$('[data-mode]', modalRoot).forEach((b) => b.addEventListener('click', () => {
    state.mode = b.dataset.mode;
    savePrefs();
    $$('[data-mode]', modalRoot).forEach((x) => x.classList.toggle('active', x.dataset.mode === state.mode));
    if (!state.record) render();
  }));
}

function showOptionSheet({ title, sub, items, activeKey, onPick }) {
  const html = `
    <div class="grabber"></div>
    <div class="sheet-title">${title}</div>
    ${sub ? `<p class="sheet-sub">${sub}</p>` : ''}
    <div class="option-list">
      ${items.map((i) => `
        <button class="option ${i.key === activeKey ? 'active' : ''}" data-opt="${i.key}" type="button">
          <span>
            <span class="t">${i.label}</span>
            ${i.desc ? `<span class="d">${i.desc}</span>` : ''}
          </span>
          <span class="tick">${ICONS.check}</span>
        </button>
      `).join('')}
    </div>
    <button class="btn-primary" data-close type="button">Done</button>`;

  openSheet(html);
  $$('[data-opt]', modalRoot).forEach((b) => b.addEventListener('click', () => {
    onPick(b.dataset.opt);
  }));
}

function showAbout() {
  const wrap = document.createElement('div');
  wrap.className = 'about-modal';
  wrap.innerHTML = `
    <div class="about-card">
      <h2>DuelShots · web preview</h2>
      <p>This is a browser build of the native Android app in this repository. The screens,
      flows, recording modes and premium gating come from the Kotlin/Compose source in
      <code>app/src/main/java/com/nrworld/duelshot/</code>.</p>

      <h3>Build</h3>
      <p>v3 — orange menu design with a centred logo, one primary action and a settings list.
      Format controls live on the record screen and in Settings. Last updated 2026-11-04.</p>

      <h3>What works here</h3>
      <ul>
        <li>All three recording modes: composed picture-in-picture, two separate files, or both.</li>
        <li>Clips are encoded live in the browser, land in the Gallery, play inline and download.</li>
        <li>Settings persist in <code>localStorage</code> (SharedPreferences in the app).</li>
        <li>4K and HEVC sit behind the mock premium purchase, like <code>grantPremium()</code>.</li>
        <li>In Dual View the front camera inset can be dragged, pinched and reset.</li>
      </ul>

      <h3>Cameras</h3>
      <p>Browsers usually allow only one camera stream at a time. If both cameras open you get a
      real dual feed; otherwise the preview falls back to single-camera mode, exactly like the
      Android fallback. If camera access is blocked an animated demo feed keeps recording usable.</p>

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

function onPreviewPointerDown(e) {
  if (!recDom.pip) return;
  const onPip = !!(e.target.closest && e.target.closest('.pip'));
  gesture.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

  if (gesture.pointers.size === 1) {
    if (!onPip) return;
    const p = currentPip();
    gesture.drag = { id: e.pointerId, x: e.clientX, y: e.clientY, cx: p.cx, cy: p.cy };
    gesture.pinch = null;
    gesture.moved = false;
    gesture.multi = false;
    recDom.pip.classList.add('active');
    hidePipHint();
    try { recDom.preview.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
  } else if (gesture.pointers.size === 2) {
    gesture.pinch = { dist: pointerDistance(), scale: currentPip().scale };
    gesture.drag = null;
    gesture.multi = true;
    recDom.pip.classList.add('active');
    hidePipHint();
  }
}

function onPreviewPointerMove(e) {
  if (!gesture.pointers.has(e.pointerId) || !recDom.pip) return;
  gesture.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

  const tile = recDom.pip.parentElement;
  if (!tile) return;
  const rect = tile.getBoundingClientRect();
  if (!rect.width || !rect.height) return;

  if (gesture.pointers.size >= 2 && gesture.pinch && gesture.pinch.dist > 0) {
    const scale = clamp(gesture.pinch.scale * (pointerDistance() / gesture.pinch.dist), PIP_MIN, PIP_MAX);
    state.pip = clampPipToStage({ ...currentPip(), scale }, tile, state.frontAspect);
    applyPipStyle();
    return;
  }

  if (gesture.drag && gesture.drag.id === e.pointerId) {
    const dx = e.clientX - gesture.drag.x;
    const dy = e.clientY - gesture.drag.y;
    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) gesture.moved = true;
    const cx = gesture.drag.cx + dx / rect.width;
    const cy = gesture.drag.cy + dy / rect.height;
    state.pip = clampPipToStage({ ...currentPip(), cx, cy }, tile, state.frontAspect);
    applyPipStyle();
  }
}

function onPreviewPointerUp(e) {
  if (!gesture.pointers.has(e.pointerId)) return;

  const onPip = !!(e.target.closest && e.target.closest('.pip'));
  const wasMulti = gesture.pointers.size > 1 || gesture.multi;
  const dragged = gesture.moved;

  gesture.pointers.delete(e.pointerId);

  if (gesture.pointers.size === 1) {
    const [id, point] = [...gesture.pointers.entries()][0];
    const cur = currentPip();
    gesture.drag = { id, x: point.x, y: point.y, cx: cur.cx, cy: cur.cy };
    gesture.pinch = null;
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
  if (!recDom.pip || !(e.target.closest && e.target.closest('.pip'))) return;
  e.preventDefault();
  const next = clamp(currentPip().scale * (1 - e.deltaY * 0.0012), PIP_MIN, PIP_MAX);
  state.pip = { ...currentPip(), scale: next };
  applyPipStyle();
  savePrefs();
}

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
  btn: null, label: null, flip: null, thumb: null, pip: null, hint: null, zoom: null,
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
  const zoom = state.zoom || 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);

  if (o.key === 'composed') {
    const back = cam.feeds.back;
    const front = cam.feeds.front;
    if (back) drawCover(ctx, back.el, w, h, back.mirror, zoom);
    if (front) {
      const aspectKey = (rec.take && rec.take.frontAspect) || state.frontAspect;
      const g = pipRect(w, h, aspectKey, currentPip());
      const radius = Math.round(Math.min(g.w, g.h) * 0.07);
      ctx.save();
      roundRect(ctx, g.x, g.y, g.w, g.h, radius);
      ctx.clip();
      ctx.fillStyle = '#000';
      ctx.fillRect(g.x, g.y, g.w, g.h);
      drawCover(ctx, front.el, g.w, g.h, front.mirror, zoom);
      ctx.restore();
      ctx.strokeStyle = 'rgba(255,255,255,.55)';
      ctx.lineWidth = Math.max(2, Math.round(w / 340));
      roundRect(ctx, g.x, g.y, g.w, g.h, radius);
      ctx.stroke();
    }
    return;
  }

  const feed = cam.feeds[o.key];
  if (feed) drawCover(ctx, feed.el, w, h, feed.mirror, zoom);
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
  };
  rec.take = take;

  const stamp = stampNow();
  const single = cam.singleFallback || !cam.feeds.back || !cam.feeds.front;
  const outs = [];

  if (single) {
    const key = cam.active;
    const o = makeOutput(key, `duelshot_${key}_${stamp}`,
      key === 'back' ? take.backAspect : take.frontAspect, true);
    if (o) outs.push(o);
  } else if (take.mode === 'DUAL_VIEW') {
    const o = makeOutput('composed', `duelshot_combined_${stamp}`, take.backAspect, true);
    if (o) outs.push(o);
  } else if (take.mode === 'SEPARATE') {
    const b = makeOutput('back', `duelshot_back_${stamp}`, take.backAspect, true);
    const f = makeOutput('front', `duelshot_front_${stamp}`, take.frontAspect, false);
    if (b) outs.push(b);
    if (f) outs.push(f);
  } else {
    const c = makeOutput('composed', `duelshot_combined_${stamp}`, take.backAspect, true);
    const b = makeOutput('back', `duelshot_back_${stamp}`, take.backAspect, false);
    const f = makeOutput('front', `duelshot_front_${stamp}`, take.frontAspect, false);
    [c, b, f].forEach((o) => o && outs.push(o));
  }

  if (!outs.length) {
    const feed = cam.feeds[cam.active] || cam.feeds.back || cam.feeds.front;
    const src = feed && feed.el && feed.el.srcObject;
    if (src instanceof MediaStream) {
      const o = makeOutput(cam.active, `duelshot_${cam.active}_${stamp}`,
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
        <button class="icon-btn" id="rec-close" type="button" aria-label="Close">${ICONS.close}</button>
        <span class="timer"><span class="dot"></span><span id="rec-timer">00:00</span></span>
        <span class="spacer"></span>
        <button class="mode-pill" id="rec-mode" type="button" aria-label="Camera setup">
          <span id="rec-mode-label">Both</span>
          <span class="caret">${ICONS.chevronDown}</span>
        </button>
      </div>

      <div class="rec-preview" id="rec-preview">
        <div class="feeds" id="rec-feeds"></div>
        <button class="torch" id="rec-torch" type="button" aria-label="Torch">${ICONS.bolt}</button>
      </div>

      <div class="rec-dock">
        <div class="rec-status" id="rec-status">
          <span id="rec-line"></span>
          <span class="formats" id="rec-formats"></span>
          <span class="saved" id="rec-saved"></span>
        </div>
        <div class="rec-controls">
          <button class="zoom-chip" id="rec-zoom" type="button" aria-label="Zoom">1×</button>
          <button class="shutter" id="rec-btn" type="button" aria-label="Record"><i></i></button>
          <button class="dock-btn" id="rec-flip" type="button" aria-label="Switch camera">${ICONS.flip}</button>
        </div>
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
  recDom.btn = $('#rec-btn');
  recDom.label = $('#rec-mode-label');
  recDom.flip = $('#rec-flip');
  recDom.zoom = $('#rec-zoom');

  $('#rec-close').addEventListener('click', async () => {
    if (rec.active) await stopRecording();
    closeRecord();
  });
  recDom.flip.addEventListener('click', switchCamera);
  $('#rec-mode').addEventListener('click', mountCameraSheet);
  recDom.btn.addEventListener('click', () => {
    if (rec.active) stopRecording();
    else startRecording();
  });
  recDom.zoom.addEventListener('click', () => {
    state.zoom = state.zoom === 1 ? 2 : state.zoom === 2 ? 3 : 1;
    applyZoom();
    toast(`Zoom ${state.zoom}×`);
  });
  $('#rec-torch').addEventListener('click', toggleTorch);

  attachPreviewGestures();
  applyZoom();
}

let torchOn = false;

async function toggleTorch() {
  const btn = $('#rec-torch');
  const feed = cam.feeds[cam.active] || cam.feeds.back || cam.feeds.front;
  const stream = feed && feed.el && feed.el.srcObject;
  const track = stream && stream.getVideoTracks ? stream.getVideoTracks()[0] : null;

  torchOn = !torchOn;
  if (btn) btn.classList.toggle('on', torchOn);

  let supported = false;
  try {
    const caps = track && track.getCapabilities ? track.getCapabilities() : null;
    supported = !!(caps && caps.torch);
    if (supported) await track.applyConstraints({ advanced: [{ torch: torchOn }] });
  } catch (_) { supported = false; }

  if (supported) toast(torchOn ? 'Torch on' : 'Torch off');
  else if (cam.demo) toast('Simulated feed — no torch');
  else toast('This camera has no torch');
}

function applyZoom() {
  if (recDom.feeds) recDom.feeds.style.setProperty('--zoom', state.zoom);
  if (recDom.zoom) {
    recDom.zoom.textContent = `${state.zoom}×`;
    recDom.zoom.classList.toggle('active', state.zoom > 1);
  }
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
  d.innerHTML = `${ICONS.camera}<span>${esc(text)}</span>`;
  return d;
}

function mountFeeds() {
  const stage = recDom.feeds;
  if (!stage) return;
  stage.innerHTML = '';
  recDom.pip = null;
  recDom.hint = null;
  mountedKey = layoutKey();

  if (!cam.ready) {
    const tile = makeTile(null);
    tile.appendChild(placeholder('Starting cameras'));
    stage.appendChild(tile);
    return;
  }

  const hasBoth = !!(cam.feeds.back && cam.feeds.front);
  const single = cam.singleFallback || !hasBoth;
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
    const tile = makeTile(null);
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
      const a = ASPECTS[take.frontAspect];
      pip.style.setProperty('--pip-ratio', `${a.w} / ${a.h}`);
      pip.appendChild(front.el);
      tile.appendChild(pip);
      recDom.pip = pip;
      applyPipStyle();

      const hint = document.createElement('div');
      hint.className = 'pip-hint';
      hint.textContent = 'Drag to move · pinch to resize';
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
  swap.innerHTML = ICONS.swap;
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
  return [cam.ready, cam.singleFallback, cam.active, t.mode, t.frontAspect, t.backAspect, state.stack].join('|');
}

function unmountFeeds() {
  Object.values(cam.feeds).forEach((f) => { if (f && f.el) sinkEl.appendChild(f.el); });
}

function recordStatusText() {
  if (recordMessage) return recordMessage;
  if (!cam.ready) return 'Starting cameras…';
  if (cam.demo) return 'Demo feed — camera unavailable here';
  if (cam.singleFallback) return `Single camera · ${cam.active}`;
  return `${MODES[activeTake().mode].title} · front + back`;
}

function formatSummary() {
  const t = activeTake();
  const quality = QUALITIES[t.quality].label;
  const audio = state.audio ? 'audio' : 'muted';
  if (cam.singleFallback) {
    const short = ASPECTS[cam.active === 'back' ? t.backAspect : t.frontAspect].short;
    return `${cam.active === 'back' ? 'Back' : 'Front'} ${short} · ${quality} · ${audio}`;
  }
  return `Front ${ASPECTS[t.frontAspect].short} · Back ${ASPECTS[t.backAspect].short} · ${quality} · ${audio}`;
}

function refreshRecord() {
  if (!state.record || !recDom.root) return;

  if (layoutKey() !== mountedKey) mountFeeds();

  if (recDom.label) recDom.label.textContent = MODES[activeTake().mode].title;
  if (recDom.preview) recDom.preview.classList.toggle('recording', rec.active);
  if (recDom.root) recDom.root.classList.toggle('recording', rec.active);
  if (recDom.btn) {
    recDom.btn.classList.toggle('on', rec.active);
    recDom.btn.disabled = !cam.ready;
  }
  if (recDom.timer) {
    recDom.timer.textContent = rec.active ? shortTime(Date.now() - rec.startedAt) : '00:00';
  }
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
    ${locked ? '<div class="sheet-note">Recording — new formats start with the next take</div>' : ''}

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
        <button class="chip ${k === state.frontAspect ? 'active' : ''}" data-recfront="${k}" type="button">${ASPECTS[k].label}</button>
      `).join('')}
    </div>

    <div class="section-title">Back camera</div>
    <div class="chips">
      ${ASPECT_ORDER.map((k) => `
        <button class="chip ${k === state.backAspect ? 'active' : ''}" data-recback="${k}" type="button">${ASPECTS[k].label}</button>
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
    if (rec.active) { toast('Mode is locked while recording'); return; }
    state.mode = b.dataset.recmode;
    savePrefs();
    mountCameraSheet();
    refreshRecord();
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
  if (state.record) return;
  document.body.dataset.screen = 'page';
  if (state.page === 'SETTINGS') renderSettings();
  else if (state.page === 'GALLERY') renderGallery();
  else renderHome();
}

function renderHome() {
  screenEl.innerHTML = `
    <div class="menu">
      <div class="menu-top">
        <span></span>
        <button class="icon-btn" id="home-settings" type="button" aria-label="Settings">${ICONS.gear}</button>
      </div>

      <div class="menu-hero">
        ${logoMark(108)}
        <h1 class="wordmark">Duel<span>Shots</span></h1>
        <p class="tagline">Record both sides. At once.</p>
      </div>

      <button class="btn-primary" id="start-btn" type="button">
        ${ICONS.camera}
        <span class="grow">Start Recording</span>
        <span class="chev">${ICONS.chevronRight}</span>
      </button>
      <p class="cta-meta">${MODES[state.mode].title} · ${QUALITIES[state.quality].label} · ${ASPECTS[state.frontAspect].short} + ${ASPECTS[state.backAspect].short}</p>

      <div class="menu-list">
        <button class="menu-row" data-go="GALLERY" type="button">
          <span class="menu-ico">${ICONS.gallery}</span>
          <span class="label">Gallery</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <button class="menu-row" data-go="SETTINGS" type="button">
          <span class="menu-ico">${ICONS.gear}</span>
          <span class="label">Settings</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <button class="menu-row gold" id="menu-premium" type="button">
          <span class="menu-ico">${ICONS.crown}</span>
          <span class="label">${state.premium ? 'Premium active' : 'Premium'}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <button class="menu-row" id="menu-about" type="button">
          <span class="menu-ico">${ICONS.info}</span>
          <span class="label">About</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
      </div>
    </div>`;

  $('#start-btn').addEventListener('click', openRecord);
  $('#home-settings').addEventListener('click', () => go('SETTINGS'));
  $('#menu-premium').addEventListener('click', () => showPro());
  $('#menu-about').addEventListener('click', showAbout);
  $$('[data-go]', screenEl).forEach((b) => b.addEventListener('click', () => go(b.dataset.go)));
}

function backHead(title) {
  return `
    <div class="page-head">
      <button class="icon-btn" data-back type="button" aria-label="Back">${ICONS.chevronLeft}</button>
      <span class="page-title">${title}</span>
    </div>`;
}

function renderSettings() {
  screenEl.innerHTML = `
    <div class="page enter">
      ${backHead('Settings')}

      <div class="section-title">Camera Settings</div>
      <div class="group">
        <button class="row" data-front-row type="button">
          <span class="row-ico">${ICONS.camera}</span>
          <span class="label">Front Camera Format</span>
          <span class="val">${ASPECTS[state.frontAspect].short}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <button class="row" data-back-row type="button">
          <span class="row-ico">${ICONS.camera}</span>
          <span class="label">Back Camera Format</span>
          <span class="val">${ASPECTS[state.backAspect].short}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
      </div>

      <div class="section-title">Recording Options</div>
      <div class="group">
        <button class="row" data-mode-row type="button">
          <span class="row-ico">${ICONS.film}</span>
          <span class="label">Save as</span>
          <span class="val">${MODES[state.mode].title}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <div class="row">
          <span class="row-ico">${ICONS.mic}</span>
          <span class="label">Enable Audio</span>
          <button class="switch" id="audio-switch" type="button" role="switch"
                  aria-checked="${state.audio}" aria-label="Enable audio"></button>
        </div>
        <div class="row">
          <span class="row-ico">${ICONS.folder}</span>
          <span class="label">Save Location</span>
          <span class="val">Phone Storage</span>
        </div>
      </div>

      <div class="section-title">Video</div>
      <div class="group">
        <button class="row" data-quality-row type="button">
          <span class="row-ico">${ICONS.aperture}</span>
          <span class="label">Resolution</span>
          <span class="val">${QUALITIES[state.quality].label}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
        <button class="row" data-codec-row type="button">
          <span class="row-ico">${ICONS.bolt}</span>
          <span class="label">Container</span>
          <span class="val">${CODECS[state.codec].label}</span>
          <span class="chev">${ICONS.chevronRight}</span>
        </button>
      </div>

      <div class="premium-card">
        <div class="premium-head">${ICONS.crown}Premium Features</div>
        <div class="premium-list">
          ${proFeatures().map((f) => `<div class="premium-item">${ICONS.check}<span>${f}</span></div>`).join('')}
        </div>
        ${state.premium
      ? `<div class="premium-active">${ICONS.check}<span>Premium is active</span></div>`
      : `<button class="btn-primary" id="settings-premium" type="button" style="margin-top:16px">
             ${ICONS.crown}<span class="grow">Go Premium</span>
           </button>`}
      </div>
    </div>`;

  $('[data-back]').addEventListener('click', () => go('HOME'));
  $('[data-front-row]').addEventListener('click', () => showFormatSheet('front'));
  $('[data-back-row]').addEventListener('click', () => showFormatSheet('back'));
  $('[data-mode-row]').addEventListener('click', showModeSheet);

  $('#audio-switch').addEventListener('click', (e) => {
    state.audio = !state.audio;
    e.currentTarget.setAttribute('aria-checked', String(state.audio));
    savePrefs();
    toast(state.audio ? 'Audio on' : 'Audio off');
  });

  $('[data-quality-row]').addEventListener('click', () => {
    showOptionSheet({
      title: 'Resolution',
      sub: '4K needs Premium.',
      activeKey: state.quality,
      items: QUALITY_ORDER.map((k) => ({
        key: k,
        label: QUALITIES[k].label,
        desc: QUALITIES[k].premium ? 'Premium' : '',
      })),
      onPick: (key) => {
        if (QUALITIES[key].premium && !state.premium) { closeSheet(); showPro({ kind: 'quality', key }); return; }
        state.quality = key;
        savePrefs();
        closeSheet();
        renderSettings();
      },
    });
  });

  $('[data-codec-row]').addEventListener('click', () => {
    showOptionSheet({
      title: 'Container',
      sub: 'HEVC needs Premium.',
      activeKey: state.codec,
      items: CODEC_ORDER.map((k) => ({
        key: k,
        label: CODECS[k].label,
        desc: CODECS[k].premium ? 'Premium' : '',
      })),
      onPick: (key) => {
        if (CODECS[key].premium && !state.premium) { closeSheet(); showPro({ kind: 'codec', key }); return; }
        state.codec = key;
        savePrefs();
        closeSheet();
        renderSettings();
      },
    });
  });

  const premiumBtn = $('#settings-premium');
  if (premiumBtn) premiumBtn.addEventListener('click', () => showPro());
}

function renderGallery() {
  screenEl.innerHTML = `
    <div class="page enter">
      ${backHead('Gallery')}
      <p class="sub" style="margin:10px 2px 0;font-size:12.5px;color:var(--text-3)">
        ${state.clips.length
      ? `${state.clips.length} clip${state.clips.length === 1 ? '' : 's'} this session · Movies/DuelShots`
      : 'Recordings are saved to Movies/DuelShots'}
      </p>

      ${state.clips.length ? `<div class="gallery-grid">${state.clips.map(clipCardHTML).join('')}</div>` : `
        <div class="empty">
          <div class="empty-ico">${ICONS.camera}</div>
          <h3>No recordings yet</h3>
          <p>Your first take lands here — composed, separate, or both.</p>
          <button class="btn-primary" id="empty-start" type="button">
            ${ICONS.camera}<span class="grow">Start recording</span><span class="chev">${ICONS.chevronRight}</span>
          </button>
        </div>`}
    </div>`;

  $('[data-back]').addEventListener('click', () => go('HOME'));
  const empty = $('#empty-start');
  if (empty) empty.addEventListener('click', openRecord);

  $$('.clip', screenEl).forEach((el) => el.addEventListener('click', () => {
    const clip = state.clips.find((c) => c.id === el.dataset.clip);
    if (clip) playClip(clip);
  }));
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
      if (state.page === 'GALLERY') renderGallery();
    });
  });
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
  $('#about-btn').addEventListener('click', showAbout);
  render();
}

init();
