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

/* Shape of the composed Dual View file; AUTO follows the back camera. */
const SAVE_ORDER = ['AUTO', 'R_9_16', 'R_16_9', 'R_1_1'];
const saveLabel = (k) => (k === 'AUTO' ? 'Auto' : ASPECTS[k].short);

/* Aspect the composed (Dual View) file is written in. */
function composedAspect(take) {
  const t = take || state;
  return t.saveAspect && t.saveAspect !== 'AUTO' ? t.saveAspect : t.backAspect;
}

const QUALITY_ORDER = ['HD720', 'FHD1080', 'UHD4K'];

const QUALITIES = {
  HD720: { label: '720p', long: 1280 },
  FHD1080: { label: '1080p', long: 1920 },
  UHD4K: { label: '4K', long: 3840 },
};

const CODEC_ORDER = ['MP4', 'HEVC'];

const CODECS = {
  MP4: { label: 'MP4' },
  HEVC: { label: 'HEVC' },
};

const STORE_KEY = 'duelshot_prefs';

const PIP_STYLES = ['PIP', 'CIRCLE', 'SPLIT'];
const PIP_STYLE_LABEL = { PIP: 'PiP', CIRCLE: 'Circle', SPLIT: '50/50' };

const state = {
  page: 'HOME',
  record: false,
  mode: 'DUAL_VIEW',
  frontAspect: 'R_9_16',
  backAspect: 'R_16_9',
  quality: 'FHD1080',
  codec: 'MP4',
  audio: true,
  grid: true,
  countdownSec: 0,
  pipStyle: 'PIP',
  favorites: [],
  galleryFilter: 'ALL',
  gallerySearchOpen: false,
  galleryQuery: '',
  saveAspect: 'AUTO',
  zoom: { main: 1, inset: 1 },
  stack: 'front-first',
  pip: { cx: 0.74, cy: 0.30, scale: 0.34 },
  pipOff: false,
  pipPaused: false,
  mainPaused: false,
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
  user: svg('<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>'),
  search: svg('<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>'),
  dots: svg('<circle cx="12" cy="5" r="1.7" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.7" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.7" fill="currentColor" stroke="none"/>'),
  layers: svg('<path d="M12 3 3 8l9 5 9-5z"/><path d="m3 13 9 5 9-5"/>'),
  gear: svg('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>'),
  bulb: svg('<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"/>'),
  photo: svg('<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="10" r="1.8"/><path d="m21 16-5-5-8 8"/>'),
  heart: svg('<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>'),
  cloud: svg('<path d="M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 9.5a4 4 0 0 1 .5 8.5z"/><path d="M12 12v6M9.5 14.5 12 12l2.5 2.5"/>'),
  hd: svg('<rect x="3" y="5" width="18" height="14" rx="3"/><text x="12" y="15" text-anchor="middle" font-size="7.5" font-weight="700" fill="currentColor" stroke="none" font-family="Segoe UI, Roboto, sans-serif">HD</text>'),
  file: svg('<path d="M7 3h7l5 5v13H7z"/><path d="M14 3v5h5"/>'),
  stabilize: svg('<path d="M12 3v18M3.5 12h5M15.5 12h5M5.5 6.5l2.5 2.5M18.5 6.5 16 9M5.5 17.5 8 15M18.5 17.5 16 15"/><rect x="10" y="9.5" width="4" height="5" rx="1" fill="currentColor"/>'),
  audio: svg('<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11"/>'),
  pin: svg('<path d="M12 21s-6-5.2-6-10.2a6 6 0 0 1 12 0C18 15.8 12 21 12 21z"/><circle cx="12" cy="11" r="2.2"/>'),
  mountain: svg('<path d="M3 19 9.5 9l4 6 2.5-3.5L21 19z"/>'),
  calendar: svg('<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>'),
  stats: svg('<path d="M3.5 18.5 9 9.5l4.5 6L20.5 4.5"/><circle cx="3.5" cy="18.5" r="1.4" fill="currentColor"/><circle cx="9" cy="9.5" r="1.4" fill="currentColor"/><circle cx="13.5" cy="15.5" r="1.4" fill="currentColor"/><circle cx="20.5" cy="4.5" r="1.4" fill="currentColor"/>'),
  grid4: svg('<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.6"/>'),
  database: svg('<ellipse cx="12" cy="5.5" rx="7.5" ry="2.8"/><path d="M4.5 5.5v13c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8v-13"/><path d="M4.5 12c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8"/>'),
  film: svg('<rect x="3.5" y="3" width="17" height="18" rx="1.5"/><path d="M7.5 3v18M16.5 3v18M3.5 8h4M3.5 12h4M3.5 16h4M16.5 8h4M16.5 12h4M16.5 16h4"/>'),
  star: svg('<path d="m12 3.2 2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 17l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z"/>'),
  trend: svg('<path d="M3.5 17.5 9 11.5l3.5 3.5 7-8"/><path d="M15 7h4.5v4.5"/><circle cx="3.5" cy="17.5" r="1.3" fill="currentColor"/><circle cx="9" cy="11.5" r="1.3" fill="currentColor"/><circle cx="12.5" cy="15" r="1.3" fill="currentColor"/>'),
  arrowUpRight: svg('<path d="M6 18 18 6M8.5 6H18v9.5"/>'),
  chevDown: svg('<path d="m5 9 7 7 7-7"/>'),
  mute: svg('<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" fill="currentColor"/><path d="m16 9.5 5 5M21 9.5l-5 5"/>'),
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
      audio: state.audio,
      grid: state.grid,
      countdownSec: state.countdownSec,
      pipStyle: state.pipStyle,
      favorites: state.favorites,
      saveAspect: state.saveAspect,
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
    state.swap = !!p.swap;
    state.onboarded = !!p.onboarded;
    if (typeof p.audio === 'boolean') state.audio = p.audio;
    if (typeof p.grid === 'boolean') state.grid = p.grid;
    if (p.countdownSec === 0 || p.countdownSec === 3 || p.countdownSec === 5) state.countdownSec = p.countdownSec;
    if (PIP_STYLES.includes(p.pipStyle)) state.pipStyle = p.pipStyle;
    if (Array.isArray(p.favorites)) state.favorites = p.favorites.filter((x) => typeof x === 'string');
    if (p.saveAspect === 'AUTO' || ASPECTS[p.saveAspect]) state.saveAspect = p.saveAspect;
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

/* AlertDialog from HomeScreen.kt. */
function showAbout() {
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog about-dialog">
        <div class="about-title">DualShots</div>
        <div class="about-version">Version 1.2.8 · Dual lens recording engine</div>
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
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'Camera blocked in this browser — allow camera for this site, then reload';
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'No camera found on this device — demo feed';
  if (name === 'NotReadableError') return 'Camera is busy in another app — demo feed';
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
    // Mobila webbläsare ger ingen kamera på http://<ip> – bara https eller localhost.
    const local = location.protocol === 'https:'
      || ['localhost', '127.0.0.1'].includes(location.hostname);
    enableDemo(local
      ? 'Camera API unavailable in this browser — demo feed'
      : 'Camera needs HTTPS — open this page on the computer, or use the APK on the phone');
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
    v.play().catch(() => { });
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
    v.play().catch(() => { });
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
  const prev = gesture.pointers.get(e.pointerId);
  gesture.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, onPip: prev ? prev.onPip : false });

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
  hide: { back: false, front: false },
  tilePaused: { back: false, front: false },
};

