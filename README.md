# Pocket Dashcam - MoboSafe Campus Challenge 2026

**Roll Number:** BTECH2511123
**Company:** Movozen Private Limited
**Date:** 18 August 2026, 8:00 PM to 10:00 PM
**Device:** Samsung Galaxy S21 FE (Snapdragon 888, Android 13)

---

## What This App Does

This app turns a regular Android phone into a live connected dashcam. Both cameras and the
microphone stream simultaneously over RTMP to the Movozen server. The server records everything
under the roll number and shows it live in the browser viewer.

---

## Live Streaming Endpoints

| View        | Physical Camera | RTMP URL                                                                  |
|-------------|-----------------|---------------------------------------------------------------------------|
| Road view   | Back camera     | rtmp://15.207.177.194:1936/hackathon/BTECH2511123_back                    |
| Cabin view  | Front camera    | rtmp://15.207.177.194:1936/hackathon/BTECH2511123_front                   |

**Browser Viewer:** http://15.207.177.194:8081/web/player.html
Open it, type BTECH2511123, hit Watch. Both feeds appear side by side within a few seconds.

**VLC (alternative):**
- Back cam:  http://15.207.177.194:8081/hackathon/BTECH2511123_back.flv
- Front cam: http://15.207.177.194:8081/hackathon/BTECH2511123_front.flv

> The browser viewer starts muted. Press Unmute to hear audio.

---

## Challenge Tier Summary

| Tier   | Requirement                                               | Result                              |
|--------|-----------------------------------------------------------|-------------------------------------|
| Tier 1 | One camera + mic streaming live with clean start and stop | Done                                |
| Tier 2 | Both cameras streaming simultaneously as two live feeds   | Done - true concurrent on S21 FE    |
| Tier 3 | 10-min run, auto-reconnect, screen-lock survival          | Done - foreground service + retry   |

---

## How to Build and Run

### Requirements

- Android Studio (Hedgehog or newer)
- JDK 11 or newer
- Android SDK 36 (install via SDK Manager in Android Studio)
- A physical Android phone with USB debugging enabled (minSdk is 23, so Android 6.0+)

### Steps

1. Open Android Studio
2. File > Open > select the `dashcam-app` folder
3. Wait for Gradle sync to finish (first time downloads about 150 MB)
4. Connect your phone via USB and allow USB debugging
5. Click the Run button (green triangle) - the app installs and launches automatically

### First Launch

When the app opens, Android will ask for Camera and Microphone permissions.
Tap Allow for both. The status bar at the top left will show "NOT STREAMING".

---

## How to Use the App

### Starting a single stream (Tier 1)

Tap **Start Stream** - the back (road-facing) camera starts streaming to the `_back` endpoint.
The status bar updates every second showing camera, elapsed time, and live bitrate.
Example: LIVE BACK - 04:12 - 1487 kbps

Tap **Stop Stream** to end it.

### Starting dual stream (Tier 2)

Tap **Dual Stream** - the app checks if your device supports concurrent Camera2 sessions.

On Samsung S21 FE (and most recent flagship phones), it does. Two independent encoder
pipelines open:
- Back camera (road view) streams to the `_back` endpoint
- Front camera (cabin/selfie view) streams to the `_front` endpoint

Both feeds appear live on the viewer page within a few seconds.

Tap **Stop Dual** to stop only the second camera and continue with the primary.
Tap **Stop All** to stop everything.

### Screen lock survival (Tier 3)

Just lock the phone screen while streaming. Both feeds stay live.

A foreground notification ("Pocket Dashcam - Streaming live - do not close") keeps the
camera and microphone pipeline running even when the screen is off or you switch to
another app. If the network drops, the app auto-reconnects within 5 seconds.

---

