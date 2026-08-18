# \U0001f4f7 Pocket Dashcam \u2014 MoboSafe Campus Challenge 2026

**Roll Number:** BTECH2511123
**Event:** Movozen Private Limited \u2014 Campus Hiring 2026
**Date:** 18 August 2026 | 8:00 PM \u2013 10:00 PM
**Device tested on:** Samsung Galaxy S21 FE (Snapdragon 888)

---

## What it does

Turns an Android phone into a connected dashcam \u2014 both cameras and the microphone stream live over RTMP to the Movozen server, where every second is watchable in real time and archived under the roll number.

---

## Live endpoints

| Camera | Physical | RTMP Endpoint |
|---|---|---|
| Road view | Back camera | `rtmp://15.207.177.194:1936/hackathon/BTECH2511123_back` |
| Cabin view | Front (selfie) camera | `rtmp://15.207.177.194:1936/hackathon/BTECH2511123_front` |

**Viewer:** http://15.207.177.194:8081/web/player.html \u2192 type `BTECH2511123` \u2192 Watch

---

## Tier completion

| Tier | Requirement | Status |
|---|---|---|
| **Tier 1** | One camera + mic, live RTMP, clean start/stop | \u2705 Complete |
| **Tier 2** | Both cameras streaming simultaneously | \u2705 Complete (true concurrent on S21 FE) |
| **Tier 3** | 10-min run, auto-reconnect, screen-lock survival | \u2705 Complete |

---

## Architecture

```
MainActivity
\u251c\u2500\u2500 RtmpCamera2 (primary)       \u2014 OpenGlView preview, BACK physical cam \u2192 _back endpoint
\u2502   \u2514\u2500\u2500 ConnectChecker          \u2014 status text + auto-reconnect via getStreamClient().reTry()
\u251c\u2500\u2500 RtmpCamera2 (secondary)     \u2014 headless Context, FRONT physical cam \u2192 _front endpoint
\u2502   \u2514\u2500\u2500 secondaryChecker        \u2014 anonymous ConnectChecker, auto-reconnect
\u2514\u2500\u2500 Handler timerRunnable       \u2014 1-second elapsed-time ticker in status bar

StreamService (foreground)
\u251c\u2500\u2500 NotificationChannel         \u2014 "Pocket Dashcam \u2014 Streaming live"
\u251c\u2500\u2500 foregroundServiceType       \u2014 camera | microphone
\u2514\u2500\u2500 startForeground()           \u2014 keeps process alive through screen lock & backgrounding
```

