# Changelog

## v3.26 (2026-03-08)
- **New Feature: Countdown (倒數日)**
  - Added `CountdownDbHelper.java` — SQLite DB for countdown events with inner `Countdown` class
  - Added `CountdownActivity.java` — List view with color-coded day badges (red/orange/green/blue)
  - Added `AddCountdownActivity.java` — Form with date picker, emoji icon picker, color picker, notify toggle
  - Modified `MainActivity.java` — Added ⏳倒數日 card in Row 3 next to 台股追蹤
  - Modified `AndroidManifest.xml` — Registered CountdownActivity, AddCountdownActivity
  - Modified `app/build.gradle` — versionCode 48, versionName 3.26
