# 7xTubeDash 2.0 · Zeekr 7X Google Maps AR-HUD & Media Suite

**7xTubeDash 2.0** brings **Google Maps directly to the Zeekr 7X Windshield AR-HUD** and **Center Screen Floating Pop-Up / PiP**, overcoming Zeekr OS D-gear driving restrictions.

---

## 🗺️ What's New in Version 2.0

### 1. Google Maps on Windshield AR-HUD
* **Full Map Projection**: Projects full Google Maps directly onto the Zeekr 7X AR-HUD projection plane via `DisplayManager` secondary display context (with `Presentation` fallback).
* **Default & Satellite Layers**: 1-tap toggle between **Vector Street Map** and **Photorealistic Satellite Imagery**.
* **Top-Left HUD Telemetry Card**:
  * Pinned in the top-left corner of the windshield HUD.
  * **Bold Turn Maneuver Arrow**: `↱` Right, `↰` Left, `⇡` Straight, `↺` U-turn, `⮬` Slight Right in high-contrast emerald green.
  * **Distance to Next Maneuver**: e.g., `250 m` or `1.2 km`.
  * **Next Street / Highway**: e.g., `Rama IX Rd` / `Highway 101 N`.
  * **Trip Telemetry**: Arrival time (`22:45`), ETA duration (`18 min`), and remaining distance (`14 km`).
  * **Cruising Mode**: When not navigating an active route, displays live vehicle speed (`72 km/h`) and GNSS status.

### 2. Independent Multi-Display Routing
* **Center Screen & AR-HUD Multitasking**:
  * Run **YouTube on the Center Pop-Up** (or full center display) while projecting **Google Maps on the Windshield AR-HUD**.
  * Or run **Google Maps on both** (Center Pop-Up + AR-HUD).
  * Or run **YouTube on both**.
  * 1-tap switcher on the Left Rail and on the Floating Window top bar.

### 3. Center Screen Remote & Sync
* Touch the Center Screen or Floating Pop-Up to search destinations, click places, zoom in/out (`+` / `-`), toggle Satellite view, or recenter your location.
* Tap **"Push to HUD"** on the center screen to beam your active Google Maps route directly to the windshield AR-HUD.

### 4. Native Vehicle GPS & Audio Mixing
* **High-Precision Snapdragon 8295 GNSS**: Directly connects to Zeekr 7X's hardware GPS receiver without requiring Google Play Services (GMS).
* **Dual Audio Mixing**: Google Maps turn-by-turn spoken guidance plays alongside media (YouTube/music) at full volume without ducking or cutting off your audio.

---

## 📲 How to Download & Install Directly on Zeekr 7X

### Method A: Direct Download via Zeekr In-Car Browser (Recommended)
1. Push this repository to your GitHub account:
   ```bash
   git remote add origin https://github.com/<your-username>/<your-repo-name>.git
   git push -u origin main
   git tag v2.0.0
   git push origin v2.0.0
   ```
2. Open the built-in browser on your Zeekr 7X center screen and enter:
   ```
   https://github.com/<your-username>/<your-repo-name>/releases/latest/download/7xTubeDash-arm64.apk
   ```
3. Open **App Lab / File Manager** and install the APK.
4. **⚠️ Critical Zeekr Installation Rule**: When the installer finishes, tap **"Back"** at the top-left corner. **Never tap "Open"** directly from the installer package manager.
5. Launch **7xTubeDash** from the Zeekr app drawer and grant **"Appear on top"** (`SYSTEM_ALERT_WINDOW`) and **Location** permissions.

### Method B: USB Drive
Copy [`release/7xTubeDash-arm64.apk`](release/7xTubeDash-arm64.apk) to a USB stick, plug into the center console, and install via App Lab.

---

## 🚗 In-Car Controls Quick Reference

| Action | Control |
|---|---|
| **Toggle Street / Satellite Layer** | Tap `Sat/Def` on Left Rail or `Layer` on Floating Window |
| **Switch Center Pop-Up (Tube ↔ Maps)** | Tap `To Maps` / `To Tube` on Floating Window top bar |
| **Dock Window into Thin Edge Tab** | Tap `Dock` or push window near screen edge |
| **Resize Floating Window** | Two-finger pinch or double-tap top bar for Small/Large |
| **Zoom Maps (+ / -)** | Tap `Zoom +` or `Zoom -` on Center Maps screen |
| **Recenter Vehicle Location** | Tap `Recenter` on Center Maps screen |
| **Media Play / Pause** | Steering wheel media button toggle |
| **Skip Video 10s** | Steering wheel right scroll wheel up (+10s) / down (-10s) |
| **Test Windshield HUD Display** | Settings → Tap `Test Pattern <ID>` (flashes 6s test card on HUD) |
