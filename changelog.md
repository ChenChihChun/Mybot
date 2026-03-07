# Changelog

## v3.27 (2026-03-08)
- **New Feature: Habit Tracker (習慣追蹤)**
  - Added `HabitDbHelper.java` — SQLite DB with habits + habit_logs tables, streak/completion tracking
  - Added `HabitActivity.java` — Today's progress card with progress bar, habit list with check buttons, long-press stats dialog with calendar grid
  - Added `AddHabitActivity.java` — Form with name, emoji icon picker, color picker
  - Modified `MainActivity.java` — Added 📊習慣追蹤 card in new Row 4
  - Modified `AndroidManifest.xml` — Registered HabitActivity, AddHabitActivity
  - Modified `app/build.gradle` — versionCode 49, versionName 3.27

## v3.26 (2026-03-08)
- **New Feature: Countdown (倒數日)**
  - Added `CountdownDbHelper.java` — SQLite DB for countdown events with inner `Countdown` class
  - Added `CountdownActivity.java` — List view with color-coded day badges (red/orange/green/blue)
  - Added `AddCountdownActivity.java` — Form with date picker, emoji icon picker, color picker, notify toggle
  - Modified `MainActivity.java` — Added ⏳倒數日 card in Row 3 next to 台股追蹤
  - Modified `AndroidManifest.xml` — Registered CountdownActivity, AddCountdownActivity
  - Modified `app/build.gradle` — versionCode 48, versionName 3.26
