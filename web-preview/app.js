/* ══════════════════════════════════════════════════════════════════════════
   DuelShots · interactive browser preview
   ──────────────────────────────────────────────────────────────────────────
   This is a UI/behaviour simulator, not the shipping app. Every model,
   status string and branch below is taken from the Kotlin sources so the
   preview stays honest about what the real APK does:

     model/Models.kt                  → RecordingMode / AspectOption /
                                        QualityOption / CodecOption
     data/AppPrefs.kt                 → persisted settings (localStorage here)
     ui/AppViewModel.kt               → premium gating of quality + codec
     camera/DualCameraController.kt   → init/bind/start/stop, fallback, file names
     ui/*Screen.kt                    → layout, spacing and copy
   ══════════════════════════════════════════════════════════════════════════ */
'use strict';

/* ───────────────────────────── models (Models.kt) ───────────────────────────── */

const MODE_ORDER = ['DUAL_VIEW', 'SEPARATE', 'BOTH'];

const MODES = {
  DUAL_VIEW: { title: 'Dual View', subtitle: 'Both cameras in one video', icon: '▢' },
  SEPARATE:  { title: 'Separate Videos', subtitle: 'Two files, one for each', icon: '▯' },
  BOTH:      { title: 'Both Options', subtitle: 'Combined + two separate files', icon: '⇄' },
};

const ASPECT_ORDER = ['R_9_16', 'R_16_9', 'R_1_1', 'R_4_3'];

const ASPECTS = {
  R_9_16: { label: '9:16 Portrait',    short: '9:16', cxRatio: 'RATIO_16_9', portrait: true },
  R_16_9: { label: '16:9 Landscape',   short: '16:9', cxRatio: 'RATIO_16_9', portrait: false },
  R_1_1:  { label: '1:1 Square',       short: '1:1',  cxRatio: 'RATIO_4_3',  portrait: true },
  R_4_3:  { label: '4:3',              short: '4:3',  cxRatio: 'RATIO_4_3',  portrait: false },
};

const QUALITY_ORDER = ['HD720', 'FHD1080', 'UHD4K'];

const QUALITY = {
  HD720:   { label: '720p',        segment: '720p',  premium: false, camerax: 'Quality.HD',  mbps: 5 },
  FHD1080: { label: '1080p Full HD', segment: '1080p', premium: false, camerax: 'Quality.FHD', mbps: 12 },
  UHD4K:   { label: '4K Ultra HD', segment: '4K★',   premium: true,  camerax: 'Quality.UHD', mbps: 45 },
};

const CODEC_ORDER = ['MP4', 'HEVC'];

const CODECS = {
  MP4:  { label: 'MP4',  segment: 'MP4',   mime: 'video/mp4',  premium: false },
  HEVC: { label: 'HEVC', segment: 'HEVC★', mime: 'video/hevc', premium: true },
};

const DEFAULTS = {
  mode: 'SEPARATE',        // AppPrefs default
  frontAspect: 'R_9_16',
  backAspect: 'R_16_9',
  quality: 'FHD1080',
  codec: 'MP4',
  isPremium: false,
  dualSupported: true,     // device capability (what the HAL reports)
  permissionGranted: false,
  clips: [],
};

/* ───────────────────────────── persistence (AppPrefs.kt) ───────────────────────────── */

const STORAGE_KEY = 'duelshots.preview.v1';

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS, clips: [] };
    const parsed = JSON.parse(raw);
    return {
      ...DEFAULTS,
      ...parsed,
      clips: Array.isArray(parsed.clips) ? parsed.clips : [],
    };
  } catch {
    return { ...DEFAULTS, clips: [] };
  }
}

const state = loadState();

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      mode: state.mode,
      frontAspect: state.frontAspect,
      backAspect: state.backAspect,
      quality: state.quality,
      codec: state.codec,
      isPremium: state.isPremium,
      dualSupported: state.dualSupported,
      permissionGranted: state.permissionGranted,
      clips: state.clips.slice(0, 30),
    }));
  } catch { /* private mode — preview still works, state just won't stick */ }
}

/* ───────────────────────────── UI state ───────────────────────────── */

const ui = {
  tab: 'HOME',          // HOME | GALLERY | SETTINGS
  inRecord: false,
  permission: state.permissionGranted,
  recording: false,
  elapsed: 0,
  status: '',
  ready: false,
  savedNames: [],
  renderedMode: state.mode,
  controller: null,
  session: 0,
  galleryLoading: false,
  premiumDialog: null,  // { feature: string }
  toast: null,
};

const app = document.getElementById('app');
const captionEl = document.getElementById('caption');
const logEl = document.getElementById('log');
const filesEl = document.getElementById('files');

/* ───────────────────────────── logging ───────────────────────────── */

const logLines = [];

function log(message, level = 'info') {
  logLines.push({ time: new Date().toLocaleTimeString('en-GB', { hour12: false }), message, level });
  if (logLines.length > 300) logLines.shift();
  paintLog();
}

