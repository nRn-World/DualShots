# DuelShots · interactive preview

A clickable, browser-based preview of the DuelShots Android app, so the UI and the
recording logic can be reviewed without an Android SDK, an emulator or a physical
device.

It is **not** a second implementation of the product — it is a faithful port of the
Kotlin/Compose sources that already live in this repo:

| Screen / behaviour | Source of truth |
|---|---|
| Permission gate | `MainActivity.kt` |
| Home, mode select, quick settings | `ui/home/HomeScreen.kt` |
| Record screen, timer, chips | `ui/record/RecordScreen.kt` |
| Settings, premium locks | `ui/settings/SettingsScreen.kt` |
| Gallery list, MediaStore query | `ui/gallery/GalleryScreen.kt` |
| Colours, gradient, cards, segmented control | `ui/theme/Color.kt`, `ui/components/Components.kt` |
| Modes, aspect ratios, quality, codec | `model/Models.kt` |
| Settings persistence | `data/AppPrefs.kt` |
| Premium gating, `grantPremium()` | `ui/AppViewModel.kt` |
| Bind / fallback / recording / file names | `camera/DualCameraController.kt` |

## Run it

```bash
cd web-preview
npm start          # → http://localhost:3000
```

No install step, no dependencies: `dev-server.js` is a ~100 line static server.

Opening `index.html` directly from disk works too, though a couple of browsers
restrict `localStorage` on `file://` URLs (the preview then simply forgets state
between reloads).

## What you can try

- **Permission gate → Home.** Grant or revoke `CAMERA` + `RECORD_AUDIO` like the real `MainActivity`.
- **Three recording modes.** Dual View shows the composed picture-in-picture feed, Separate
  Videos shows two live feeds, Both Options records all three files.
- **Record.** The timer counts up, files are named exactly like the Kotlin code
  (`duelshot_combined_<stamp>.mp4`, `duelshot_back_<stamp>.mp4`, `duelshot_front_<stamp>.mp4`),
  and they land in the Gallery and the *Files written* panel.
- **Single-camera fallback.** Flip **Dual concurrent cameras** off in the left rail to
  simulate a device whose HAL refuses concurrent streams — the app degrades to one
  camera and one file, with the same status line as the APK.
- **Premium.** Tap `4K★` or `HEVC★` while on Free to see the premium dialog; **Upgrade**
  calls the mocked `grantPremium()` and unlocks both.
- **Settings persist** in `localStorage` (mirroring `SharedPreferences`).
- **Camera log** (right rail) narrates what the controller would be doing: selector
  discovery, `CompositionSettings` alphas/offsets, `UseCaseGroup` binding, MediaStore inserts.

## Scope and honesty

- Camera frames are stand-in stills (`assets/`); no camera is accessed.
- Playback is simulated — clicking a clip logs the `ACTION_VIEW` intent it would fire.
- The `⟳` chip is a placeholder in the Android source, and the preview says so when tapped.
- Recording sizes are estimated from the selected quality's bitrate, not measured.

## Files

```
web-preview/
├── index.html      page shell, rails, device frame
├── styles.css      design tokens + all screen styles
├── app.js          models, "controller", screens, rendering, events
├── dev-server.js   static server + /__lint (dev aid)
└── assets/         stand-in front/back camera stills
```

## Self-check

```bash
npm run lint        # verifies every data-action in the markup has a handler
```