## Project Structure

    dashcam-app/
    |
    +-- app/
    |   +-- src/main/
    |   |   +-- java/com/mobosafe/dashcam/
    |   |   |   +-- MainActivity.kt       All streaming logic for Tier 1, 2, and 3
    |   |   |   +-- StreamService.kt      Foreground service for screen-lock survival
    |   |   |
    |   |   +-- res/
    |   |   |   +-- layout/
    |   |   |   |   +-- activity_main.xml  UI layout: camera preview + two buttons
    |   |   |   +-- values/
    |   |   |   |   +-- strings.xml        Roll number and RTMP server address
    |   |   |   +-- mipmap-*/
    |   |   |       +-- ic_launcher.png    App icon for all screen densities
    |   |   |
    |   |   +-- AndroidManifest.xml        Permissions and service declaration
    |   |
    |   +-- build.gradle                   App-level: compileSdk 36, targetSdk 36
    |
    +-- build.gradle                       Project-level: AGP 8.7.3, Kotlin 2.2.0
    +-- settings.gradle
    +-- README.md                          This file

---

## Key Files Explained

### MainActivity.kt

The entire streaming brain of the app. Responsibilities:

- Creates a primary RtmpCamera2 instance bound to the OpenGlView (the preview you see on screen).
  This handles the back camera and streams to the `_back` endpoint.

- Creates a secondary RtmpCamera2 instance using just the app Context (no preview surface needed).
  This is headless - it captures from the front camera and streams to `_front` without showing
  any preview. This is how Tier 2 concurrent dual-streaming works.

- Implements ConnectChecker (the callback interface from RootEncoder) for both streams.
  When a connection drops, reTry(5000, reason, null) schedules an automatic reconnect in 5s.

- Runs a Handler-based 1-second timer that updates the status bar with elapsed time and bitrate.

- Starts StreamService on launch so the camera pipeline survives screen lock.

### StreamService.kt

A foreground service declared with foregroundServiceType camera and microphone.
Calling startForegroundService() from MainActivity triggers it. The service immediately
calls startForeground() with a persistent notification. This tells Android not to kill
the process when the screen turns off or the app goes to the background.

The actual RtmpCamera2 objects stay in MainActivity (they need the Activity context for
surface callbacks), but the foreground service keeps the process alive so the encoding
pipeline is not interrupted.

### strings.xml

Contains the roll number and RTMP host as string resources so they are easy to update
without touching any Kotlin code.

    roll_no  = BTECH2511123
    rtmp_host = rtmp://15.207.177.194:1936/hackathon/

---

## Video and Audio Settings

These match the spec exactly.

| Setting          | Value        |
|------------------|--------------|
| Resolution       | 1280 x 720   |
| Frame rate       | 25 fps       |
| Video bitrate    | 1.5 Mbps     |
| Keyframe interval| 2 seconds    |
| Video codec      | H.264        |
| Audio bitrate    | 128 kbps     |
| Sample rate      | 44100 Hz     |
| Channels         | Stereo       |
| Audio codec      | AAC          |
| Orientation      | Landscape    |

---

## Build Configuration Explained

    build.gradle (project level)
        Android Gradle Plugin : 8.7.3
        Kotlin plugin         : 2.2.0

    app/build.gradle
        compileSdk  : 36
        targetSdk   : 36
        minSdk      : 23
        jvmTarget   : 11

**Why these exact versions?**

RootEncoder 2.7.3 (the streaming library) was compiled with Kotlin 2.3.0. Kotlin metadata
has a versioning system where compiler N.x can read metadata written by compiler up to (N+1).x.

- Kotlin 1.9.x can only read metadata up to 2.0 - RootEncoder fails to resolve
- Kotlin 2.0.x crashes with a null-source internal bug when reading 2.3 metadata
- Kotlin 2.2.x can read up to 2.3 metadata - this works correctly

Android Gradle Plugin 8.2.0 (the original version) caps the maximum recommended compileSdk
at 34. RootEncoder 2.7.3 requires compileSdk 36. AGP 8.7.3 fully supports compileSdk 36.

---