function paintLog() {
  if (!logEl) return;
  if (!logLines.length) {
    logEl.innerHTML = '<p class="log-empty">Camera events will appear here.</p>';
    return;
  }
  const atBottom = logEl.scrollTop + logEl.clientHeight >= logEl.scrollHeight - 24;
  logEl.innerHTML = logLines
    .map((l) => `<div class="log-line ${l.level}"><span class="t">${l.time}</span><span class="m">${escapeHtml(l.message)}</span></div>`)
    .join('');
  if (atBottom) logEl.scrollTop = logEl.scrollHeight;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/* ───────────────────────────── helpers ───────────────────────────── */

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const pad2 = (n) => String(n).padStart(2, '0');

function fmtClock(total) {
  return `${pad2(Math.floor(total / 3600))}:${pad2(Math.floor((total % 3600) / 60))}:${pad2(total % 60)}`;
}

function stamp(date = new Date()) {
  return `${date.getFullYear()}${pad2(date.getMonth() + 1)}${pad2(date.getDate())}_`
    + `${pad2(date.getHours())}${pad2(date.getMinutes())}${pad2(date.getSeconds())}`;
}

function fmtSize(bytes) {
  return `${Math.max(1, Math.round(bytes / 1_000_000))} MB`;
}

function modeInfo(key = state.mode) { return MODES[key]; }

/* ───────────────────────────── camera controller ─────────────────────────────
   Mirrors camera/DualCameraController.kt. All delays are stand-ins for the
   real binder/hardware round-trips.
   ──────────────────────────────────────────────────────────────────────────── */

function createController() {
  const c = {
    provider: null,
    supported: false,
    canBind: false,
    singleFallback: false,
    activeMode: 'SEPARATE',

    /** @returns {Promise<string>} the status line, verbatim from the Kotlin. */
    async init() {
      log('ProcessCameraProvider.getInstance(context).get()', 'dim');
      await wait(280);
      c.provider = { ready: true };

      if (state.dualSupported) {
        c.supported = true;
        c.canBind = true;
        log('availableConcurrentCameraInfos → [BACK + FRONT]', 'ok');
        log('selectors: DEFAULT_BACK_CAMERA, DEFAULT_FRONT_CAMERA', 'dim');
        return 'Ready – dual cameras.';
      }

      c.supported = false;
      c.canBind = true;
      log('availableConcurrentCameraInfos → none (HAL denies concurrent streams)', 'warn');
      log('pick(DEFAULT_BACK_CAMERA) → ok · single-camera fallback', 'warn');
      return 'This device does not support simultaneous dual cameras – single camera enabled.';
    },

    /** Mirrors bind(wanted, …) incl. the “advanced mode not supported” fallback. */
    async bind(wanted) {
      log(`bind(wanted = ${wanted}, front = ${state.frontAspect}, back = ${state.backAspect}, quality = ${state.quality})`, 'info');
      await wait(240);

      if (!c.supported) {
        c.activeMode = 'SEPARATE';
        c.singleFallback = true;
        log('bindSingle(): Preview + VideoCapture<Recorder> on one lens', 'dim');
        log('bindToLifecycle(owner, selector, preview, videoCapture) → bound', 'ok');
        return 'Single camera (BACK) – recording one file.';
      }

      c.singleFallback = false;

      if (wanted === 'DUAL_VIEW') {
        c.activeMode = 'DUAL_VIEW';
        log('CompositionSettings: back α=1.00 offset=(0,0) scale=1.00', 'dim');
        log('CompositionSettings: front α=1.00 offset=(0.33,−0.33) scale=0.34', 'dim');
        log('bindToLifecycle([backCfg, frontCfg]) → one composed stream', 'ok');
        return 'Camera ready – Dual View.';
      }

      if (wanted === 'BOTH') {
        c.activeMode = 'BOTH';
        log('UseCaseGroup ×2 — shared composed capture + per-camera VideoCapture', 'dim');
        log('bindToLifecycle([backCfg, frontCfg]) → 3 outputs (combined, back, front)', 'ok');
        return 'Camera ready – Both Options.';
      }

      c.activeMode = 'SEPARATE';
      log('UseCaseGroup ×2 — Preview + VideoCapture per lens', 'dim');
      log('bindToLifecycle([backCfg, frontCfg]) → two parallel recorders', 'ok');
      return 'Camera ready – Separate Videos.';
    },

    /** Mirrors startRecording(): file names, audio flags and output targets. */
    startRecording() {
      const s = stamp();
      const files = [];
      const push = (name, audio, tag) => files.push({ name, audio, tag });

      log(`startRecording() — activeMode = ${c.activeMode}${c.singleFallback ? ' (single-camera fallback)' : ''}`, 'info');

      if (c.activeMode === 'SEPARATE') {
        if (c.singleFallback) {
          push(`duelshot_back_${s}`, true, 'back');
        } else {
          push(`duelshot_back_${s}`, true, 'back');
          push(`duelshot_front_${s}`, false, 'front');
        }
      } else if (c.activeMode === 'DUAL_VIEW') {
        push(`duelshot_combined_${s}`, true, 'combined');
      } else {
        push(`duelshot_combined_${s}`, true, 'combined');
        push(`duelshot_back_${s}`, false, 'back');
        push(`duelshot_front_${s}`, false, 'front');
      }

      files.forEach((f) => {
        log(`prepareRecording("${f.name}.mp4") → MediaStore Movies/DuelShots`, 'dim');
        if (f.audio) log(`  withAudioEnabled() · quality = ${QUALITY[state.quality].camerax}`, 'dim');
        log(`  record(…, audio=${f.audio}) → started`, 'ok');
      });

      return files;
    },

    stopRecording() {
      log('Recording.stop() on all active recorders', 'dim');
    },
  };

  return c;
}

/* ───────────────────────────── screen flow ───────────────────────────── */

let toastTimer = null;
let timerInterval = null;

async function beginCameraSession() {
  ui.session += 1;
  const session = ui.session;

  stopTimer();
  ui.recording = false;
  ui.ready = false;
  ui.elapsed = 0;
  ui.savedNames = [];
  ui.status = 'Starting cameras…';
  render();

  const controller = createController();
  ui.controller = controller;

  const initStatus = await controller.init();
  if (session !== ui.session) return;
  ui.status = initStatus;
  if (!controller.canBind) {
    render();
    return;
  }
  render();

  const bindStatus = await controller.bind(state.mode);
  if (session !== ui.session) return;
  ui.status = bindStatus;
  ui.renderedMode = controller.activeMode;
  ui.ready = true;
  render();
}

function endCameraSession(stopFirst = true) {
  ui.session += 1;
  if (stopFirst && ui.recording) stopRecording(true);
  stopTimer();
  ui.recording = false;
  ui.ready = false;
  ui.controller = null;
  ui.savedNames = [];
  ui.elapsed = 0;
}

function goRecord() {
  ui.inRecord = true;
  ui.tab = 'HOME';
  beginCameraSession();
}

function exitRecord() {
  if (ui.recording) stopRecording(true);
  endCameraSession(false);
  ui.inRecord = false;
  ui.tab = 'HOME';
  render();
}

function startTimer() {
  stopTimer();
  timerInterval = setInterval(() => {
    if (!ui.recording) return;
    ui.elapsed += 1;
    const el = app.querySelector('[data-timer]');
    if (el) el.textContent = fmtClock(ui.elapsed);
  }, 1000);
}

function stopTimer() {
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = null;
}

function toggleRecording() {
  if (!ui.recording) {
    const controller = ui.controller;
    if (!controller || !ui.ready) return;
    const files = controller.startRecording();
    ui.pendingFiles = files;
    ui.savedNames = files.map((f) => `${f.name}.mp4`);
    ui.recording = true;
    ui.startedAt = Date.now();
    ui.elapsed = 0;
    startTimer();
    render();
    showToast(`Recording ${files.length === 1 ? '1 file' : `3 files`} — ${modeInfo(controller.activeMode).title}`);
  } else {
    stopRecording(false);
  }
}

function stopRecording(silent) {
  const controller = ui.controller;
  const seconds = Math.max(1, ui.elapsed);
  const files = ui.pendingFiles || [];
  if (controller) controller.stopRecording();

  stopTimer();
  ui.recording = false;
  ui.pendingFiles = [];

  if (files.length) {
    const mbps = QUALITY[state.quality].mbps;
    const newClips = files.map((f) => {
      const audioBytes = f.audio ? 16_000 * seconds : 0;
      const bytes = Math.round((mbps * 1_000_000 / 8) * seconds + audioBytes);
      return {
        id: `clip-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        name: `${f.name}.mp4`,
        tag: f.tag,
        bytes,
        seconds,
        created: Date.now(),
      };
    });
    state.clips = [...newClips, ...state.clips].slice(0, 30);
    saveState();
    newClips.forEach((clip) => {
      log(`MediaStore insert → Movies/DuelShots/${clip.name} (${fmtSize(clip.bytes)})`, 'ok');
    });
    paintFiles();
  }

  ui.status = 'Stopped. Saved to Gallery → Movies/DuelShots';
  render();
  if (!silent) {
    showToast(`Saved ${files.length} file${files.length === 1 ? '' : 's'} to <b>Movies/DuelShots</b>`);
  }
}

/* ───────────────────────────── actions ───────────────────────────── */

function selectMode(key) {
  state.mode = key;
  saveState();
  render();
}

function setQuality(key) {
  const q = QUALITY[key];
  if (q.premium && !state.isPremium) return openPremiumDialog(q.label);
  state.quality = key;
  saveState();
  render();
}

function setCodec(key) {
  const c = CODECS[key];
  if (c.premium && !state.isPremium) return openPremiumDialog(c.label);
  state.codec = key;
  saveState();
  render();
}

function openPremiumDialog(feature) {
  ui.premiumDialog = { feature };
  render();
  requestAnimationFrame(() => {
    app.querySelector('.dialog-backdrop')?.classList.add('is-open');
  });
  log(`PremiumDialog("${feature}") — locked by AppViewModel`, 'warn');
}

function closePremiumDialog() {
  const el = app.querySelector('.dialog-backdrop');
  if (el) el.classList.remove('is-open');
  ui.premiumDialog = null;
  setTimeout(render, 190);
}

function upgradePremium() {
  state.isPremium = true;
  saveState();
  log('grantPremium() → AppPrefs.isPremium = true', 'ok');
  closePremiumDialog();
  paintSwitchStates();
  showToast('♛ Premium unlocked — 4K and HEVC are now selectable');
}

function switchTab(tab) {
  if (ui.recording) stopRecording(true);
  endCameraSession(false);
  ui.inRecord = false;
  ui.tab = tab;
  if (tab === 'GALLERY') {
    ui.galleryLoading = true;
    render();
    setTimeout(() => {
      ui.galleryLoading = false;
      render();
      log(`MediaStore query: RELATIVE_PATH LIKE "%/DuelShots%" → ${state.clips.length} clip(s)`, 'dim');
    }, 220);
  } else {
    render();
  }
}

function grantPermission() {
  ui.permission = true;
  state.permissionGranted = true;
  saveState();
  log('CAMERA + RECORD_AUDIO granted → DuelShotsApp composed', 'ok');
  render();
}

function showPermissionGate() {
  endCameraSession(false);
  ui.inRecord = false;
  ui.permission = false;
  state.permissionGranted = false;
  saveState();
  log('Permission gate shown — permissions not granted', 'warn');
  render();
}

function showToast(html) {
  ui.toast = { html };
  const el = app.querySelector('.toast');
  if (el) {
    el.innerHTML = html;
    requestAnimationFrame(() => el.classList.add('is-visible'));
  }
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    const node = app.querySelector('.toast');
    if (node) node.classList.remove('is-visible');
    ui.toast = null;
    setTimeout(() => {
      const stale = app.querySelector('.toast');
      if (stale && !ui.toast) stale.innerHTML = '';
    }, 220);
  }, 2800);
}

/* ───────────────────────────── markup ───────────────────────────── */

function logoMark(className) {
  return `
  <svg class="${className}" viewBox="0 0 100 100" aria-hidden="true">
    <path d="M91.35 60.26 A44 30 0 1 1 91.35 39.74" fill="none" stroke="url(#duelGrad)" stroke-width="3"/>
    <g>
      <circle cx="30" cy="50" r="20" fill="#05070D"/>
      <circle cx="30" cy="50" r="17" fill="none" stroke="url(#duelGrad)" stroke-width="3.5"/>
      <circle cx="30" cy="50" r="15" fill="#0B1024"/>
      <circle cx="26.6" cy="46.6" r="5.95" fill="#22D3EE"/>
    </g>
    <g>
      <circle cx="70" cy="50" r="20" fill="#05070D"/>
      <circle cx="70" cy="50" r="17" fill="none" stroke="url(#duelGrad)" stroke-width="3.5"/>
      <circle cx="70" cy="50" r="15" fill="#0B1024"/>
      <circle cx="66.6" cy="46.6" r="5.95" fill="#E94FD0"/>
    </g>
  </svg>`;
}

function wordmark(extra = '') {
  return `<div class="wordmark ${extra}"><b>Duel</b><i>Shots</i></div>`;
}

const TAB_ICONS = {
  HOME: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"><path d="M3.6 10.4 12 3.6l8.4 6.8V20a.9.9 0 0 1-.9.9h-4.7v-6.3H9.2v6.3H4.5a.9.9 0 0 1-.9-.9z"/></svg>',
  GALLERY: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 4.6 19 12 8 19.4z"/></svg>',
  SETTINGS: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><circle cx="12" cy="12" r="3.2"/><path d="M12 2.7v2.1M12 19.2v2.1M2.7 12h2.1M19.2 12h2.1M5.5 5.5l1.5 1.5M17 17l1.5 1.5M18.5 5.5 17 7M7 17l-1.5 1.5"/></svg>',
};

const TAB_LABELS = { HOME: 'Home', GALLERY: 'Gallery', SETTINGS: 'Settings' };

/* ───────── permission gate (MainActivity.kt) ───────── */

function screenPermission() {
  return `
  <section class="screen ${activeScreen() === 'PERMISSION' ? 'is-active' : ''}" id="screen-permission" ${activeScreen() === 'PERMISSION' ? '' : 'inert'}>
    <div class="permission">
      ${logoMark('perm-mark')}
      <p>DuelShots needs camera and microphone permissions to record from both cameras at the same time.</p>
      <button class="btn-solid" data-action="grant-permission">Request permission</button>
      <small>Simulated — the real app opens the Android permission dialog.</small>
    </div>
  </section>`;
}

/* ───────── home (HomeScreen.kt) ───────── */

function screenHome() {
  const quick = `${ASPECTS[state.frontAspect].label} · ${ASPECTS[state.backAspect].label}`;
  const quickSub = `${QUALITY[state.quality].label} · ${CODECS[state.codec].label}`;

  return `
  <section class="screen ${activeScreen() === 'HOME' ? 'is-active' : ''}" id="screen-home" ${activeScreen() === 'HOME' ? '' : 'inert'}>
    <div class="scroll" data-scroll="home">
      <div class="appbar">
        ${logoMark('mark')}
        ${wordmark()}
        <span class="pill ${state.isPremium ? 'is-premium' : ''}">${state.isPremium ? '♛ Premium' : 'Free'}</span>
      </div>

      <p class="home-tagline">Capture More. From Every Angle.</p>

      <button class="btn-primary" data-action="open-record">
        <svg class="cam" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <rect x="2.2" y="6.4" width="12.6" height="11.2" rx="2.4"/>
          <path d="M14.8 11.2 21.4 7.6v8.8l-6.6-3.6z"/>
          <circle cx="8.5" cy="12" r="2.6"/>
        </svg>
        <span>
          <b>Start Recording</b>
          <small>Choose your mode</small>
        </span>
      </button>

      <div class="section-label">Recording Mode</div>
      ${MODE_ORDER.map((key) => `
        <button class="card mode-row ${state.mode === key ? 'is-selected' : ''}" data-action="select-mode:${key}"
                aria-pressed="${state.mode === key}">
          <div class="row">
            <span class="mode-icon">${MODES[key].icon}</span>
            <span class="grow">
              <span class="mode-title" style="display:block">${MODES[key].title}</span>
              <span class="mode-sub" style="display:block">${MODES[key].subtitle}</span>
            </span>
            <span class="check" aria-hidden="true">✓</span>
          </div>
        </button>`).join('')}

      <div class="section-label">Quick Settings</div>
      <button class="card" data-action="open-settings">
        <div class="row">
          <span class="grow" style="min-width:0">
            <span class="quick-value">⚡&nbsp; ${quick}</span>
            <span class="quick-sub">${quickSub}</span>
          </span>
          <span class="chev">›</span>
        </div>
      </button>

      ${state.isPremium ? '' : `
      <button class="card card-gold" data-action="open-premium" style="margin-top:16px">
        <div class="row">
          <span class="gold" style="font-size:20px">♛</span>
          <span class="grow">
            <span class="bold" style="display:block">Go Premium</span>
            <span class="muted xs" style="display:block;margin-top:2px">4K · 60fps · HEVC · custom formats · watermark-free</span>
          </span>
        </div>
      </button>`}
    </div>
  </section>`;
}

/* ───────── record (RecordScreen.kt) ───────── */

function feedHtml({ label, ratio, composed }) {
  const src = 'assets/feed-back.jpg';
  if (composed) {
    return `
      <div class="feed is-composed" style="flex:1">
        <img src="assets/feed-back.jpg" alt="Rear camera preview" />
        <div class="pip"><img src="assets/feed-front.jpg" alt="Front camera preview" /></div>
        <span class="tag">BACK + FRONT</span>
        <span class="ratio">${ASPECTS[state.backAspect].short} · PiP</span>
      </div>`;
  }
  const isFront = label === 'FRONT';
  return `
    <div class="feed">
      <img src="${isFront ? 'assets/feed-front.jpg' : src}" alt="${label || 'Camera'} preview" />
      ${label ? `<span class="tag">${label}</span>` : ''}
      ${ratio ? `<span class="ratio">${ratio}</span>` : ''}
    </div>`;
}

function screenRecord() {
  const controller = ui.controller;
  const supported = controller ? controller.supported : state.dualSupported;
  const mode = ui.renderedMode || state.mode;

  let preview;
  if (!supported) {
    preview = feedHtml({ label: 'CAMERA', ratio: ASPECTS[state.backAspect].label });
  } else if (mode === 'DUAL_VIEW') {
    preview = feedHtml({ composed: true });
  } else {
    preview = `${feedHtml({ label: 'BACK', ratio: ASPECTS[state.backAspect].label })}
               ${feedHtml({ label: 'FRONT', ratio: ASPECTS[state.frontAspect].label })}`;
  }

  const saved = ui.savedNames.length
    ? `<div class="saved-line">Saved: <b>${ui.savedNames.map(escapeHtml).join(', ')}</b></div>`
    : '';

  return `
  <section class="screen dark ${activeScreen() === 'RECORD' ? 'is-active' : ''}" id="screen-record" ${activeScreen() === 'RECORD' ? '' : 'inert'}>
    <div class="record-top">
      <button class="icon-chip" data-action="exit-record" title="Close">✕</button>
      <div class="timer ${ui.recording ? 'is-recording' : ''}">
        <span class="dot"></span>
        <span data-timer>${fmtClock(ui.elapsed)}</span>
      </div>
      <span style="flex:1"></span>
      <button class="icon-chip" data-action="flip-camera" title="Switch camera">⟳</button>
    </div>

    <div class="preview">${preview}</div>

    <div class="record-status">
      <div class="status-line">${escapeHtml(ui.status || 'Starting cameras…')}</div>
      ${saved}
    </div>

    <div class="record-controls">
      <button class="record-btn ${ui.recording ? 'is-recording' : ''}" data-action="toggle-record"
              ${ui.ready ? '' : 'disabled'} aria-label="${ui.recording ? 'Stop recording' : 'Start recording'}">
        <span class="core"></span>
      </button>
    </div>
  </section>`;
}

/* ───────── settings (SettingsScreen.kt) ───────── */

function screenSettings() {
  return `
  <section class="screen ${activeScreen() === 'SETTINGS' ? 'is-active' : ''}" id="screen-settings" ${activeScreen() === 'SETTINGS' ? '' : 'inert'}>
    <div class="scroll" data-scroll="settings">
      <h1 class="screen-title">Settings</h1>

      <div class="section-label">Recording Mode</div>
      ${MODE_ORDER.map((key) => `
        <button class="card radio-row" data-action="select-mode:${key}">
          <div class="row">
            <span class="grow">
              <span class="row-title" style="display:block">${MODES[key].title}</span>
              <span class="row-sub" style="display:block">${MODES[key].subtitle}</span>
            </span>
            <span class="radio ${state.mode === key ? 'is-on' : ''}"><i></i></span>
          </div>
        </button>`).join('')}

      <div class="section-label">Camera Formats</div>
      <div class="card">
        <div class="bold semi">Front Camera</div>
        <div class="chips">
          ${ASPECT_ORDER.map((key) => `
            <button class="chip ${state.frontAspect === key ? 'is-on' : ''}" data-action="set-front:${key}">${ASPECTS[key].label}</button>`).join('')}
        </div>
      </div>
      <div class="card">
        <div class="bold semi">Back Camera</div>
        <div class="chips">
          ${ASPECT_ORDER.map((key) => `
            <button class="chip ${state.backAspect === key ? 'is-on' : ''}" data-action="set-back:${key}">${ASPECTS[key].label}</button>`).join('')}
        </div>
      </div>

      <div class="section-label">Video Quality</div>
      <div class="card">
        <div class="row">
          <span class="grow">Resolution</span>
          <div class="segmented">
            ${QUALITY_ORDER.map((key) => `
              <button class="${state.quality === key ? 'is-on' : ''}" data-action="set-quality:${key}">${QUALITY[key].segment}</button>`).join('')}
          </div>
        </div>
      </div>

      <div class="section-label">Container / Codec</div>
      <div class="card">
        <div class="row">
          <span class="grow">Format</span>
          <div class="segmented">
            ${CODEC_ORDER.map((key) => `
              <button class="${state.codec === key ? 'is-on' : ''}" data-action="set-codec:${key}">${CODECS[key].segment}</button>`).join('')}
          </div>
        </div>
      </div>

      <div class="card card-gold premium-card" style="margin-top:16px">
        <h3><span class="crown">♛</span>&nbsp; Premium</h3>
        <ul>
          <li><b>✓</b> More format options (4K, HEVC)</li>
          <li><b>✓</b> 60fps &amp; custom bitrate</li>
          <li><b>✓</b> Per-camera aspect control</li>
          <li><b>✓</b> No watermark</li>
        </ul>
        ${state.isPremium
          ? '<div class="active">♛ Premium active</div>'
          : '<button class="btn-gold" data-action="open-premium">♛ Go Premium</button>'}
      </div>
    </div>
  </section>`;
}

/* ───────── gallery (GalleryScreen.kt) ───────── */

function screenGallery() {
  let body;
  if (ui.galleryLoading) {
    body = '<p class="muted small">Loading…</p>';
  } else if (!state.clips.length) {
    body = '<div class="empty-state">No recordings yet. Tap “Start Recording”.</div>';
  } else {
    body = state.clips.map((clip) => `
      <button class="clip" data-action="open-clip:${clip.id}">
        <span class="play">▶</span>
        <span class="grow" style="min-width:0">
          <span class="name truncate" style="display:block">${escapeHtml(clip.name)}</span>
          <span class="meta">${fmtSize(clip.bytes)} · ${fmtClock(clip.seconds)}</span>
        </span>
        <span class="chev">›</span>
      </button>`).join('');
  }

  return `
  <section class="screen ${activeScreen() === 'GALLERY' ? 'is-active' : ''}" id="screen-gallery" ${activeScreen() === 'GALLERY' ? '' : 'inert'}>
    <div class="scroll" data-scroll="gallery">
      <h1 class="screen-title">Gallery</h1>
      <p class="gallery-sub">Recordings are saved to Gallery → Movies/DuelShots</p>
      ${body}
    </div>
  </section>`;
}

function activeScreen() {
  if (!ui.permission) return 'PERMISSION';
  if (ui.inRecord) return 'RECORD';
  return ui.tab;
}

function bottomBar() {
  if (!ui.permission || ui.inRecord) return '';
  return `
  <nav class="bottom-bar">
    ${['HOME', 'GALLERY', 'SETTINGS'].map((key) => `
      <button class="tab ${ui.tab === key ? 'is-active' : ''}" data-action="tab:${key}">
        ${TAB_ICONS[key]}
        <span>${TAB_LABELS[key]}</span>
      </button>`).join('')}
  </nav>`;
}

function dialogMarkup() {
  if (!ui.premiumDialog) {
    return '<div class="dialog-backdrop"><div class="dialog"></div></div>';
  }
  return `
  <div class="dialog-backdrop ${app.querySelector('.dialog-backdrop.is-open') ? 'is-open' : ''}">
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dlg-title">
      <h3 id="dlg-title">♛&nbsp; ${escapeHtml(ui.premiumDialog.feature)}</h3>
      <p>This is a Premium feature. Upgrade for 4K, 60fps, HEVC, per-camera format control and watermark-free export.</p>
      <div class="actions">
        <button class="later" data-action="premium-later">Later</button>
        <button class="upgrade" data-action="premium-upgrade">Upgrade</button>
      </div>
    </div>
  </div>`;
}

/* ───────────────────────────── rendering ───────────────────────────── */

const scrollMemory = {};

function captureScroll() {
  app.querySelectorAll('.scroll').forEach((el) => {
    scrollMemory[el.dataset.scroll] = el.scrollTop;
  });
}

function restoreScroll() {
  app.querySelectorAll('.scroll').forEach((el) => {
    const value = scrollMemory[el.dataset.scroll];
    if (typeof value === 'number') el.scrollTop = value;
  });
}

function render() {
  captureScroll();

  const toast = ui.toast ? `<div class="toast">${ui.toast.html}</div>` : '<div class="toast"></div>';

  app.innerHTML = `
    <div class="screen-stack">
      ${screenPermission()}
      ${screenHome()}
      ${screenRecord()}
      ${screenSettings()}
      ${screenGallery()}
      ${toast}
      ${dialogMarkup()}
    </div>
    ${bottomBar()}
  `;

  restoreScroll();
  document.getElementById('device').classList.toggle('is-recording', ui.recording);
  paintCaption();
  paintJumpList();
  paintFiles();
  paintSwitchStates();

  const timer = app.querySelector('[data-timer]');
  if (timer) timer.textContent = fmtClock(ui.elapsed);
}

function paintCaption() {
  const screen = activeScreen();
  const parts = {
    PERMISSION: ['Permission gate', 'MainActivity.kt'],
    HOME: ['Home', 'mode select'],
    SETTINGS: ['Settings', '★ = premium'],
    GALLERY: ['Gallery', 'MediaStore'],
    RECORD: ['Record', ui.controller && !ui.controller.supported
      ? 'single-camera fallback'
      : (ui.renderedMode === 'DUAL_VIEW' ? 'composed dual view' : 'dual live preview')],
  };
  const [left, right] = parts[screen];
  captionEl.innerHTML = `${left} · <b>${right}</b>`;
}

function paintJumpList() {
  const current = activeScreen().toLowerCase();
  document.querySelectorAll('#jumpList button').forEach((btn) => {
    btn.classList.toggle('is-current', btn.dataset.jump === current);
  });
}

function paintFiles() {
  if (!filesEl) return;
  if (!state.clips.length) {
    filesEl.innerHTML = '<p class="files-empty">Nothing recorded yet.</p>';
    return;
  }
  filesEl.innerHTML = state.clips.map((clip) => `
    <div class="file-row">
      <span class="ftag">${clip.tag}</span>
      <span class="fname" title="${escapeHtml(clip.name)}">${escapeHtml(clip.name)}</span>
      <span class="fsize">${fmtSize(clip.bytes)}</span>
    </div>`).join('');
}

function paintSwitchStates() {
  const dual = document.getElementById('toggleDual');
  const premium = document.getElementById('togglePremium');
  if (dual) dual.checked = state.dualSupported;
  if (premium) premium.checked = state.isPremium;
}

/* ───────────────────────────── events ───────────────────────────── */

app.addEventListener('click', (event) => {
  const target = event.target.closest('[data-action]');
  if (target) {
    const [action, arg] = target.dataset.action.split(':');
    handleAction(action, arg);
    return;
  }
  if (event.target.classList.contains('dialog-backdrop')) {
    closePremiumDialog();
  }
});

function handleAction(action, arg) {
  switch (action) {
    case 'grant-permission': grantPermission(); break;
    case 'open-record': goRecord(); break;
    case 'select-mode': selectMode(arg); break;
    case 'open-settings': switchTab('SETTINGS'); break;
    case 'open-premium': openPremiumDialog('DuelShots Premium'); break;
    case 'tab': switchTab(arg); break;
    case 'exit-record': exitRecord(); break;
    case 'toggle-record': toggleRecording(); break;
    case 'flip-camera':
      log('⟳ icon chip is a placeholder in RecordScreen.kt — no camera switch wired', 'dim');
      showToast('Camera switch is not wired yet in the Android source');
      break;
    case 'set-front':
      state.frontAspect = arg; saveState(); render();
      log(`AppPrefs.frontAspect = ${arg} (${ASPECTS[arg].cxRatio})`, 'dim');
      break;
    case 'set-back':
      state.backAspect = arg; saveState(); render();
      log(`AppPrefs.backAspect = ${arg} (${ASPECTS[arg].cxRatio})`, 'dim');
      break;
    case 'set-quality': setQuality(arg); break;
    case 'set-codec': setCodec(arg); break;
    case 'premium-upgrade': upgradePremium(); break;
    case 'premium-later': closePremiumDialog(); break;
    case 'open-clip': {
      const clip = state.clips.find((c) => c.id === arg);
      if (!clip) break;
      log(`Intent(ACTION_VIEW) → content://media/external/video/media/${clip.id}`, 'dim');
      showToast(`Opening <b>${escapeHtml(clip.name)}</b> in the system player`);
      break;
    }
    default: break;
  }
}

/* rail controls */

document.getElementById('toggleDual').addEventListener('change', async (event) => {
  state.dualSupported = event.target.checked;
  saveState();
  log(`Device capability: dual concurrent cameras ${state.dualSupported ? 'ON' : 'OFF'}`, 'info');
  if (ui.inRecord) {
    if (ui.recording) stopRecording(true);
    await beginCameraSession();
  }
});

document.getElementById('togglePremium').addEventListener('change', (event) => {
  state.isPremium = event.target.checked;
  saveState();
  log(state.isPremium
    ? 'grantPremium() → AppPrefs.isPremium = true'
    : 'isPremium revoked (only the preview rail can do this)', state.isPremium ? 'ok' : 'warn');
  render();
});

document.getElementById('resetDemo').addEventListener('click', () => {
  localStorage.removeItem(STORAGE_KEY);
  location.reload();
});

document.getElementById('clearLog').addEventListener('click', () => {
  logLines.length = 0;
  paintLog();
});

document.querySelectorAll('#jumpList button').forEach((btn) => {
  btn.addEventListener('click', () => {
    const jump = btn.dataset.jump;
    if (jump === 'permission') return showPermissionGate();
    if (jump === 'record') return goRecord();
    switchTab(jump.toUpperCase());
  });
});

/* ───────────────────────────── device fit + clock ───────────────────────────── */

function fitDevice() {
  const column = document.querySelector('.phone-column');
  if (!column) return;
  const deviceH = 812;
  const chromeH = 26;
  const availableW = column.clientWidth;
  const availableH = window.innerHeight - 96;
  /* Desktop keeps the whole device on screen; narrow layouts scroll, so width is
     the only constraint there. */
  const scale = window.innerWidth > 1180
    ? Math.min(1, availableW / 400, availableH / (deviceH + chromeH))
    : Math.min(1, availableW / 400);
  document.documentElement.style.setProperty('--fit', scale.toFixed(3));
  const fit = document.getElementById('deviceFit');
  if (fit) fit.style.height = `${deviceH * scale + chromeH}px`;
}

window.addEventListener('resize', fitDevice);
window.addEventListener('orientationchange', () => setTimeout(fitDevice, 120));

function paintClock() {
  const el = document.querySelector('.sb-time');
  if (el) {
    const now = new Date();
    el.textContent = `${now.getHours() % 12 === 0 ? 12 : now.getHours() % 12}:${pad2(now.getMinutes())}`;
  }
}

/* ───────────────────────────── boot ───────────────────────────── */

function boot() {
  render();
  fitDevice();
  paintClock();
  setInterval(paintClock, 20_000);
  paintLog();
  paintFiles();

  log('DuelShots preview ready — Kotlin/Compose UI simulated in the browser', 'ok');
  if (!ui.permission) {
    log('Waiting for CAMERA + RECORD_AUDIO permissions (MainActivity)', 'warn');
  } else {
    log(`Restored AppPrefs — mode=${state.mode}, front=${state.frontAspect}, back=${state.backAspect}, quality=${state.quality}, codec=${state.codec}, premium=${state.isPremium}`, 'dim');
  }
}

boot();
