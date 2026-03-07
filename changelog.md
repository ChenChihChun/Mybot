# Changelog

## v3.36 (2026-03-08)
- **Enhancement: Music Manager — Add AppLog Logging**
  - Modified `MusicActivity.java` — Added AppLog calls for: sync start/complete, token failure, channel selection, channel resolve (success/fail), playlist loading errors, video playback, song deletion
  - Modified `MusicShareReceiver.java` — Added AppLog calls for: share received (videoId + title), unrecognized share content, song saved (API + fallback)
  - Modified `app/build.gradle` — versionCode 58, versionName 3.36

## v3.35 (2026-03-08)
- **Enhancement: Music Manager — Font Size Setting**
  - Modified `MusicActivity.java` — Added ⚙ settings button in top bar; font size setting dialog (小/中/大) stored in SharedPreferences; song card title/channel/badge text sizes adjust dynamically (small: 14/11/10, medium: 17/13/12, large: 20/15/14); default changed from small to medium
  - Modified `app/build.gradle` — versionCode 57, versionName 3.35

## v3.34 (2026-03-08)
- **Fix: Music Manager — Support Legacy YouTube Username Lookup**
  - Modified `YouTubeClient.java` — `resolveChannel()` now tries `forHandle` first, then falls back to `forUsername` (legacy YouTube username from pre-Google-merger era); extracted `parseChannelResponse()` helper to reduce duplication
  - Modified `app/build.gradle` — versionCode 56, versionName 3.34

## v3.33 (2026-03-08)
- **Enhancement: Music Manager — Support @handle Input for Channel Resolution**
  - Modified `YouTubeClient.java` — Added `resolveChannel()` method that auto-detects input format: `@handle` uses `forHandle` param, `UC...` uses `id` param, plain text tries `forHandle`; returns resolved channel ID + title
  - Modified `MusicActivity.java` — Manual channel input now supports `@username` format (e.g. `@a12555a`); updated hint text and input placeholder; confirm handler uses `resolveChannel()` instead of raw `getChannelInfo()`
  - Modified `app/build.gradle` — versionCode 55, versionName 3.33

## v3.32 (2026-03-08)
- **Enhancement: Music Manager — Manual Channel ID Input for Legacy YouTube Accounts**
  - Modified `YouTubeClient.java` — Added `getChannelInfo(token, channelId, callback)` to fetch channel title by ID
  - Modified `MusicActivity.java` — Channel selection dialog now includes "手動輸入頻道 ID" option at bottom; added `showManualChannelIdDialog()` with instructions and UC... input; channel picker and first-time sync always show selection dialog (even when API returns empty list); auto-fetches channel title after manual ID input
  - Modified `app/build.gradle` — versionCode 54, versionName 3.32

## v3.31 (2026-03-08)
- **Enhancement: Music Manager — YouTube Brand Account / Channel Switcher**
  - Modified `YouTubeClient.java` — Added `ChannelInfo` class, `listMyChannels()` method (channels?mine=true), renamed playlist listing to `listPlaylists(token, channelId, callback)` using `channelId` param instead of `mine=true`
  - Modified `MusicActivity.java` — Sync flow now lists all YouTube channels (including brand accounts) and lets user pick which one; saves selected channel in SharedPreferences; shows channel indicator bar with "切換頻道" button; subsequent syncs use saved channel directly
  - Modified `app/build.gradle` — versionCode 53, versionName 3.31

## v3.30 (2026-03-08)
- **New Feature: Music Manager (音樂管理)**
  - Added `YouTubeClient.java` — YouTube Data API v3 HTTP client with playlist/video CRUD, search, pagination support
  - Added `MusicDbHelper.java` — SQLite DB (mybot_music.db) for songs and categories with favorites, categorization
  - Added `MusicActivity.java` — Main music manager UI with filter chips (all/favorites/categories), song cards with thumbnails, sync from YouTube playlists, search, paste URL, sequential playback, category management
  - Added `MusicShareReceiver.java` — Transparent activity handling YouTube share intents (ACTION_SEND text/plain), extracts video ID, saves to local DB with category selection
  - Modified `GoogleAuthHelper.java` — Added YOUTUBE_SCOPE (youtube.force-ssl), added to all GSO builders (sign-in, silent refresh, sign-out)
  - Modified `MainActivity.java` — Added 🎵音樂管理 card in new Row 5 under FEATURES
  - Modified `AndroidManifest.xml` — Registered MusicActivity (exported=false) and MusicShareReceiver (exported=true with SEND intent-filter)
  - Modified `app/build.gradle` — versionCode 52, versionName 3.30

## v3.29 (2026-03-08)
- **New Feature: Invoice Scanner (發票掃描記帳)**
  - Added `InvoiceActivity.java` — Camera/gallery image capture, AI analysis via Bridge vision, editable result fields, save to expense DB
  - Modified `BridgeClient.java` — Added analyzeInvoice() with InvoiceCallback and invoice-specific prompt
  - Modified `bridge.py` — Added analyze_invoice task routing to vision with 60s timeout
  - Modified `MainActivity.java` — Added 🧾發票掃描記帳 card in TOOLS section new row
  - Modified `AndroidManifest.xml` — Registered InvoiceActivity, added CAMERA permission + camera feature (required=false)
  - Modified `file_paths.xml` — Added cache-path for camera photo FileProvider
  - Modified `app/build.gradle` — versionCode 51, versionName 3.29

## v3.28 (2026-03-08)
- **New Feature: Water Reminder (喝水提醒)**
  - Added `WaterDbHelper.java` — SQLite DB for water logs, SharedPreferences for settings (goal, interval, hours)
  - Added `WaterActivity.java` — Progress card with motivational text, quick-add buttons, 7-day bar chart, today's log list, settings dialog
  - Added `WaterReminderReceiver.java` — Periodic notification with active hours check and goal-reached skip
  - Modified `ReminderHelper.java` — Added scheduleWaterReminder, cancelWaterReminder, restoreWaterIfEnabled
  - Modified `BootReceiver.java` — Added restoreWaterIfEnabled in restoreAlarms()
  - Modified `MainActivity.java` — Added 💧喝水提醒 in Row 4 right cell, added water reminder restore in onCreate
  - Modified `AndroidManifest.xml` — Registered WaterActivity, WaterReminderReceiver
  - Modified `app/build.gradle` — versionCode 50, versionName 3.28

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