const recDom = {
  root: null, preview: null, feeds: null, status: null, timer: null,
  btn: null, label: null, flip: null, swap: null, thumb: null, pip: null, hint: null, zoom: null,
  pipAspect: null,
  pipBtn: null,
};
let mountedKey = null;
let stackSplit = 0.5;
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

    if (state.pipStyle === 'SPLIT' && small && !state.pipOff) {
      const half = Math.round(h / 2);
      if (big && !state.mainPaused) {
        ctx.save();
        ctx.beginPath(); ctx.rect(0, 0, w, half); ctx.clip();
        drawCover(ctx, big.el, w, half, big.mirror, zoom.main);
        ctx.restore();
      }
      ctx.save();
      ctx.translate(0, half);
      ctx.beginPath(); ctx.rect(0, 0, w, h - half); ctx.clip();
      if (!state.pipPaused) drawCover(ctx, small.el, w, h - half, small.mirror, zoom.inset);
      ctx.restore();
      return;
    }

    if (big && !state.mainPaused) drawBig(ctx, big, bigKey, w, h, zoom.main);
    if (small && !state.pipOff) {
      const isCircle = state.pipStyle === 'CIRCLE';
      const aspectKey = isCircle ? 'R_1_1' : (smallKey === 'back'
        ? ((rec.take && rec.take.backAspect) || state.backAspect)
        : ((rec.take && rec.take.frontAspect) || state.frontAspect));
      const g = pipRect(w, h, aspectKey, currentPip());
      const radius = isCircle ? Math.round(Math.min(g.w, g.h) / 2) : Math.round(Math.min(g.w, g.h) * 0.07);
      ctx.save();
      ctx.translate(g.x, g.y);
      roundRect(ctx, 0, 0, g.w, g.h, radius);
      ctx.clip();
      ctx.fillStyle = '#000';
      ctx.fillRect(0, 0, g.w, g.h);
      if (state.pipPaused) {
        ctx.fillStyle = '#000';
        ctx.fillRect(0, 0, g.w, g.h);
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
  if (feed && !rec.tilePaused[o.key]) drawCover(ctx, feed.el, w, h, feed.mirror, zoom.main);
}

/* Draws the large camera. With an explicit saved format that differs from
   the camera's own, the camera keeps its shape (e.g. 9:16 inside a 16:9
   file) on a blurred, darkened fill of itself instead of being cropped. */
function drawBig(ctx, feed, key, w, h, zoom) {
  const t = rec.take || state;
  const own = ASPECTS[key === 'back' ? t.backAspect : t.frontAspect];
  const explicit = t.saveAspect && t.saveAspect !== 'AUTO';
  const ownRatio = own.w / own.h;
  if (!explicit || Math.abs(ownRatio - w / h) < 0.01) {
    drawCover(ctx, feed.el, w, h, feed.mirror, zoom);
    return;
  }
  ctx.save();
  ctx.filter = `blur(${Math.round(Math.max(w, h) / 40)}px) brightness(.45)`;
  drawCover(ctx, feed.el, w, h, feed.mirror, 1);
  ctx.restore();
  let fw = w;
  let fh = Math.round(w / ownRatio);
  if (fh > h) { fh = h; fw = Math.round(h * ownRatio); }
  ctx.save();
  ctx.translate(Math.round((w - fw) / 2), Math.round((h - fh) / 2));
  ctx.beginPath();
  ctx.rect(0, 0, fw, fh);
  ctx.clip();
  drawCover(ctx, feed.el, fw, fh, feed.mirror, zoom);
  ctx.restore();
}

/* ------------------------------------------------------------- photos */

/* Still photo of what the current mode records; works while filming too. */
async function takePhoto() {
  if (!cam.ready) { toast('Cameras are not ready yet'); return; }
  const t = rec.take || state;
  const stamp = stampNow();
  const single = !cam.feeds.back || !cam.feeds.front;
  const shots = [];
  if (single) shots.push([cam.active, t[cam.active === 'back' ? 'backAspect' : 'frontAspect']]);
  else {
    if (t.mode !== 'SEPARATE') shots.push(['composed', composedAspect(t)]);
    if (t.mode !== 'DUAL_VIEW') shots.push(['back', t.backAspect], ['front', t.frontAspect]);
  }

  const photos = [];
  for (const [key, aspectKey] of shots) {
    const { width, height } = outputSize(aspectKey, t.quality);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const o = { key, canvas, ctx: canvas.getContext('2d') };
    drawOutput(o);
    // eslint-disable-next-line no-await-in-loop
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92));
    if (!blob) continue;
    photos.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      kind: 'photo',
      name: `dualshot_photo_${key === 'composed' ? 'combined' : key}_${stamp}.jpg`,
      sizeMb: mb(blob.size),
      url: URL.createObjectURL(blob),
      mime: 'image/jpeg',
      duration: '',
      when: new Date(),
      mode: t.mode,
      poster: posterFor(o),
    });
  }
  if (!photos.length) { toast('Could not take a photo here'); return; }
  state.clips.unshift(...photos);
  refreshRecord();
  if (recDom.preview) {
    recDom.preview.classList.remove('flash');
    void recDom.preview.offsetWidth;
    recDom.preview.classList.add('flash');
  }
  toast(photos.length === 1 ? 'Photo saved to Gallery' : `${photos.length} photos saved`);
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
    saveAspect: state.saveAspect,
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
    const o = makeOutput('composed', `dualshot_combined_${stamp}`, composedAspect(take), true);
    if (o) outs.push(o);
  } else if (take.mode === 'SEPARATE') {
    const b = makeOutput('back', `dualshot_back_${stamp}`, take.backAspect, true);
    const f = makeOutput('front', `dualshot_front_${stamp}`, take.frontAspect, false);
    if (b) outs.push(b);
    if (f) outs.push(f);
  } else {
    const c = makeOutput('composed', `dualshot_combined_${stamp}`, composedAspect(take), true);
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
  rec.hide = { back: false, front: false };
  rec.tilePaused = { back: false, front: false };

  let lastDraw = 0;
  const loop = (now) => {
    if (!rec.active) return;
    if (now - lastDraw >= 30) {
      lastDraw = now;
      rec.outs.forEach((o) => { if (!o.stopped) drawOutput(o); });
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
  rec.hide = { back: false, front: false };
  rec.tilePaused = { back: false, front: false };
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
  rec.outs.forEach((o) => { if (!o.stopped) { try { o.recorder.pause(); } catch (_) { /* unsupported */ } } });
  recordMessage = 'Paused — resume to continue the same file';
  refreshRecord();
  toast('Paused');
}

function resumeTake() {
  if (!rec.active || !rec.paused) return;
  rec.pausedTotal = (rec.pausedTotal || 0) + (Date.now() - (rec.pausedAt || Date.now()));
  rec.pausedAt = 0;
  rec.paused = false;
  rec.outs.forEach((o) => { if (!o.stopped) { try { o.recorder.resume(); } catch (_) { /* unsupported */ } } });
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

let countdownTimer = 0;
let countdownLeft = 0;

function cancelCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = 0; }
  countdownLeft = 0;
  if (recDom.countdownOverlay) {
    recDom.countdownOverlay.hidden = true;
    recDom.countdownOverlay.textContent = '';
  }
}

function triggerRecordClick() {
  if (countdownLeft > 0) { cancelCountdown(); toast('Countdown cancelled'); return; }
  if (rec.active) { stopRecording(); return; }
  if (state.countdownSec > 0) {
    countdownLeft = state.countdownSec;
    if (recDom.countdownOverlay) {
      recDom.countdownOverlay.textContent = String(countdownLeft);
      recDom.countdownOverlay.hidden = false;
    }
    countdownTimer = setInterval(() => {
      countdownLeft -= 1;
      if (countdownLeft <= 0) {
        cancelCountdown();
        startRecording();
      } else if (recDom.countdownOverlay) {
        recDom.countdownOverlay.textContent = String(countdownLeft);
      }
    }, 1000);
  } else {
    startRecording();
  }
}

function buildRecordShell() {
  screenEl.innerHTML = `
    <div class="record" id="record-root">
      <div class="rec-top">
        <button class="icon-chip" id="rec-close" type="button" aria-label="Close">${ICONS.close}</button>
        <span class="timer"><span class="dot"></span><span id="rec-timer">00:00:00</span></span>
        <button class="icon-chip" id="rec-pause" type="button" aria-label="Pause recording" hidden>${ICONS.pause}</button>
        <span class="spacer"></span>
        <button class="icon-chip" id="rec-audio" type="button" aria-label="Toggle microphone">${state.audio ? ICONS.audio : ICONS.mute}</button>
        <button class="icon-chip rec-timer-chip" id="rec-countdown" type="button" aria-label="Countdown timer">${state.countdownSec ? `${state.countdownSec}s` : '0s'}</button>
        <button class="icon-chip" id="rec-grid" type="button" aria-label="Grid">${ICONS.grid}</button>
        <button class="icon-chip" id="rec-mode" type="button" aria-label="Camera setup">${ICONS.settings}</button>
      </div>

      <div class="rec-preview" id="rec-preview">
        <div class="feeds" id="rec-feeds"></div>
        <div class="rec-grid" id="rec-grid-layer"></div>
        <div class="rec-countdown-overlay" id="rec-countdown-overlay" hidden></div>
      </div>

      <div class="rec-status" id="rec-status">
        <span id="rec-line"></span>
        <span class="formats" id="rec-formats"></span>
        <span class="saved" id="rec-saved"></span>
      </div>

      <div class="rec-controls">
        <button class="zoom-chip" id="rec-zoom" type="button" aria-label="Zoom">1×</button>
        <button class="dock-btn" id="rec-photo" type="button" aria-label="Take photo">${ICONS.camera}</button>
        <button class="shutter" id="rec-btn" type="button" aria-label="Record"><i></i></button>
        <button class="dock-btn" id="rec-flip" type="button" aria-label="Switch camera">${ICONS.flipCam}</button>
        <button class="zoom-chip" id="rec-layout" type="button" aria-label="Dual View layout">${PIP_STYLE_LABEL[state.pipStyle]}</button>
        <button class="dock-btn" id="rec-swap" type="button" aria-label="Swap large and small camera">${ICONS.swap}</button>
        <button class="dock-btn" id="rec-pip" type="button" aria-label="Hide small camera">${ICONS.pip}</button>
        <button class="rec-thumb" id="rec-thumb" type="button" aria-label="Latest recording" hidden></button>
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
  recDom.audioBtn = $('#rec-audio');
  recDom.countdownBtn = $('#rec-countdown');
  recDom.countdownOverlay = $('#rec-countdown-overlay');
  recDom.layoutBtn = $('#rec-layout');
  recDom.thumb = $('#rec-thumb');
  recDom.gridBtn = $('#rec-grid');
  recDom.gridLayer = $('#rec-grid-layer');
  recDom.pauseBtn.addEventListener('click', () => (rec.paused ? resumeTake() : pauseTake()));
  recDom.audioBtn.addEventListener('click', () => {
    if (rec.active) { toast('Audio setting applies to the next take'); return; }
    state.audio = !state.audio;
    savePrefs();
    refreshRecord();
    toast(state.audio ? 'Microphone on' : 'Microphone muted');
  });
  recDom.countdownBtn.addEventListener('click', () => {
    if (rec.active || countdownLeft > 0) return;
    state.countdownSec = state.countdownSec === 0 ? 3 : (state.countdownSec === 3 ? 5 : 0);
    savePrefs();
    refreshRecord();
    toast(state.countdownSec ? `Countdown ${state.countdownSec}s` : 'Countdown off');
  });
  recDom.layoutBtn.addEventListener('click', () => {
    const idx = PIP_STYLES.indexOf(state.pipStyle);
    state.pipStyle = PIP_STYLES[(idx + 1) % PIP_STYLES.length];
    savePrefs();
    mountFeeds();
    refreshRecord();
    toast(`Layout: ${PIP_STYLE_LABEL[state.pipStyle]}`);
  });
  recDom.thumb.addEventListener('click', () => {
    if (rec.active || !state.clips.length) return;
    playClip(state.clips[0]);
  });
  recDom.gridBtn.addEventListener('click', () => {
    state.grid = !state.grid;
    savePrefs();
    applyGrid();
    toast(state.grid ? 'Grid on' : 'Grid off');
  });
  recDom.btn = $('#rec-btn');
  recDom.flip = $('#rec-flip');
  recDom.swap = $('#rec-swap');
  recDom.zoom = $('#rec-zoom');
  recDom.pipBtn = $('#rec-pip');
  recDom.pipBtn.addEventListener('click', () => {
    if (activeTake().mode !== 'DUAL_VIEW') {
      state.mode = 'DUAL_VIEW';
      state.pipOff = false;
      state.pipPaused = false;
      savePrefs();
      if (rec.active) {
        toast('Dual View — applies to the next take');
      } else {
        mountFeeds();
        refreshRecord();
        toast('Dual View — small camera on');
      }
      return;
    }
    state.pipOff = !state.pipOff;
    if (!state.pipOff) state.pipPaused = false;
    mountFeeds();
    refreshRecord();
    toast(state.pipOff ? 'Small camera off — main camera only' : 'Small camera on');
  });

  $('#rec-close').addEventListener('click', async () => {
    cancelCountdown();
    if (rec.active) await stopRecording();
    closeRecord();
  });
  recDom.flip.addEventListener('click', switchCamera);
  recDom.swap.addEventListener('click', () => {
    state.swap = !state.swap;
    savePrefs();
    mountFeeds();
    refreshRecord();
    toast(state.swap ? 'Front camera large' : 'Back camera large');
  });
  $('#rec-mode').addEventListener('click', mountCameraSheet);
  $('#rec-photo').addEventListener('click', takePhoto);
  recDom.btn.addEventListener('click', triggerRecordClick);
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
/* Pause button for the large camera in Dual View; paused = black frame. */
function buildMainControls() {
  const wrap = document.createElement('div');
  wrap.className = 'tile-controls main-controls';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'pip-ctl' + (state.mainPaused ? ' on' : '');
  btn.setAttribute('aria-label', state.mainPaused ? 'Resume large camera' : 'Pause large camera');
  btn.innerHTML = state.mainPaused ? ICONS.play : ICONS.pause;
  btn.addEventListener('pointerdown', (e) => e.stopPropagation());
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    state.mainPaused = !state.mainPaused;
    mountFeeds();
    refreshRecord();
    toast(state.mainPaused ? 'Large camera paused' : 'Large camera running');
  });
  wrap.appendChild(btn);
  return wrap;
}

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
  recDom.stackTiles = null;
  recDom.splitHandle = null;
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

    if (state.pipStyle === 'SPLIT' && small && !state.pipOff) {
      const topTile = makeTile(bigKey.toUpperCase());
      topTile.classList.toggle('paused', !!state.mainPaused);
      topTile.appendChild(buildMainControls());
      if (big) {
        if (big.mirror) topTile.classList.add('mirror');
        topTile.appendChild(big.el);
      }
      const botTile = makeTile(smallKey.toUpperCase());
      botTile.classList.toggle('paused', !!state.pipPaused);
      if (small) {
        if (small.mirror) botTile.classList.add('mirror');
        botTile.appendChild(small.el);
      }
      stage.append(topTile, botTile);
      return;
    }

    const tile = makeTile(bigKey.toUpperCase());
    tile.classList.toggle('paused', !!state.mainPaused);
    tile.appendChild(buildMainControls());
    stage.appendChild(tile);
    if (big) {
      if (big.mirror) tile.classList.add('mirror');
      tile.appendChild(big.el);
    }

    if (small && !state.pipOff) {
      const isCircle = state.pipStyle === 'CIRCLE';
      const pip = document.createElement('div');
      pip.className = 'pip' + (small.mirror ? ' mirror' : '') + (state.pipPaused ? ' paused' : '') + (isCircle ? ' circle' : '');
      const a = isCircle ? ASPECTS.R_1_1 : ASPECTS[smallKey === 'back' ? take.backAspect : take.frontAspect];
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
      recDom.pipAspect = isCircle ? 'R_1_1' : (smallKey === 'back' ? take.backAspect : take.frontAspect);
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
  const visible = order.filter((key) => !rec.hide[key]);
  recDom.stackTiles = { back: null, front: null };
  visible.forEach((key) => {
    const aspect = ASPECTS[key === 'back' ? take.backAspect : take.frontAspect].short;
    const tile = makeTile(`${key.toUpperCase()} · ${aspect}`);
    tile.dataset.camera = key;
    tile.classList.toggle('paused', !!rec.tilePaused[key]);
    const feed = cam.feeds[key];
    if (feed) {
      if (feed.mirror) tile.classList.add('mirror');
      tile.appendChild(feed.el);
    } else {
      tile.appendChild(placeholder('Unavailable'));
    }
    if (rec.active && visible.length > 1) tile.appendChild(buildTileControls(key, true));
    stage.appendChild(tile);
    recDom.stackTiles[key] = tile;
  });

  if (visible.length > 1) {
    const split = document.createElement('div');
    split.className = 'stack-split';
    split.setAttribute('role', 'separator');
    split.setAttribute('aria-label', 'Drag to resize the two previews');
    split.innerHTML = '<i></i>';
    stage.appendChild(split);
    recDom.splitHandle = split;
    wireSplitHandle(split, stage);
  }
  applySplit();

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
    saveAspect: state.saveAspect,
  };
}

function layoutKey() {
  const t = activeTake();
  return [cam.ready, cam.singleFallback, cam.active, t.mode, t.frontAspect, t.backAspect, state.stack, state.swap, rec.active].join('|');
}

/* Delar upp de två rutorna: dra handtaget för att göra ena bilden större. */
function applySplit() {
  const tiles = recDom.stackTiles;
  if (!tiles) return;
  const order = state.stack === 'back-first' ? ['back', 'front'] : ['front', 'back'];
  const shown = order.filter((k) => tiles[k] && !tiles[k].hidden);
  if (shown.length === 2) {
    const frac = shown[0] === 'back' ? stackSplit : 1 - stackSplit;
    tiles[shown[0]].style.flexGrow = String(frac);
    tiles[shown[1]].style.flexGrow = String(1 - frac);
  } else if (shown.length === 1) {
    tiles[shown[0]].style.flexGrow = '1';
  }
  const handle = recDom.splitHandle;
  if (handle) {
    handle.hidden = shown.length !== 2;
    const frac = shown.length === 2 ? (shown[0] === 'back' ? stackSplit : 1 - stackSplit) : 0.5;
    handle.style.top = `${(frac * 100).toFixed(2)}%`;
  }
}

function wireSplitHandle(handle, stage) {
  let dragging = false;
  const setFrom = (clientY) => {
    const rect = stage.getBoundingClientRect();
    if (!rect.height) return;
    const order = state.stack === 'back-first' ? ['back', 'front'] : ['front', 'back'];
    const shown = order.filter((k) => recDom.stackTiles && recDom.stackTiles[k] && !recDom.stackTiles[k].hidden);
    if (shown.length !== 2) return;
    const frac = clamp((clientY - rect.top) / rect.height, 0.3, 0.7);
    stackSplit = shown[0] === 'back' ? frac : 1 - frac;
    applySplit();
  };
  handle.addEventListener('pointerdown', (e) => {
    e.stopPropagation();
    e.preventDefault();
    dragging = true;
    handle.classList.add('active');
    try { handle.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
  });
  handle.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    e.preventDefault();
    setFrom(e.clientY);
  });
  const end = () => { dragging = false; handle.classList.remove('active'); };
  handle.addEventListener('pointerup', end);
  handle.addEventListener('pointercancel', end);
}

/* Pausa eller ta bort en kamera i den delade vyn medan den andra spelar in. */
function buildTileControls(key, canHide) {
  const wrap = document.createElement('div');
  wrap.className = 'tile-controls';

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'pip-ctl';
  const paint = () => {
    const paused = !!rec.tilePaused[key];
    btn.innerHTML = paused ? ICONS.play : ICONS.pause;
    btn.setAttribute('aria-label', paused ? 'Resume this camera' : 'Pause this camera');
    btn.classList.toggle('on', paused);
    const tile = recDom.stackTiles && recDom.stackTiles[key];
    if (tile) tile.classList.toggle('paused', paused);
  };
  paint();
  btn.addEventListener('pointerdown', (e) => e.stopPropagation());
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const paused = !!rec.tilePaused[key];
    const out = (rec.outs || []).find((o) => o.key === key && !o.stopped);
    try {
      if (out) { if (paused) out.recorder.resume(); else out.recorder.pause(); }
    } catch (_) { /* unsupported */ }
    rec.tilePaused[key] = !paused;
    paint();
    recordMessage = `${key.toUpperCase()} ${paused ? 'resumed — same file' : 'paused — the other camera keeps recording'}`;
    refreshRecord();
    toast(rec.tilePaused[key] ? `${key.toUpperCase()} paused` : `${key.toUpperCase()} resumed`);
  });
  wrap.appendChild(btn);

  if (canHide) {
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'pip-ctl';
    close.innerHTML = ICONS.close;
    close.setAttribute('aria-label', 'Remove this camera');
    close.addEventListener('pointerdown', (e) => e.stopPropagation());
    close.addEventListener('click', (e) => {
      e.stopPropagation();
      const out = (rec.outs || []).find((o) => o.key === key && !o.stopped);
      if (out) { out.stopped = true; try { out.recorder.stop(); } catch (_) { /* already stopped */ } }
      rec.hide[key] = true;
      rec.tilePaused[key] = false;
      const tile = recDom.stackTiles && recDom.stackTiles[key];
      if (tile) tile.hidden = true;
      const otherKey = key === 'back' ? 'front' : 'back';
      const otherTile = recDom.stackTiles && recDom.stackTiles[otherKey];
      const otherClose = otherTile && otherTile.querySelector('[aria-label="Remove this camera"]');
      if (otherClose) otherClose.hidden = true;
      applySplit();
      recordMessage = `${key.toUpperCase()} removed — recording continues with the other camera`;
      refreshRecord();
      toast(`${key.toUpperCase()} removed — recording continues`);
    });
    wrap.appendChild(close);
  }

  return wrap;
}

function unmountFeeds() {
  Object.values(cam.feeds).forEach((f) => { if (f && f.el) sinkEl.appendChild(f.el); });
}

function recordStatusText() {
  if (recordMessage) return recordMessage;
  if (!cam.ready) return 'Starting cameras…';
  if (cam.demo) return 'Demo feed — no camera access (allow camera for this site to use real feeds)';
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
  const pipState = (state.mainPaused ? ' · large paused' : '')
    + (state.pipOff ? ' · small off' : state.pipPaused ? ' · small paused' : '');
  const saved = t.mode !== 'SEPARATE' && t.saveAspect && t.saveAspect !== 'AUTO' ? ` · file ${ASPECTS[t.saveAspect].short}` : '';
  return `Front ${ASPECTS[t.frontAspect].short} · Back ${ASPECTS[t.backAspect].short}${saved} · ${quality} · ${audio}${pipState}`;
}

function refreshRecord() {
  if (!state.record || !recDom.root) return;

  if (layoutKey() !== mountedKey) mountFeeds();

  if (recDom.label) recDom.label.textContent = MODES[activeTake().mode].title;
  if (recDom.preview) recDom.preview.classList.toggle('recording', rec.active);
  if (recDom.root) recDom.root.classList.toggle('recording', rec.active);
  const dual = !!(cam.feeds.back && cam.feeds.front);
  const isDualView = activeTake().mode === 'DUAL_VIEW';
  if (recDom.layoutBtn) {
    recDom.layoutBtn.hidden = !dual || !isDualView;
    recDom.layoutBtn.textContent = PIP_STYLE_LABEL[state.pipStyle];
    recDom.layoutBtn.classList.toggle('active', state.pipStyle !== 'PIP');
  }
  if (recDom.audioBtn) {
    recDom.audioBtn.innerHTML = state.audio ? ICONS.audio : ICONS.mute;
    recDom.audioBtn.classList.toggle('muted', !state.audio);
  }
  if (recDom.countdownBtn) {
    recDom.countdownBtn.textContent = state.countdownSec ? `${state.countdownSec}s` : '0s';
    recDom.countdownBtn.classList.toggle('on', state.countdownSec > 0);
  }
  if (recDom.thumb) {
    const latest = state.clips[0];
    recDom.thumb.hidden = rec.active || !latest;
    recDom.thumb.innerHTML = latest && latest.poster ? `<img src="${latest.poster}" alt="" />` : ICONS.gallery;
  }
  if (recDom.swap) {
    recDom.swap.hidden = !dual || !isDualView;
    recDom.swap.classList.toggle('swapped', state.swap);
  }
  if (recDom.flip) recDom.flip.hidden = dual;
  if (recDom.pipBtn) {
    recDom.pipBtn.hidden = !dual;
    recDom.pipBtn.classList.toggle('off', !isDualView || state.pipOff);
    recDom.pipBtn.setAttribute('aria-label', (!isDualView || state.pipOff) ? 'Show small camera' : 'Hide small camera');
  }
  const photoBtn = $('#rec-photo');
  if (photoBtn) photoBtn.disabled = !cam.ready;
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

    ${state.mode !== 'SEPARATE' ? `
    <div class="section-title">Saved video (Dual View)</div>
    <div class="chips">
      ${SAVE_ORDER.map((k) => `
        <button class="chip ${k === state.saveAspect ? 'active' : ''}" data-recsave="${k}" type="button">${saveLabel(k)}</button>
      `).join('')}
    </div>` : ''}

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
  $$('[data-recsave]', modalRoot).forEach((b) => b.addEventListener('click', () => {
    state.saveAspect = b.dataset.recsave;
    savePrefs();
    if (rec.active) toast(`Saved video ${saveLabel(state.saveAspect)} · starts with the next take`);
    mountCameraSheet();
    if (!rec.active) refreshRecord();
  }));

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
  renderNav();
  fitZoom();
  if (state.page === 'SETTINGS') renderSettings();
  else if (state.page === 'GALLERY') renderGallery();
  else if (state.page === 'STATS') renderStats();
  else renderHome();
}

/* ------------------------------------------------------------------
   Home / Gallery / Stats / Settings — drawn in the reference images'
   own pixels (1024 wide) and scaled to the screen with CSS zoom, so the
   proportions match design/reference/*.png exactly.
   ------------------------------------------------------------------ */

/* Scale factor from 1024-wide design pixels to the real screen. */
function fitZoom() {
  const w = screenEl.clientWidth || deviceEl.clientWidth;
  if (!w) return;
  const z = w / 1024;
  const root = document.documentElement.style;
  root.setProperty('--z', String(z));
  root.setProperty('--ph', `${Math.max(1, screenEl.clientHeight) / z}px`);
}

function renderNav() {
  const nav = $('#nav');
  if (!nav) return;
  nav.hidden = state.record || !state.onboarded;
  const tabs = [
    ['HOME', 'Home', ICONS.home],
    ['GALLERY', 'Gallery', ICONS.gallery],
    ['STATS', 'Stats', ICONS.stats],
    ['SETTINGS', 'Settings', ICONS.settings],
  ];
  nav.innerHTML = tabs.map(([key, label, icon]) => `
    <button class="tab ${state.page === key ? 'active' : ''}" data-tab="${key}" type="button">
      <span class="tab-icon">${icon}</span>
      <span class="tab-label">${label}</span>
    </button>`).join('');
  $$('[data-tab]', nav).forEach((b) => b.addEventListener('click', () => go(b.dataset.tab)));
}

/* Sample library (photos cut from the reference) until the user records. */
const DEMO_CLIPS = [
  { name: 'Kista Sunset', date: 'Today, 09:12', duration: '00:24', img: 'assets/kista-sunset.webp' },
  { name: 'Live Event', date: 'Yesterday, 21:47', duration: '01:03', img: 'assets/live-event.webp' },
  { name: 'Road Trip', date: '3 days ago', duration: '00:42', img: 'assets/road-trip.webp' },
  { name: 'At the Beach', date: '4 days ago', duration: '00:18', img: 'assets/at-the-beach.webp' },
  { name: 'Ski Trip', date: '1 week ago', duration: '01:21', img: 'assets/ski-trip.webp' },
  { name: 'Stockholm', date: '1 week ago', duration: '00:37', img: 'assets/stockholm.webp' },
];

const isDemo = () => state.clips.length === 0;

function isFavorite(id) {
  return Array.isArray(state.favorites) && state.favorites.includes(id);
}

function toggleFavorite(id) {
  if (!Array.isArray(state.favorites)) state.favorites = [];
  if (state.favorites.includes(id)) {
    state.favorites = state.favorites.filter((x) => x !== id);
  } else {
    state.favorites = [...state.favorites, id];
  }
  savePrefs();
}

function clipDateText(c) {
  return c.when instanceof Date && !Number.isNaN(c.when.getTime())
    ? c.when.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
    : '';
}

function clipMetaLine(c) {
  const when = clipDateText(c);
  return `${c.sizeMb} MB${when ? ` · ${when}` : ''}`;
}

/* "00:01:05" -> 65 */
function clipSeconds(c) {
  return String(c.duration || '').split(':').reduce((s, p) => s * 60 + (Number(p) || 0), 0);
}

const shortDur = (sec) => `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`;

function libraryItems() {
  if (!isDemo()) {
    return state.clips.map((c) => ({
      id: c.id, clip: c, kind: c.kind || 'video', name: c.name, date: clipDateText(c),
      duration: c.kind === 'photo' ? 'PHOTO' : shortDur(clipSeconds(c)), img: c.poster, baked: false,
    }));
  }
  return DEMO_CLIPS.map((d, i) => ({ id: `demo-${i}`, clip: null, kind: 'video', baked: true, ...d }));
}

function openItem(id) {
  const item = libraryItems().find((i) => i.id === id);
  if (!item) return;
  if (item.clip) playClip(item.clip);
  else toast('Sample clip – record a take to play your own here');
}

function bindCommon(root) {
  $$('[data-item]', root).forEach((el) => el.addEventListener('click', () => openItem(el.dataset.item)));
  $$('[data-go]', root).forEach((el) => el.addEventListener('click', () => go(el.dataset.go)));
  $$('[data-about]', root).forEach((el) => el.addEventListener('click', showAbout));
}

function thumbHTML(item) {
  const img = item.img ? `<img src="${item.img}" alt="" />` : '';
  return `${img}${item.baked ? '' : `<span class="v5-dur">${esc(item.duration)}</span>`}`;
}

const brand = (cls = '') => `<div class="v5-brand ${cls}">Dual<span>Shots</span></div>`;

/* ---------------------------------------------------------------- home */

const QUICK_ACTIONS = [
  { go: 'GALLERY', title: 'My Recordings', sub: 'View and manage<br>your videos', icon: ICONS.layers },
  { go: 'SETTINGS', title: 'Settings', sub: 'Customize your<br>experience', icon: ICONS.gear },
  { go: 'STATS', title: 'Stats', sub: 'View your<br>recording stats', icon: ICONS.stats },
  { tips: true, title: 'Tips', sub: 'Get the most<br>out of DualShots', icon: ICONS.bulb },
];

function showTips() {
  openSheet(`
    <div class="grabber"></div>
    <div class="sheet-title">Tips</div>
    <div class="option-list">${ONBOARD_STEPS.map((s) => `
      <div class="option"><span class="t">${s.title}</span><span class="d">${s.body}</span></div>`).join('')}
    </div>
    <button class="btn-primary" data-close type="button">Got it</button>`);
}

function renderHome() {
  const recent = libraryItems().slice(0, 3);
  screenEl.innerHTML = `
    <div class="v5 v5-home">
      <section class="h-hero">
        <img class="h-art" src="assets/hero.webp" alt="" />
        ${brand('h-brand')}
        <p class="h-kicker">RECORD BOTH SIDES.<br><span>AT ONCE.</span></p>
        <h1 class="h-title">Two<br>Perspectives.<br><span>One Story.</span></h1>
        <p class="h-sub">Capture more. Create better.</p>
      </section>

      <button class="h-start" id="start-btn" type="button">
        <span class="h-start-ico">${ICONS.video}</span>
        <span class="h-start-label">START RECORDING</span>
        <span class="h-start-chev">${ICONS.chevron}</span>
      </button>

      <div class="h-quick">
        ${QUICK_ACTIONS.map((q) => `
          <button class="h-qcard" type="button" ${q.go ? `data-go="${q.go}"` : ''} ${q.tips ? 'data-tips' : ''}>
            <span class="h-qico">${q.icon}</span>
            <b>${q.title}</b>
            <span class="h-qsub">${q.sub}</span>
            <span class="h-qchev">${ICONS.chevron}</span>
          </button>`).join('')}
      </div>

      <section class="h-panel">
        <h2>Recent Recordings</h2>
        ${recent.length ? `<button class="v5-seeall h-seeall" data-go="GALLERY" type="button">See All ${ICONS.chevron}</button>` : ''}
        ${recent.length ? `
          <div class="h-items">
            ${recent.map((i) => `
              <button class="h-item" data-item="${i.id}" type="button">
                <span class="h-thumb">${thumbHTML(i)}</span>
                <span class="h-ititle">${esc(i.name)}</span>
                <span class="h-idate">${esc(i.date)}</span>
                <span class="v5-dots">${ICONS.dots}</span>
              </button>`).join('')}
          </div>` : `
          <div class="h-empty">
            <b>No recordings yet</b>
            <span>Tap Start Recording above to capture your first dual-camera clip.</span>
          </div>`}
      </section>
    </div>`;

  $('#start-btn').addEventListener('click', openRecord);
  $$('[data-tips]', screenEl).forEach((b) => b.addEventListener('click', showTips));
  const copyMail = $('#st-copy-email', screenEl);
  if (copyMail) {
    copyMail.addEventListener('click', async () => {
      const ok = await copyText('bynrnworld@gmail.com');
      toast(ok ? 'Email copied: bynrnworld@gmail.com' : 'bynrnworld@gmail.com');
    });
  }

  bindCommon(screenEl);
}

/* ------------------------------------------------------------- gallery */

const FILTERS = [
  ['ALL', 'All', ICONS.grid4],
  ['VIDEOS', 'Videos', ICONS.video],
  ['PHOTOS', 'Photos', ICONS.photo],
  ['FAVORITES', 'Favorites', ICONS.heart],
];

const fmtSize = (mbVal) => (mbVal >= 1024 ? `${(mbVal / 1024).toFixed(1)} GB` : `${mbVal} MB`);

function renderGallery() {
  const filter = state.galleryFilter || 'ALL';
  const q = String(state.galleryQuery || '').trim().toLowerCase();
  const items = libraryItems().filter((i) => {
    const okFilter = (filter === 'ALL')
      || (filter === 'VIDEOS' && i.kind !== 'photo')
      || (filter === 'PHOTOS' && i.kind === 'photo')
      || (filter === 'FAVORITES' && isFavorite(i.id));
    return okFilter && (!q || i.name.toLowerCase().includes(q));
  });
  const showList = items.length > 0;
  const videos = state.clips.filter((c) => c.kind !== 'photo');
  const photos = state.clips.filter((c) => c.kind === 'photo');
  const videoMb = videos.reduce((sum, c) => sum + (c.sizeMb || 0), 0);
  const photoMb = photos.reduce((sum, c) => sum + (c.sizeMb || 0), 0);
  const totalMb = videoMb + photoMb;
  const storage = {
    label: `${fmtSize(totalMb)} recorded`,
    video: Math.min(100, (videoMb / 1024) * 100),
    photo: Math.min(100, (photoMb / 1024) * 100),
    legend: [`Videos (${fmtSize(videoMb)})`, `Photos (${fmtSize(photoMb)})`],
  };
  const filterLabel = (FILTERS.find(([k]) => k === filter) || FILTERS[0])[1];

  screenEl.innerHTML = `
    <div class="v5 v5-gallery">
      <header class="g-head">
        ${brand('g-brand')}
        <div class="v5-tagline g-tagline">YOUR MOMENTS. TWO PERSPECTIVES.</div>
        <button class="v5-circle g-search ${state.gallerySearchOpen ? 'active' : ''}" id="g-search-btn" type="button" aria-label="Search">${ICONS.search}</button>
        <button class="v5-circle g-more" data-about type="button" aria-label="More">${ICONS.dots}</button>
      </header>

      ${state.gallerySearchOpen ? `
        <div class="g-searchbar">
          ${ICONS.search}
          <input id="g-search-input" type="search" placeholder="Search clips and photos…" value="${esc(state.galleryQuery || '')}" />
          ${state.galleryQuery ? '<button id="g-search-clear" type="button">Clear</button>' : ''}
        </div>` : ''}

      <div class="g-chips">
        ${FILTERS.map(([k, label, icon]) => `
          <button class="g-chip g-chip-${k.toLowerCase()} ${filter === k ? 'active' : ''}" data-filter="${k}" type="button">${icon}<span>${label}</span></button>`).join('')}
      </div>

      ${showList ? `
        <div class="g-grid">
          ${items.map((i) => `
            <div class="g-card" data-item="${i.id}" role="button" tabindex="0">
              <span class="g-thumb">${thumbHTML(i)}</span>
              <span class="g-ctitle">${esc(i.name)}</span>
              <span class="g-cdate">${esc(i.date)}</span>
              <button class="g-fav ${isFavorite(i.id) ? 'on' : ''}" data-fav="${i.id}" type="button" aria-label="Favorite">${ICONS.heart}</button>
            </div>`).join('')}
        </div>

        <div class="g-storage">
          <h2>Storage</h2>
          <span class="g-sused">${storage.label}</span>
          <div class="g-bar"><i class="v" style="width:${storage.video}%"></i><i class="p" style="width:${storage.photo}%"></i></div>
          <div class="g-legend">
            <span><i class="dot o"></i>${storage.legend[0]}</span>
            <span><i class="dot g"></i>${storage.legend[1]}</span>
          </div>
        </div>` : `
        <div class="g-empty">
          <span class="g-empty-ico">${filter === 'PHOTOS' ? ICONS.photo : filter === 'FAVORITES' ? ICONS.heart : ICONS.video}</span>
          <b>${q ? `No matches for "${esc(state.galleryQuery)}"` : `No ${filterLabel.toLowerCase()} yet`}</b>
          <span>${state.clips.length === 0 ? 'Record your first dual-camera video or photo — it will appear right here.' : 'Try another filter or record a new clip.'}</span>
          ${state.clips.length === 0 ? '<button class="g-empty-cta" id="g-start-btn" type="button">Start Recording</button>' : ''}
        </div>`}
    </div>`;

  $('#g-search-btn').addEventListener('click', () => {
    state.gallerySearchOpen = !state.gallerySearchOpen;
    if (!state.gallerySearchOpen) state.galleryQuery = '';
    renderGallery();
    if (state.gallerySearchOpen) {
      const inp = $('#g-search-input');
      if (inp) inp.focus();
    }
  });
  const searchInp = $('#g-search-input');
  if (searchInp) {
    searchInp.addEventListener('input', (e) => {
      state.galleryQuery = e.target.value;
      renderGallery();
      const el = $('#g-search-input');
      if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length); }
    });
  }
  const clearBtn = $('#g-search-clear');
  if (clearBtn) clearBtn.addEventListener('click', () => { state.galleryQuery = ''; renderGallery(); });
  const startBtn = $('#g-start-btn');
  if (startBtn) startBtn.addEventListener('click', openRecord);

  $$('[data-filter]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.galleryFilter = b.dataset.filter;
    renderGallery();
  }));
  $$('[data-fav]', screenEl).forEach((b) => b.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleFavorite(b.dataset.fav);
    renderGallery();
  }));
  const copyMail = $('#st-copy-email', screenEl);
  if (copyMail) {
    copyMail.addEventListener('click', async () => {
      const ok = await copyText('bynrnworld@gmail.com');
      toast(ok ? 'Email copied: bynrnworld@gmail.com' : 'bynrnworld@gmail.com');
    });
  }

  bindCommon(screenEl);
}

/* --------------------------------------------------------------- stats */

function realStats() {
  const clips = state.clips.filter((c) => c.kind !== 'photo');
  const photos = state.clips.filter((c) => c.kind === 'photo');
  const secs = clips.map(clipSeconds);
  const totalSec = secs.reduce((a, b) => a + b, 0);
  const totalMb = clips.reduce((s, c) => s + (c.sizeMb || 0), 0);
  const photoMb = photos.reduce((s, c) => s + (c.sizeMb || 0), 0);
  const avg = clips.length ? Math.round(totalSec / clips.length) : 0;
  const favCount = state.clips.filter((c) => isFavorite(c.id)).length;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = [];
  const series = { clips: [], minutes: [], storage: [] };
  for (let i = 6; i >= 0; i -= 1) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const same = clips.filter((c) => c.when instanceof Date && c.when.toDateString() === d.toDateString());
    days.push([d.toLocaleDateString('en-GB', { weekday: 'short' }), d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })]);
    series.clips.push(same.length);
    series.minutes.push(Math.round(same.reduce((s, c) => s + clipSeconds(c), 0) / 60));
    series.storage.push(Math.round(same.reduce((s, c) => s + (c.sizeMb || 0), 0) / 102.4) / 10);
  }
  const by = (k) => clips.filter((c) => c.name.includes(`_${k}_`)).length;
  const n = clips.length || 1;
  const both = by('combined');
  const front = by('front');
  const backCount = Math.max(0, clips.length - both - front);
  return {
    total: clips.length, totalDelta: '',
    cards: [
      ['camera', 'RECORDING TIME', `${Math.round(totalSec / 60)} min`, ''],
      ['database', 'STORAGE USED', fmtSize(totalMb + photoMb), ''],
      ['film', 'AVERAGE LENGTH', `${Math.floor(avg / 60)}:${String(avg % 60).padStart(2, '0')}`, ''],
      ['star', 'FAVORITES', `${favCount} clips`, ''],
    ],
    days,
    series,
    usage: clips.length
      ? [Math.round((both / n) * 100), Math.round((front / n) * 100), Math.round((backCount / n) * 100)]
      : [0, 0, 0],
    storage: { label: `${fmtSize(totalMb + photoMb)} recorded`, pct: Math.min(100, ((totalMb + photoMb) / 1024) * 100), rows: [fmtSize(totalMb), fmtSize(photoMb), '0 MB'] },
  };
}

function chartScale(values) {
  const max = Math.max(...values, 0);
  if (max <= 10) return 10;
  const step = Math.ceil(max / 5);
  return step * 5;
}

function renderStats() {
  const s = realStats();
  const metric = state.statsMetric || 'clips';
  const values = s.series[metric];
  const top = chartScale(values);
  const ticks = [5, 4, 3, 2, 1, 0].map((k) => +(top * k / 5).toFixed(1));
  const delta = (d) => (d ? `<span class="s-delta">${ICONS.arrowUpRight}${d}</span>` : '<span class="s-delta none">–</span>');
  const [both, front, back] = s.usage;

  screenEl.innerHTML = `
    <div class="v5 v5-stats">
      <header class="s-head">
        ${brand('s-brand')}
        <h1 class="s-title">Stats</h1>
        <p class="s-sub">Your DualShots recording history</p>
        <span class="s-range">${ICONS.calendar}<span>Last 7 days</span></span>
      </header>

      <section class="s-total">
        <span class="s-total-tile">${ICONS.trend}</span>
        <span class="s-label s-total-label">TOTAL RECORDINGS</span>
        <span class="s-total-value">${s.total} clips</span>
        <span class="s-total-delta">${s.totalDelta ? `${ICONS.arrowUpRight}<b>${s.totalDelta}</b> vs. last week` : 'Last 7 days'}</span>
        <span class="s-total-tag">RECORD BOTH SIDES.<br><em>AT ONCE.</em></span>
        <svg class="s-wave" viewBox="0 0 500 100" aria-hidden="true">
          <path d="M0 92 C40 90 70 72 110 78 C150 84 160 20 210 22 C250 24 255 80 300 80 C345 80 350 52 385 52 C420 52 430 84 460 60 C480 44 490 22 500 22" />
        </svg>
      </section>

      <div class="s-cards">
        ${s.cards.map(([icon, label, value, d]) => `
          <div class="s-card">
            <span class="s-cico">${ICONS[icon]}</span>
            <span class="s-label">${label}</span>
            <b>${value}</b>
            ${delta(d)}
          </div>`).join('')}
      </div>

      <section class="s-chart">
        <span class="s-label s-chart-label">RECORDINGS BY DAY</span>
        <div class="s-seg">
          ${[['clips', 'Clips'], ['minutes', 'Minutes'], ['storage', 'Storage']].map(([k, label]) => `
            <button class="${metric === k ? 'active' : ''}" data-metric="${k}" type="button">${label}</button>`).join('')}
        </div>
        <div class="s-plot">
          ${ticks.map((t, i) => `<span class="s-tick" style="top:${i * 30.4}px">${t}</span><i class="s-grid" style="top:${i * 30.4}px"></i>`).join('')}
          ${values.map((v, i) => {
    const h = top ? (v / top) * 152 : 0;
    return `<span class="s-bar" style="left:${109 + i * 123.8}px;height:${h}px"><em>${v}</em></span>
              <span class="s-day" style="left:${109 + i * 123.8}px"><b>${s.days[i][0]}</b>${s.days[i][1]}</span>`;
  }).join('')}
        </div>
      </section>

      <div class="s-bottom">
        <section class="s-usage">
          <h2>Camera Usage</h2>
          <div class="s-donut" style="background:conic-gradient(from 0deg, #ff5a0a 0 ${both * 0.6}%, #ff8a1c ${both * 0.6}% ${both}%, #ffa53a ${both}% ${both + front}%, #2a3245 ${both + front}% 100%)">
            <span><b>${s.total}</b>clips</span>
          </div>
          <ul class="s-legend">
            <li><i class="dot o"></i>Both cameras<em>${both}%</em></li>
            <li><i class="dot l"></i>Front camera<em>${front}%</em></li>
            <li><i class="dot d"></i>Back camera<em>${back}%</em></li>
          </ul>
        </section>
        <section class="s-store">
          <h2>Storage Overview</h2>
          <span class="s-store-used">${s.storage.label}</span>
          <div class="s-store-bar"><i style="width:${s.storage.pct}%"></i></div>
          <ul class="s-store-rows">
            <li><i class="dot o"></i>Videos<em>${s.storage.rows[0]}</em></li>
            <li><i class="dot b"></i>Photos<em>${s.storage.rows[1]}</em></li>
            <li><i class="dot d"></i>Other<em>${s.storage.rows[2]}</em></li>
          </ul>
        </section>
      </div>
    </div>`;

  $$('[data-metric]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.statsMetric = b.dataset.metric;
    renderStats();
  }));
  const copyMail = $('#st-copy-email', screenEl);
  if (copyMail) {
    copyMail.addEventListener('click', async () => {
      const ok = await copyText('bynrnworld@gmail.com');
      toast(ok ? 'Email copied: bynrnworld@gmail.com' : 'bynrnworld@gmail.com');
    });
  }

  bindCommon(screenEl);
}

/* ------------------------------------------------------------ settings */

const MODE_ICONS = {
  DUAL_VIEW: ICONS.video,
  SEPARATE: svg('<rect x="2.5" y="4.5" width="8.5" height="15" rx="1.8"/><rect x="13" y="4.5" width="8.5" height="15" rx="1.8"/>'),
  BOTH: svg('<rect x="2.5" y="3" width="13" height="13" rx="2.2"/><rect x="8.5" y="8.5" width="13" height="13" rx="2.2"/>'),
};

const MODE_SUBS = {
  DUAL_VIEW: 'Both cameras<br>in one video',
  SEPARATE: 'Two files, one<br>for each',
  BOTH: 'Combined +<br>two separate files',
};

const SETTING_TOGGLES = [
  ['grid', 'Grid', 'Show 3×3 framing grid', ICONS.grid4],
  ['audio', 'Record Audio', 'Capture microphone audio', ICONS.audio],
];

const CAMERA_ASPECTS = ['R_9_16', 'R_16_9', 'R_1_1'];
const ASPECT_HINTS = { R_9_16: 'Reels', R_16_9: 'YouTube', R_1_1: 'Feed' };

function cameraCardHTML(title, icon, key, attr) {
  return `
    <div class="st-cam">
      <span class="st-cam-ico">${icon}</span>
      <b>${title}</b>
      <div class="st-chips">
        ${CAMERA_ASPECTS.map((k) => {
    const short = ASPECTS[k].short;
    const word = ASPECT_HINTS[k] || '';
    return `<button class="st-chip ${state[key] === k ? 'active' : ''}" data-${attr}="${k}" type="button"><b>${short}</b><span>${word}</span></button>`;
  }).join('')}
      </div>
    </div>`;
}

function renderSettings() {
  screenEl.innerHTML = `
    <div class="v5 v5-settings">
      <header class="st-head">
        ${brand('st-brand')}
        <div class="v5-tagline st-tagline">CUSTOMIZE YOUR EXPERIENCE</div>
      </header>

      <div class="st-sec"><span class="v5-label">RECORDING MODE</span><span class="st-hint">RECORD BOTH SIDES. <em>AT ONCE.</em></span></div>
      <div class="st-modes">
        ${MODE_ORDER.map((m) => `
          <button class="st-mode ${state.mode === m ? 'sel' : ''}" data-mode="${m}" type="button">
            <span class="st-mico">${MODE_ICONS[m]}</span>
            <b>${MODES[m].title}</b>
            <span class="st-msub">${MODE_SUBS[m]}</span>
            <span class="st-radio"></span>
          </button>`).join('')}
      </div>

      <div class="st-sec"><span class="v5-label">CAMERA FORMATS</span></div>
      <div class="st-cams">
        ${cameraCardHTML('Front Camera', ICONS.user, 'frontAspect', 'front')}
        ${cameraCardHTML('Back Camera', ICONS.camera, 'backAspect', 'back')}
      </div>

      <div class="st-sec"><span class="v5-label">SAVED VIDEO</span><span class="st-hint">DUAL VIEW FILE. <em>16:9 FOR YOUTUBE.</em></span></div>
      <div class="st-row st-save">
        <span class="st-rico">${ICONS.film}</span>
        <b>Video format</b>
        <span class="st-rsub">Shape of the combined file</span>
        <div class="st-seg">
          ${SAVE_ORDER.map((k) => `<button class="${state.saveAspect === k ? 'active' : ''}" data-save="${k}" type="button">${saveLabel(k)}</button>`).join('')}
        </div>
      </div>

      <div class="st-sec"><span class="v5-label">VIDEO QUALITY</span><span class="st-hint">HIGHER QUALITY. BETTER STORIES.</span></div>
      <div class="st-row st-res">
        <span class="st-rico st-hd">HD</span>
        <b>Resolution</b>
        <span class="st-rsub">Choose video resolution</span>
        <div class="st-seg">
          ${QUALITY_ORDER.map((k) => `<button class="${state.quality === k ? 'active' : ''}" data-quality="${k}" type="button">${QUALITIES[k].label}</button>`).join('')}
        </div>
        <span class="st-rchev">${ICONS.chevron}</span>
      </div>

      <div class="st-sec"><span class="v5-label">CONTAINER / CODEC</span></div>
      <div class="st-row st-fmt">
        <span class="st-rico">${ICONS.file}</span>
        <b>Format</b>
        <span class="st-rsub">Choose file format</span>
        <div class="st-seg">
          ${CODEC_ORDER.map((k) => `<button class="${state.codec === k ? 'active' : ''}" data-codec="${k}" type="button">${CODECS[k].label}</button>`).join('')}
        </div>
        <span class="st-rchev">${ICONS.chevron}</span>
      </div>

      <div class="st-sec"><span class="v5-label">COUNTDOWN TIMER</span></div>
      <div class="st-row st-timer">
        <span class="st-rico">${ICONS.calendar}</span>
        <b>Timer</b>
        <span class="st-rsub">Delay before recording starts</span>
        <div class="st-seg">
          ${[0, 3, 5].map((sec) => `<button class="${state.countdownSec === sec ? 'active' : ''}" data-timer="${sec}" type="button">${sec ? `${sec}s` : 'Off'}</button>`).join('')}
        </div>
      </div>

      <div class="st-sec"><span class="v5-label">ADDITIONAL SETTINGS</span></div>
      <div class="st-toggles">
        ${SETTING_TOGGLES.map(([key, title, sub, icon]) => `
          <div class="st-tg">
            <span class="st-tico">${icon}</span>
            <b>${title}</b>
            <span class="st-rsub">${sub}</span>
            <button class="st-switch ${state[key] ? 'on' : ''}" data-toggle="${key}" type="button" role="switch" aria-checked="${!!state[key]}" aria-label="${title}"></button>
          </div>`).join('')}
      </div>

      <div class="st-footer">
        <div class="st-footer-ver">
          <span>DualShots · v1.2.8 · </span>
          <a class="st-footer-link" href="https://nrnworld.one" target="_blank" rel="noopener noreferrer">Made by nRnWorld</a>
        </div>
        <button class="st-footer-mail" id="st-copy-email" type="button" aria-label="Copy email address">
          <span>bynrnworld@gmail.com</span>
          ${ICONS.copy}
        </button>
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

  $$('[data-save]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.saveAspect = b.dataset.save;
    savePrefs();
    renderSettings();
  }));

  $$('[data-quality]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.quality = b.dataset.quality;
    savePrefs();
    renderSettings();
  }));

  $$('[data-codec]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.codec = b.dataset.codec;
    savePrefs();
    renderSettings();
  }));

  $$('[data-timer]', screenEl).forEach((b) => b.addEventListener('click', () => {
    state.countdownSec = Number(b.dataset.timer) || 0;
    savePrefs();
    renderSettings();
  }));

  $$('[data-toggle]', screenEl).forEach((b) => b.addEventListener('click', () => {
    const key = b.dataset.toggle;
    state[key] = !state[key];
    savePrefs();
    renderSettings();
  }));

  const copyMail = $('#st-copy-email', screenEl);
  if (copyMail) {
    copyMail.addEventListener('click', async () => {
      const ok = await copyText('bynrnworld@gmail.com');
      toast(ok ? 'Email copied: bynrnworld@gmail.com' : 'bynrnworld@gmail.com');
    });
  }

  bindCommon(screenEl);
}