### Key library
RootEncoder 2.7.3 by pedroSG94 (https://github.com/pedroSG94/RootEncoder)
- `com.pedro.library.rtmp.RtmpCamera2` \u2014 Camera2 API + RTMP encoder
- `com.pedro.library.view.OpenGlView` \u2014 GL-backed camera preview
- `com.pedro.common.ConnectChecker` \u2014 stream status callbacks

---

## Encoder settings

| Parameter | Value | Spec requirement |
|---|---|---|
| Resolution | 1280 x 720 | 1280 x 720 \u2705 |
| Frame rate | 25 fps | 25 fps \u2705 |
| Video bitrate | 1.5 Mbps | 1\u20132 Mbps \u2705 |
| Keyframe interval | 2 seconds | 2 seconds \u2705 |
| Audio bitrate | 128 kbps AAC | AAC \u2705 |
| Audio sample rate | 44.1 kHz stereo | \u2014 |
| Orientation | Landscape | Landscape \u2705 |

---

## How to build and run

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11+
- Android SDK 36 (installed via SDK Manager)
- USB debugging enabled on your phone

### Steps

1. Open the project in Android Studio: File -> Open -> select dashcam-app folder
2. Let Gradle sync (first time downloads ~150 MB)
3. Connect phone via USB with USB debugging enabled
4. Hit Run (play button) -- app installs automatically

### First launch
1. Tap Allow for Camera and Microphone when prompted
2. The status bar shows: NOT STREAMING

---

## How to use the app

### Tier 1 -- Single stream

| Action | Button |
|---|---|
| Start streaming (back camera, road view) | **Start Stream** |
| Stop streaming | **Stop Stream** |

Status bar shows: LIVE BACK -- 03:42 -- 1487 kbps

### Tier 2 -- Dual stream

| Action | Button |
|---|---|
| Start both cameras simultaneously | **Dual Stream** |
| Stop secondary, keep primary | **Stop Dual** |
| Stop everything | **Stop All** |

The app checks CameraManager.concurrentCameraIds at runtime:
- If concurrent cameras supported (S21 FE YES) -> opens two independent encoder pipelines
- If not supported -> fast-switches between cameras (still earns partial credit)

### Tier 3 -- Screen-lock survival

Just lock your screen while streaming -- feeds stay live automatically.
The foreground notification ("Pocket Dashcam -- Streaming live") keeps the process alive.
Auto-reconnect triggers within 5 seconds if the network drops.

---

## Project file structure

```
dashcam-app/
\u251c\u2500\u2500 app/
\u2502   \u251c\u2500\u2500 src/main/
\u2502   \u2502   \u251c\u2500\u2500 java/com/mobosafe/dashcam/
\u2502   \u2502   \u2502   \u251c\u2500\u2500 MainActivity.kt       <- All streaming logic (Tier 1, 2, 3)
\u2502   \u2502   \u2502   \u2514\u2500\u2500 StreamService.kt      <- Foreground service (screen-lock survival)
\u2502   \u2502   \u251c\u2500\u2500 res/
\u2502   \u2502   \u2502   \u251c\u2500\u2500 layout/activity_main.xml   <- UI: OpenGlView + two buttons
\u2502   \u2502   \u2502   \u251c\u2500\u2500 values/strings.xml         <- Roll number + RTMP host config
\u2502   \u2502   \u2502   \u2514\u2500\u2500 mipmap-*/ic_launcher.png   <- App icon (all densities)
\u2502   \u2502   \u2514\u2500\u2500 AndroidManifest.xml
\u2502   \u2514\u2500\u2500 build.gradle                  <- compileSdk 36, targetSdk 36
\u251c\u2500\u2500 build.gradle                      <- AGP 8.7.3, Kotlin 2.2.0
\u2514\u2500\u2500 README.md                         <- this file
```

---

## Build configuration

```
// build.gradle (root)
classpath 'com.android.tools.build:gradle:8.7.3'
classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0'

// app/build.gradle
compileSdk 36
targetSdk  36
minSdk     23        // Android 6.0+
jvmTarget  '11'
```

Why these versions?
RootEncoder 2.7.3 was compiled with Kotlin 2.3.0 (metadata version 2.3.0).
- AGP must be >= 8.7 to support compileSdk 36
- Kotlin must be >= 2.2 to read Kotlin 2.3 metadata without crashing

---

## Permissions declared

- CAMERA
- RECORD_AUDIO
- INTERNET
- ACCESS_NETWORK_STATE
- WAKE_LOCK
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_CAMERA
- FOREGROUND_SERVICE_MICROPHONE

---

## Key technical decisions

### Why RootEncoder?
Open-source, actively maintained, wraps Camera2 API + MediaCodec natively.
Outputs H.264/AAC over RTMP -- exactly what the spec requires.
Version 2.7.3 is the latest stable.

### Why two separate RtmpCamera2 instances for Tier 2?
Android's Camera2 API supports concurrent camera sessions on capable hardware
(CameraManager.getConcurrentCameraIds()). The secondary instance uses the Context
constructor (headless -- no preview surface needed) so it can encode from the front
camera independently while the primary renders to the OpenGlView.

### Why not stop stream in surfaceDestroyed?
When the screen locks, Android destroys the OpenGlView surface but the underlying
Camera2 session and MediaCodec encoder keep running. Calling stopStream() in
surfaceDestroyed would kill the feed on every screen lock. The encoder continues
without a display surface; the foreground service keeps the process alive.

### Why foreground service?
Android aggressively kills background processes that hold camera and mic resources.
A foreground service with foregroundServiceType="camera|microphone" creates a
persistent notification and exempts the process from background restrictions --
satisfying Tier 3's screen-lock requirement.

---

## Viewer quick-reference

| URL | Purpose |
|---|---|
| http://15.207.177.194:8081/web/player.html | Live viewer (type roll number) |
| http://15.207.177.194:8081/hackathon/BTECH2511123_back.flv | VLC direct (back cam) |
| http://15.207.177.194:8081/hackathon/BTECH2511123_front.flv | VLC direct (front cam) |

The viewer starts muted by default (browser rule). Press Unmute to hear audio.

---

Built for MoboSafe Pocket Dashcam Challenge - Movozen Private Limited - Jaipur
