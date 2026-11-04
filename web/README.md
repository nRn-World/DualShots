# DuelShots — Web Preview

A dependency-free browser replica of the native Android app, so the UI and the
whole recording flow can be tried without an Android SDK, emulator or device.

```bash
python3 web/serve.py 8000
# open http://localhost:8000
```

`serve.py` is a thin static server that sends `Cache-Control: no-store`, so the
preview always reflects the current build instead of a cached one.

No build step, no npm packages — just `index.html`, `styles.css` and `app.js`.

## What it mirrors

| Native app | Web preview |
|---|---|
| `ui/theme/Color.kt` | CSS custom properties in `styles.css` |
| `ui/home/HomeScreen.kt` | `renderHome()` |
| `ui/record/RecordScreen.kt` | `buildRecordShell()` + camera engine |
| `ui/settings/SettingsScreen.kt` | `renderSettings()` |
| `ui/gallery/GalleryScreen.kt` | `renderGallery()` |
| `model/Models.kt` | `MODES`, `ASPECTS`, `QUALITIES`, `CODECS` |
| `data/AppPrefs.kt` | `localStorage` (`duelshot_prefs`) |
| `camera/DualCameraController.kt` | `startCameras()` / `startRecording()` |
| `grantPremium()` (mocked billing) | premium dialog → `state.premium` |

## Design

The interface is built like an analog camera manual rather than a dashboard.

- **Poster menu.** The home screen is a single column: the split-lens mark and the
  DuelShots wordmark, the tagline in tracked mono, one full-width *Start Recording*
  action, then rounded cards for Gallery, Settings, Premium and About — each with a lit
  icon orb and a monospaced value (clip count, formats, Pro, build) on the right.
- **One accent, used sparingly.** Orange appears on the brand gradient, the icon orbs,
  the record state and premium. Everything else is warm near-black, paper white and
  hairline rules.
- **Monospaced metadata.** Every technical value (formats, resolution, durations,
  filenames, section labels) is set in mono with wide tracking; prose stays in the system
  sans. Settings and the gallery are hairline lists, not stacks of shadowed cards.
- **Viewfinder HUD.** The record screen frames the preview with corner brackets, a
  red-dot timer, a torch control in the top bar, a white-ring shutter and mono status
  lines. A fine film grain sits over the whole device.

Colour tokens live in `:root` in `styles.css`; the type stacks are `--font` and `--mono`.

## What actually works

- **Three recording modes.** Dual View composes the two feeds into one file with a
  picture-in-picture inset (34 % width, top-right — same geometry as
  `CompositionSettings` in the Kotlin controller). Separate Videos records two
  files, Both Options records all three.
- **Real files.** Recording is done with canvas composition + `MediaRecorder`;
  finished clips appear in the Gallery as poster thumbnails, play inline and can
  be downloaded or deleted. Filenames follow the Android scheme:
  `duelshot_combined_YYYYMMDD_HHMMSS.mp4`.
- **Camera handling.** Both cameras are requested at once. If the browser refuses
  the second stream the preview degrades to the same single-camera fallback the
  app uses. If camera access is blocked entirely (permissions, iframe policy,
  plain HTTP) an animated demo feed keeps the recorder usable.
- **Camera setup while filming.** Tap the mode pill on the record screen to set the
  format (9:16, 16:9, 1:1, 4:3) for the front and back camera independently, swap
  them in one tap, or change the recording mode — no trip to Settings. A take keeps
  the formats it started with (a file cannot change resolution mid-stream), so
  changes made while recording start with the next take; the dock always shows the
  formats currently in use.
- **Zoom chip.** The 1×/2×/3× chip on the record dock crops the preview and the
  recorded frame together, so what you frame is what you get.
- **Movable overlay.** In Dual View the front camera sits in an inset you can drag
  around and pinch to resize (double-tap resets). The position and size are used for
  the composed recording, not just the preview.
- **Settings persistence** and the freemium gating (4K, HEVC) behave like the app:
  picking a locked option opens the Pro sheet, and unlocking applies that option.

## Known differences

- Browsers rarely allow two simultaneous camera streams, so true dual capture is
  device- and browser-dependent; the Android app uses the CameraX Dual Concurrent
  Camera API for this.
- Output is MP4 where the browser supports it, otherwise WebM (`MediaRecorder`
  capability, not a preference).
- The 4K option caps the composition at 1600 px on the long edge to keep the
  browser responsive; the native app records real 4K.
- Clips live in memory for the session — reloading the page clears the gallery
  (the app writes to `Movies/DuelShots` via `MediaStore`).

The real app is still the Android module in `../app` — see the root README for the
`./gradlew :app:assembleDebug` build.
