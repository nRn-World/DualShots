# ⚔️ DuelShots

**Capture more. From every angle.**

DuelShots is a native Android camera app that records from the **front and back cameras simultaneously**. Choose a single composed video, two independent files, or both at once — with per-camera format controls and a freemium premium tier.

Built with **Kotlin**, **Jetpack Compose**, and the **CameraX Dual Concurrent Camera** API.

---

## 📸 Screenshots

<table>
  <tr>
    <td align="center"><b>Home</b><br/><img src="screenshots/01-home.png" width="210" alt="Home screen"/></td>
    <td align="center"><b>Record</b><br/><img src="screenshots/02-record.png" width="210" alt="Live record screen"/></td>
    <td align="center"><b>Settings</b><br/><img src="screenshots/03-settings.png" width="210" alt="Settings screen"/></td>
    <td align="center"><b>Gallery</b><br/><img src="screenshots/04-gallery.png" width="210" alt="Gallery screen"/></td>
  </tr>
</table>

---

## ✨ Features

- **Simultaneous dual-camera recording** — front + back at the same time (device-dependent).
- **Three recording modes**:
  - **Dual View** — both cameras composed into **one** MP4 (picture-in-picture).
  - **Separate Videos** — **two** files, one per camera.
  - **Both Options** — the composed video **plus** the two separate files.
- **Per-camera format control** — set aspect ratio for the front and back camera independently (e.g. front 9:16, back 16:9).
- **Freemium model** — core recording is free; premium unlocks higher resolutions (4K), codecs (HEVC), and advanced format control.
- **Gallery** — recordings are saved to `Movies/DuelShots` via `MediaStore` and are immediately visible in the system gallery.
- **Robust fallback** — if a device does not support concurrent cameras, the app automatically degrades to a working single-camera mode so you can always record.
- **Modern dark design system** — custom logo, gradient UI, Material 3 theming.

## 🧩 Recording modes at a glance

| Mode | Output | Notes |
|------|--------|-------|
| Dual View | 1 composed MP4 | Uses CameraX composition; PiP layout |
| Separate Videos | 2 MP4 files | One per camera |
| Both Options | 3 MP4 files | Composed + two separate |

## 🛠 Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Camera:** CameraX 1.5.0 (Dual Concurrent Camera + VideoCapture)
- **Storage:** MediaStore (`MediaStoreOutputOptions`)
- **Preferences:** SharedPreferences
- **Architecture:** ViewModel + lightweight controller layer
- **Build:** Gradle (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.09.03)

## 📱 Requirements

- **Android:** `minSdk 24` (Android 7.0+), `targetSdk 34`
- **JDK:** 17 or 21 for building (21 recommended)
- **Dual-camera recording** requires a device whose camera HAL supports concurrent camera streams. Many devices do not — the app then falls back to single-camera mode automatically.

## 🖥️ Web preview (no Android SDK needed)

Want to click through the app right away? A dependency-free browser replica lives in
[`web/`](web) — same screens, same design tokens, same three recording modes, with the
recording pipeline reimplemented on `getUserMedia` + `MediaRecorder`:

```bash
python3 web/serve.py 8000
# open http://localhost:8000
```

It records real (downloadable) clips into the Gallery tab, persists settings in
`localStorage`, and falls back to a single camera — or an animated demo feed — when the
browser cannot open both cameras. The native Android app remains the reference
implementation; see [`web/README.md`](web/README.md) for the mapping.

## 🚀 Getting started

### Build from source

```bash
git clone https://github.com/nRn-World/DuelShots.git
cd DuelShots

# Point Gradle at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# Build the debug APK
./gradlew :app:assembleDebug
```

The APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** The Android emulator does not support concurrent dual-camera. Use it to validate the UI and single-camera recording; test true dual-camera capture on a physical device.

## 🔐 Permissions

- `CAMERA` — required for recording.
- `RECORD_AUDIO` — required for audio in recordings.

## 💎 Premium

The premium tier is a design-ready monetization layer. In this build the purchase is **mocked** (`grantPremium()`); wire it to **Google Play Billing** before release. Premium unlocks:

- 4K Ultra HD recording
- HEVC codec
- 60fps & custom bitrate
- Full per-camera aspect control
- Watermark-free export

## 📂 Project structure

```
DuelShots/
├── app/src/main/java/com/nrworld/duelshot/
│   ├── MainActivity.kt              # Entry point, permissions, theming
│   ├── camera/DualCameraController.kt  # CameraX binding + 3 modes + fallback
│   ├── data/AppPrefs.kt             # SharedPreferences wrapper
│   ├── model/Models.kt              # Enums: modes, aspect, quality, codec
│   └── ui/
│       ├── AppViewModel.kt          # Observable settings state
│       ├── DuelShotsApp.kt          # Navigation shell + bottom bar
│       ├── components/Components.kt # Logo, buttons, cards, dialogs
│       ├── theme/                   # Color, Type, Theme
│       ├── home/HomeScreen.kt
│       ├── record/RecordScreen.kt
│       ├── settings/SettingsScreen.kt
│       └── gallery/GalleryScreen.kt
├── design/                          # Design poster & mockups
└── build.gradle.kts / settings.gradle.kts
```

## 🗺 Roadmap

- [ ] Real Google Play Billing integration
- [ ] Manual front/back switch in single-camera mode
- [ ] In-app video trimmer & export presets
- [ ] Configurable picture-in-picture layout for Dual View

## 🤝 Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change.

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.

---

Made by **nRn World**.
