# Zoom AI Studio v0.4.0 — Phase 1 UI spec (from the user's own 10 points + adoption plan)

Build target: versionName 0.4.0, versionCode 5, package com.zoomai.studio.
Keep ALL existing working features (capture to MediaStore, video, gallery pick, editor effect engine in Effects.kt, ML Kit pose overlay, torch, timer, level, zoom). This phase is a UI rebuild + the pose flow + the pose library swap. NO new dependencies.

## Palette (replaces the old mint theme everywhere — no mint anywhere)

- bg: #0B0E13 (near-black blue)
- surface: #151A21, surface-2: #1E2631
- ink: #F2F5F8, muted: #9AA7B4
- accent (active/selected): #4CC3FF (sky, matches the app's icon identity)
- shutter: white ring; record: red dot
- SeekBars: white track/progress with accent thumb — explicitly NOT the old mint.
- Rounded panels 20dp, bottom sheets with rounded top corners.

## Camera screen

- Full-screen preview. Nothing decorative over it.
- Top-left: ONE small translucent Settings gear (goes to Settings tab). Top area otherwise empty.
- One quiet quick-toggle row (translucent dark circles, white line icons, accent when ON): flash, timer, grid, ratio. No second row.
- A "more tools" icon (tune/sliders icon) opens a bottom TOOL SHEET (rounded top, surface bg): toggle tiles for grid, level, torch, live pose skeleton, and a row "کتابخانه ژست‌ها" that jumps to the Poses tab. Tiles: icon + label, accent border when on.
- Mode row above the shutter deck: پرو (Pro) · عکس (Photo) · ویدیو (Video) — active word in white bold + small accent dot, others muted. Pro opens a slim panel with EV slider + zoom slider (styled per palette).
- Zoom chips row above the mode row: built from the device's real min/max zoom (e.g. 0.5/1/2/5/10/max× as the device allows) — small translucent pills, accent when active. Pinch zoom also works (already exists).
- Shutter deck: [gallery thumbnail] [big white ring shutter] [camera switch]. In Video mode the shutter becomes a record button.
- Pose ghost (when a pose is applied): image at low opacity over the preview (existing behavior) + a small chip top-center showing the pose name with an ✕ to remove.
- Level indicator and grid overlay: keep existing implementations.

## Editor screen (Samsung Gallery editor is the benchmark — this is the section the user called a disaster; rebuild it properly)

- Header: back arrow, title ویرایش/Edit, and a "ذخیره" (Save) text button in accent at the end. Save enabled only when there is a photo.
- NO-PHOTO state: the center shows only a calm empty state — a large soft image icon + one button «انتخاب عکس» (Choose photo). Absolutely no sliders, chips or tools visible in this state.
- WITH PHOTO: photo large and centered on bg. Bottom tool bar: icon + tiny label, horizontally scrollable: برش Crop · چرخش Rotate · قرینه Flip · تنظیم Adjust · افکت‌ها Effects.
- Each tool opens a panel ABOVE the tool bar, with a ✓ apply / ✕ cancel row (Samsung pattern):
  - Crop: ratio chips (آزاد Free, ۱:۱, ۴:۳, ۳:۴, ۱۶:۹, ۹:۱۶) + buttons چرخش ۹۰° and قرینه افقی/عمودی. Crop applies a center-crop to the chosen ratio (keep it simple and correct).
  - Adjust: ONLY here do sliders appear: روشنایی Brightness, کنتراست Contrast, اشباع Saturation, گرمی Warmth — reuse the existing adjustment pipeline in Effects.kt.
  - Effects: a horizontal strip of LIVE THUMBNAILS — each preset rendered on a small copy of the actual photo (not text chips) — tapping selects; an intensity slider under the strip (reuse the existing preset engine + intensity support if present, else apply preset at full and blend with original by intensity).
- Save writes a NEW file to the gallery (never overwrites the original) — existing behavior, keep.

## Poses tab (new flow from the user + Stance-style)

- Library = the 12 v2 candidates: copy poses_v2_candidates/cand_*.jpg into assets/poses/ as pose_01..12.jpg in manifest order, and build the new manifest.json from manifest_v2.json PLUS a short tip per pose (tip_fa, tip_en — 1 short line each, written from what the photo shows; keep them simple and true).
- Categories/chips from the v2 manifest: همه All · زنانه Women · مردانه Men · دو نفره Couple · فشن Fashion. (Family/kids chips return in Phase 4 with the full library.)
- Grid: 2 columns, photo cards with rounded corners + title under each.
- Tap a pose → POSE DETAIL page (full-screen): the photo large, title, the tip line, and ONE big accent button at the bottom: «استفاده در عکس» / "Use in photo". Only tapping it sets the ghost overlay and switches to the Camera tab. Back returns to the grid WITHOUT applying.

## Settings + About (keep the calm list; restyle to palette)

- Rows: زبان Language (FA | EN segmented), خطوط راهنما Grid (switch), صدای شاتر Shutter sound (switch if the feature exists, else skip), نسخه Version row, درباره ما About row → About page.
- About page: ALL developer info lives here only, as quiet list rows (name, role, email, WhatsApp, Telegram, Instagram, LinkedIn, GitHub) — same data as v0.3.0's About, restyled.

## Navigation

- Bottom bar, 4 tabs with icons + labels: دوربین Camera · ژست‌ها Poses · ویرایش Editor · تنظیمات Settings. Active = accent icon+label, inactive = muted. Pose detail is a page inside the Poses tab (back returns to grid).

## Code structure (commercial/extensible requirement)

- Keep the existing files (MainActivity.kt, Effects.kt, I18n.kt, Poses.kt, PoseOverlayView.kt) but organize MainActivity into clearly commented sections (Camera / Editor / Poses / Settings). Do not break I18n: all user-visible strings go through the existing FA/EN string system; add new keys for both languages.
- Add all new drawables needed (crop, rotate, flip, adjust, check, close, tune, image) as simple white line icons in res/drawable, same style as existing ones.
- Update CHANGELOG.md with a v0.4.0 entry summarizing the UI rebuild.

## Hard rules

- No mint color anywhere. No fake buttons: everything shown must work.
- Do not touch signing, workflows, or gradle config except versionName/versionCode in app/build.gradle.kts.
- The build must pass: gradle assembleDebug with the existing project setup (compileSdk 36, arm64-v8a).
