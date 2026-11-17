# 7xTubeDash · Zeekr 7X Media & AR-HUD Suite

**7xTubeDash** is an automotive-optimized Android media player built for the **Zeekr 7X** (Snapdragon 8295 cockpit running Android 12 / Zeekr OS). It overcomes the native D-gear video lockout restriction by detaching playback into hardware-accelerated system overlay services and secondary display projection.

---

## 🚗 Core Features for Zeekr 7X

### 1. D-Gear Video Lockout Bypass
* **Method 1A — Floating Pop-Up Window (`TYPE_APPLICATION_OVERLAY`)**:
  * Detaches from the foreground task stack so Zeekr OS cannot suspend it when shifting into Drive.
  * Floats directly over native Zeekr Navigation / 360° cameras.
  * Supports **drag-to-move**, **two-finger pinch-to-resize**, **double-tap** to toggle Small (520×320) vs Large (820×480), and **edge-docking** (push to screen edge to collapse into a slim tab; tap to expand).
* **Method 1B — Android 12 Picture-in-Picture (`onUserLeaveHint`)**:
  * Seamless fallback PiP window.
* **Auto-Resume Behavior**:
  * Remembers whichever mode was last active (Center Pop-Up, AR-HUD, or Dual Output) and automatically re-engages when shifting from Park into Drive.

### 2. Windshield AR-HUD Optical Projection
* Binds to the Zeekr 7X secondary AR-HUD display via Android `DisplayManager` (`createDisplayContext` with `Presentation` fallback).
* **Display ID Scanner & Live Test Pattern**:
  * Cockpit displays (Display 1, Display 2, Display 3) are enumerated.
  * One-tap **"Test Pattern"** broadcasts a 6-second high-contrast alignment card so you can verify which display ID illuminates the windshield AR-HUD.
* **Safe-Zone Presets**:
  * Quick presets (`Corner`, `Banner`, `Wide`) keep video playback clear of critical ADAS telemetry, speed readouts, and navigation arrows.
  * Fine-grained sliders for Scale (%), X Offset (px), and Y Offset (px).

### 3. Media Sources & Browser
* **YouTube Desktop Player**: Custom desktop user-agent, automatic ad-skip injection, and auto-fullscreening inside floating frames.
* **Quick URL & Bookmarks Bar**: 1-tap presets for **YouTube, Bilibili, Netflix, Disney+, and Twitch**, plus custom URL entry.

### 4. Audio Focus & Steering Wheel Controls
* **Constant High-Volume Audio**: Requests `AUDIOFOCUS_GAIN` without ducking or lowering volume during Zeekr navigation voice announcements.
* **Steering Wheel Media Integration (`MediaSessionCompat`)**:
  * **Play / Pause**: Steering wheel media button / toggle.
  * **Skip Forward 10s**: Steering wheel right scroll up / next track.
  * **Skip Backward 10s**: Steering wheel right scroll down / previous track.

---

## 📲 How to Download & Install on Zeekr 7X

### Method A: Direct Download via Zeekr 7X In-Car Browser (Recommended)
1. In your Zeekr 7X, open the built-in Web Browser or App Lab browser.
2. Navigate to your GitHub repository Releases page:
   ```
   https://github.com/<your-username>/<your-repo>/releases/latest/download/7xTubeDash-arm64.apk
   ```
3. Once downloaded, open **App Lab / File Manager** and install the APK.
4. **⚠️ Critical Zeekr Installation Step**: When the installer finishes, tap **"Back"** at the top-left corner. **Never tap "Open"** directly from the installer package manager.
5. Launch **7xTubeDash** from your Zeekr app drawer.
6. Grant the **"Appear on top"** (`SYSTEM_ALERT_WINDOW`) permission when prompted.

---

## 🛠 Local & Cloud Build Instructions

### GitHub Actions (Cloud Builder)
* Push this repository to GitHub or trigger the **Build 7xTubeDash APK** action under the **Actions** tab.
* The workflow builds `7xTubeDash-arm64.apk` with JDK 17 and publishes it as a downloadable release asset.

### Local Build on Mac
Run the automated build script:
```bash
./scripts/build-local.sh
```
The compiled APK will be output to:
```
release/7xTubeDash-arm64.apk
```
