# ZOOM AI STUDIO — Android camera app (v0.4.0)

A native Android camera app in Kotlin with CameraX and on-device ML Kit.
Status: **in active development.** Test APKs are built with GitHub Actions,
signed, and tested on a Samsung Galaxy S21+. Not yet released on Google Play.

## What is implemented (v0.4.0)

- CameraX preview, photo capture and HD video; front/back switch.
- Zoom uses the device-reported range (pinch + on-screen chips) — no artificial cap.
- **Live on-device pose detection (ML Kit)**: the user's skeleton is drawn live.
- **Real-photo pose library**: curated photography poses with a full-photo detail
  view, a posing tip, and a "Use in photo" ghost overlay on the camera.
- Clean Samsung-style camera UI: mode words (Pro / Photo / Video), quick toggles,
  and a tool sheet.
- Photo editor modeled on the Samsung Gallery editor: crop, rotate, flip,
  adjust sliders (brightness / contrast / saturation / warmth) and 24 effect
  presets shown as **live thumbnails of your own photo**. Saving always writes a
  new file — the original is never overwritten.
- Bilingual Persian (RTL) / English UI; calm list-style Settings; all developer
  info lives on a single About page.
- Photo, video and pose detection stay on device; no account, no cloud upload.

## Not yet

- Live effects and beauty on the camera preview (in development).
- Portrait / night modes and the extended mode set (in development).
- Google Play release.

## Build

Android Studio with Android SDK Platform 36 and JDK 17, or push to `main` and
let the GitHub Actions workflow (`.github/workflows/build-apk.yml`) build the APK.

## Privacy by design

Everything runs on the phone. The app makes no outbound network requests for
photos; pose detection is on-device ML Kit.