## Android Permissions

    CAMERA                        - capture from both cameras
    RECORD_AUDIO                  - capture microphone
    INTERNET                      - push RTMP stream to server
    ACCESS_NETWORK_STATE          - check connectivity
    WAKE_LOCK                     - prevent CPU sleep during streaming
    FOREGROUND_SERVICE            - run the persistent streaming service
    FOREGROUND_SERVICE_CAMERA     - required on Android 14+ for camera in services
    FOREGROUND_SERVICE_MICROPHONE - required on Android 14+ for mic in services

---

## Technical Decisions and Reasoning

### Why RootEncoder?

It is the most actively maintained open-source Android RTMP library. It wraps Camera2 API
and MediaCodec natively in Kotlin, produces H.264 video and AAC audio, and handles RTMP
handshake, chunking, and reconnect internally. The API is clean and the version 2.7.3
had all the features needed for this challenge.

### How does Tier 2 concurrent dual-camera work?

Android's Camera2 API supports opening two physical cameras at the same time on devices
that advertise this capability via CameraManager.getConcurrentCameraIds(). The S21 FE
supports it.

The app creates two completely independent RtmpCamera2 encoder pipelines:
1. Primary instance - uses OpenGlView constructor, opens the back camera with a visible preview
2. Secondary instance - uses Context constructor (headless), opens the front camera with
   startPreview(CameraHelper.Facing.FRONT) before calling startStream()

Both run simultaneously and push to different RTMP endpoints.

On devices that do not support concurrent cameras, the app falls back to fast-switching:
stopping one stream, flipping the camera, and restarting. This earns partial Tier 2 credit
according to the challenge spec.

### Why is there no stopStream() in surfaceDestroyed?

When the screen locks, Android destroys the window Surface that the OpenGlView is drawn on.
This triggers surfaceDestroyed(). If you call stopStream() here, the feed goes dark every
time the user locks their phone - which is the opposite of what Tier 3 requires.

RootEncoder's camera capture and MediaCodec encoder pipeline keep running even without a
display surface. The camera hardware continues capturing frames, the encoder compresses them,
and the RTMP client pushes them to the server. The OpenGlView preview just goes blank on
the phone screen - the server still receives live video.

When the user unlocks the phone, surfaceCreated() fires again. The guard
`if (rtmpCamera2.isStreaming) return` prevents the encoder from being re-initialized
(which would cause a brief drop) and lets the already-running pipeline reconnect to the
new surface for preview.

### Why a foreground service?

Android aggressively kills background processes that hold camera and microphone resources.
Without a foreground service, the OS terminates the app within seconds of the screen locking
or the user pressing the Home button.

A foreground service with foregroundServiceType="camera|microphone" creates a persistent
notification visible to the user and registers the process with the OS as one that legitimately
needs camera and mic access in the background. Android will not kill it.

### Why OpenGlView instead of SurfaceView?

RootEncoder 2.x requires its own OpenGlView for the camera preview. It uses OpenGL ES for
rendering so it can apply filters and handle surface lifecycle events cleanly. Plain SurfaceView
is not compatible with the 2.x API.

---

## Interview Preparation Notes

The top 10 students get a technical interview. Here is a summary of decisions you should
be ready to explain:

- Why RootEncoder over ExoPlayer or other libraries (ExoPlayer is for playback, not streaming)
- How Camera2 concurrent sessions work (concurrentCameraIds check at runtime)
- Why the secondary encoder is headless (Context constructor, no UI needed for encoding)
- Why surfaceDestroyed does not stop the stream (encoder runs without display)
- Why a foreground service is needed (background process restrictions on Android 8+)
- The Kotlin and AGP version chain and why each upgrade was necessary
- How auto-reconnect works (ConnectChecker.onConnectionFailed -> getStreamClient().reTry)

---

Built for MoboSafe Pocket Dashcam Challenge - Movozen Private Limited - Jaipur - August 2026