/* GalleryScreen.kt: spelare i dialog med Favorite / Share / Delete / Close. */
function playClip(clip) {
  const fav = isFavorite(clip.id);
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog clip-dialog">
        <div class="clip-dialog-head">
          <div class="clip-dialog-name">${esc(clip.name)}</div>
          <button class="clip-fav-btn ${fav ? 'on' : ''}" data-clip-fav type="button" aria-label="Favorite">${ICONS.heart}</button>
        </div>
        ${clip.kind === 'photo'
      ? `<img class="clip-video" src="${clip.url}" alt="${esc(clip.name)}" />`
      : `<video class="clip-video" src="${clip.url}" controls autoplay playsinline></video>`}
        <div class="clip-dialog-meta">${esc(clipMetaLine(clip))}${clip.duration ? ` · ${esc(clip.duration)}` : ''}</div>
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
  $('[data-clip-fav]', modalRoot).addEventListener('click', () => {
    toggleFavorite(clip.id);
    playClip(clip);
    if (!state.record) render();
  });
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
    render();
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
        <div class="onb-tile"><img class="logo-mark" src="assets/icon.png" alt="DualShots" /></div>
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

const APK_URL = 'https://github.com/nRn-World/DualShots/releases/latest/download/DualShots-v1.2.8.apk';
const APK_PROMPT_KEY = 'dualshots_apk_prompt_dismissed';

function isMobileVisitor() {
  const ua = navigator.userAgent || '';
  if (/Android|iPhone|iPad|iPod|Mobile/i.test(ua)) return true;
  return !!(window.matchMedia && window.matchMedia('(pointer: coarse) and (max-width: 820px)').matches);
}

function maybeShowApkPrompt() {
  const params = new URLSearchParams(location.search);
  if (params.has('ref')) return;
  const force = params.has('install');
  if (!force) {
    if (!isMobileVisitor()) return;
    try {
      if (localStorage.getItem(APK_PROMPT_KEY) === '1') return;
    } catch (_) { /* private mode */ }
  }

  const isAndroid = /Android/i.test(navigator.userAgent || '') || force;
  modalRoot.innerHTML = `
    <div class="modal">
      <div class="dialog apk-dialog">
        <div class="apk-tile"><img class="logo-mark" src="assets/icon.png" alt="DualShots" /></div>
        <div class="dlg-title">Install DualShots</div>
        <div class="dlg-sub">ANDROID APP · V1.2.8 · FREE</div>
        <p class="apk-body">
          ${isAndroid
            ? 'Vill du installera DualShots som APK på mobilen? Med Android-appen får du riktig dubbelkamera (fram + bak samtidigt), 4K och direkt sparning i Galleriet.'
            : 'DualShots finns som gratis Android-app (APK) med riktig dubbelkamera (fram + bak samtidigt). Ladda ner APK-filen eller fortsätt i webbläsaren.'}
        </p>
        <a class="dlg-btn apk-dl" href="${APK_URL}" download="DualShots-v1.2.8.apk" data-apk-install>
          ${ICONS.download}<span>Installera APK (Android)</span>
        </a>
        <button class="dlg-close" data-apk-dismiss type="button">Fortsätt i webbläsaren</button>
      </div>
    </div>`;

  const dismiss = () => {
    try { localStorage.setItem(APK_PROMPT_KEY, '1'); } catch (_) { /* ignore */ }
    closeSheet();
  };
  const backdrop = $('.modal', modalRoot);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) dismiss(); });
  $('[data-apk-dismiss]', modalRoot).addEventListener('click', dismiss);
  $('[data-apk-install]', modalRoot).addEventListener('click', () => {
    try { localStorage.setItem(APK_PROMPT_KEY, '1'); } catch (_) { /* ignore */ }
    closeSheet();
    toast('Laddar ner DualShots-v1.2.8.apk — öppna filen för att installera');
  });
}

function tickClock() {
  const now = new Date();
  const h = now.getHours() % 12 || 12;
  $('#clock').textContent = `${h}:${String(now.getMinutes()).padStart(2, '0')}`;
}

function init() {
  loadPrefs();
  tickClock();
  setInterval(tickClock, 15000);
  // ?ref[&page=gallery]: frameless 1:1 view used to compare against design/reference/*.png
  const params = new URLSearchParams(location.search);
  if (params.has('ref')) {
    document.body.classList.add('ref');
    state.onboarded = true;
    state.page = (params.get('page') || 'HOME').toUpperCase();
  }
  if (window.ResizeObserver) new ResizeObserver(fitZoom).observe(screenEl);
  render();
  maybeShowApkPrompt();
}

init();
