# DuelShots — Web Preview

A dependency-free browser replica of the native Android app, so the UI and the
whole recording flow can be tried without an Android SDK, emulator or device.

```bash
python3 -m http.server 8000 --directory web
# open http://localhost:8000
```

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

## What actually works

- **Three recording modes.** Dual View composes the two feeds into one file with a
  picture-in-picture inset (34 % width, top-right — same geometry as
  `CompositionSettings` in the Kotlin controller). Separate Videos records two
  files, Both Options records all three.
- **Real files.** Recording is done with canvas composition + `MediaRecorder`;
  finished clips appear in the Gallery tab, play inline and can be downloaded.
  Filenames follow the Android scheme: `duelshot_combined_YYYYMMDD_HHMMSS.mp4`.
- **Camera handling.** Both cameras are requested at once. If the browser refuses
  the second stream the preview degrades to the same single-camera fallback the
  app uses. If camera access is blocked entirely (permissions, iframe policy,
  plain HTTP) an animated demo feed keeps the recorder usable.
- **Settings persistence** and the freemium gating (4K, HEVC) behave like the app.

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
