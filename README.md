# Pocket Dashcam — MoboSafe Challenge (BTECH2511123)

## What's here
- Tier 1 fully wired: front camera + mic → RTMP, clean start/stop, status text.
- Tier 2 fallback wired: "Switch Cam" button toggles between `_front` and `_back`
  endpoints. True concurrent dual-camera is scaffolded but NOT wired in
  (`StreamService.supportsConcurrentCameras()`) — check that on your actual test
  device first thing, it decides how much Tier 2 work is worth attempting.
- Tier 3 scaffolded (`StreamService.kt`) but not wired into `MainActivity` yet —
  do this last, only if Tier 1+2 are solid and submitted.

## Endpoints (already filled in with your roll number)
```
rtmp://15.207.177.194:1936/hackathon/BTECH2511123_front
rtmp://15.207.177.194:1936/hackathon/BTECH2511123_back
```
Viewer: http://15.207.177.194:8081/web/player.html — type `BTECH2511123`, press Unmute.

## Setup (do this first, ~10-15 min)
1. Open this folder in Android Studio (Hedgehog/2023.1+). Let it generate the
   Gradle wrapper on first sync — I didn't ship `gradle-wrapper.jar` since it's
   a binary; Android Studio's "Sync Now" prompt creates it automatically. If it
   doesn't, run `gradle wrapper` once with any local Gradle install.
2. Connect a real Android phone via USB, enable Developer Options + USB
   debugging. **Emulators don't have real cameras worth streaming** — use a
   physical device.
3. Confirm the RootEncoder version in `app/build.gradle` still resolves — check
   https://github.com/pedroSG94/RootEncoder for the current release tag if
   `2.4.2` 404s on jitpack.
4. Run on device, grant camera + mic permissions, hit **Start Stream**, open
   the viewer page on a laptop on the same network (or any network — it's
   public IP) and confirm video + audio arrive.

## Honest limitations of this scaffold
- I built this without a device or Android SDK to test against — expect at
  least one real bug (API mismatch, permission edge case, codec quirk) once
  you actually run it. That's normal and is literally what section 07 of the
  brief says they're grading for.
- `RtmpCamera2`'s exact method names/signatures can drift between RootEncoder
  versions — if something doesn't compile, check the sample app in the
  RootEncoder repo for the version you pulled and adjust `prepareVideo`/
  `prepareAudio`/`startStream` signatures to match.
- Concurrent dual-camera streaming is genuinely device-dependent (Pixel 6+,
  Samsung S21+ tend to support it; older/budget phones usually don't) — don't
  burn your whole Tier 2 budget on it if `supportsConcurrentCameras()` returns
  false; take the switch-camera partial credit and move to Tier 3 instead.

## Priority reminder (from the brief)
Submission order matters — a working Tier 1 stream announced at 8:40 PM beats
a Tier 2 stream announced at 9:59 PM. **Get Tier 1 live and tell the
organisers immediately**, then keep building on top of a stream that's
already scoring.
