# ZOOM AI STUDIO PRO — Android project (0.2.0)

This is a **source-code starter**, not a finished commercial app and **not an APK**.
The code has **not been compiled/tested in this environment** (Android SDK/Gradle unavailable).
The project is supplied for import into Android Studio, further implementation and hardware testing.

## What is actually implemented

- Android app in Kotlin with a bilingual Persian (RTL) / English UI and four tabs; language switch in Settings.
- CameraX camera preview; capture JPG images into `Pictures/ZoomAIStudio` using MediaStore.
- HD video recording with optional audio into `Movies/ZoomAIStudio` (microphone permission requested only for recording).
- Front/back camera switch, pinch + slider zoom using the device-reported zoom range, touch-to-focus with a focus ring.
- Flash mode cycling, torch, exposure compensation, 3s/10s self-timer, sensor horizon level line, thirds grid overlay.
- **Live on-device pose detection (ML Kit)**: the user's real skeleton is drawn next to the selected guide pose.
- **73 original vector pose sketches** in 12 categories (single, men, women, kids, family, couples, friends, celebration, street, nature, studio, product), with category filtering.
- Editor: pick a local image, preview **24 ColorMatrix presets** with intensity plus brightness/contrast/saturation/warmth sliders, rotate/flip, save an edited **copy** to the gallery.
- Settings: language, grid default, and a developer section with the author's links and other apps.
- Photo and video storage stays on device. Pose detection runs on-device. The code makes no outbound network requests.

## What is NOT implemented yet

- Cloud AI processing / paid API / user account / server.
- Numeric pose-match scoring for live guidance (the live skeleton is drawn beside the guide; scoring is a later step).
- Photo-reference pose library (current poses are vector silhouettes, not licensed photographs).
- Hardware-dependent HDR / night / 4K / RAW / advanced manual ISO and shutter.
- Applying filters to camera preview and captured JPG automatically.
- Camera hardware compatibility validation, app signing, Play release or APK.
- Automated tests / crash monitoring / accessibility localization.

## Open in Android Studio

1. Install a current Android Studio and Android SDK Platform 35; use JDK 17 for Gradle.
2. Open this extracted folder via File → Open.
3. Allow Gradle synchronization and downloads (requires internet).
4. Connect an Android 10+ phone, grant camera access, and run the app.
5. Validate camera capture, zoom, gallery editing, and pose overlay on the target phone.

If the installed Android Studio prompts for an AGP or SDK adjustment, use its supported migration tools. This source bundle intentionally does **not** claim a tested APK or include a gradle-wrapper binary.

## Privacy by design

- This starter requests camera permission; it does **not** request blanket gallery access.
- User picks gallery images using GetContent; no photo is uploaded.
- For online AI: require explicit per-photo consent, secure API authentication, delete-on-request, transparent retention policies, and avoid logging photo contents.
- AI-generated detail should not be represented as scientifically verified optical detail.

## References

- CameraX: https://developer.android.com/media/camera/camerax
- CameraX releases: https://developer.android.com/jetpack/androidx/releases/camera
- ML Kit pose: https://developers.google.com/ml-kit/vision/pose-detection/android
- Android Photo Picker: https://developer.android.com/training/data-storage/shared/photo-picker

Read `docs/PRODUCT_SPEC_FA.md` for the complete product scope and future modules.
