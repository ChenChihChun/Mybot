# Changelog

## v3.50 (2026-03-10)
- **Fix: Stock Chart Not Updating with Real-time Price**
  - Modified `StockActivity.java` — Chart now updates on every quote fetch (removed `isHistoricalPeriod()` guard that blocked chart refresh for day/week/month views); last candle's close/high/low now updates with real-time price so K-line chart follows current stock price
  - Modified `app/build.gradle` — versionCode 72, versionName 3.50

## v3.49 (2026-03-10)
- **Fix: Revert Stock API Changes + Fix Screenshot Analysis**
  - Reverted `StockClient.java` — Restored to original TWSE API (v3.45 state); Yahoo Finance rewrite caused English stock names and broken chart updates
  - Reverted `StockActivity.java` — Restored to v3.45 state (cost/shares + comma formatting retained, removed broken SwipeRefreshLayout)
  - Removed `swiperefreshlayout` dependency from `app/build.gradle`
  - Modified `bridge.py` — Added `cli_path=CLAUDE_PATH` to `ClaudeAgentOptions` so Agent SDK uses `claude-fix` wrapper instead of broken system `claude` (shebang `/usr/bin/env` not available in Termux)
  - Modified `bridge/start.sh` — Added `export CLAUDE_CODE_TMPDIR` and auto-patch cli.js `/tmp` paths for Termux compatibility
  - Modified `app/build.gradle` — versionCode 71, versionName 3.49

## v3.48 (2026-03-10)
- **Fix: Stock Tracker — Switch to Yahoo Finance API**
  - Rewritten `StockClient.java` — Replaced TWSE mis API (unreliable, requires session cookies) with Yahoo Finance v8 chart API; auto-detects `.TW` (上市) vs `.TWO` (上櫃) suffix per stock with cache; realtime quotes via `interval=1m&range=1d`; historical candles via `interval=1d&range=6mo/1y` (single request instead of monthly loop); removed TWSE/TPEx-specific code, rate limiting, and session management
  - Modified `app/build.gradle` — versionCode 70, versionName 3.48

## v3.47 (2026-03-10)
- **Enhancement: Stock Tracker — Pull-to-Refresh + Update Time + API Fix**
  - Modified `StockActivity.java` — Added SwipeRefreshLayout for pull-to-refresh (orange/blue spinner); shows last update time in status bar (HH:mm:ss format); tracks `lastUpdateTime`; dismisses refresh indicator on callback
  - Modified `StockClient.java` — Fixed API headers: added realistic User-Agent, Referer, Accept headers; added `_=timestamp` cache-busting param; disabled HttpURLConnection cache; added error logging for HTTP errors and exceptions
  - Modified `app/build.gradle` — Added `swiperefreshlayout:1.1.0` dependency; versionCode 69, versionName 3.47

## v3.46 (2026-03-10)
- **Fix: Stock Tracker — Support OTC (上櫃) Stocks**
  - Modified `StockClient.java` — Realtime quotes now query both `tse_` and `otc_` prefixes for each code, with deduplication; tracks market type via `ex` field in API response; `fetchMultiMonthHistory()` uses TPEx API (`tpex.org.tw`) for OTC stocks with correct ROC date format and `aaData` field; added `OTC_HISTORY_URL` and `otcMap` for market type tracking
  - Modified `app/build.gradle` — versionCode 68, versionName 3.46

## v3.45 (2026-03-10)
- **Enhancement: Stock Tracker — Comma-Formatted Cost Display**
  - Modified `StockActivity.java` — Added `formatComma()` helper with `DecimalFormat("#,###")` for thousands separator; cost display now shows formatted shares (e.g. 1,000股) and P&L (e.g. +15,000); imported `java.text.DecimalFormat`
  - Modified `bridge.py` — Increased `call_claude_web()` max-turns from 3 to 8 (fix AI analysis error_max_turns)
  - Modified `app/build.gradle` — versionCode 67, versionName 3.45

## v3.44 (2026-03-10)
- **Fix: Stock Tracker — Cost/Shares Button Always Visible**
  - Modified `StockActivity.java` — Cost row now always visible with "點擊設定成本與股數" hint and 📝設定 button (orange outline); entire row clickable to open cost dialog; no longer hidden when no data set
  - Modified `app/build.gradle` — versionCode 66, versionName 3.44

## v3.43 (2026-03-10)
- **Enhancement: Stock Tracker — Cost Basis Input + AI Web Search**
  - Modified `StockActivity.java` — Added cost/shares input dialog (long-press stock → 設定成本); cost/P&L display row in info card with real-time unrealized gain/loss; cost data included in AI analysis prompt; long-press menu now shows "設定成本" and "刪除" options; delete also cleans up cost/shares prefs
  - Modified `BridgeClient.java` — AI prompt now includes cost position analysis (point 4: 加碼/減碼/持有 strategy); web search instructions for latest news/trends
  - Modified `bridge.py` — Added `call_claude_web()` function using `--tools WebSearch,WebFetch` for stock analysis with real-time web data; routed `analyze_stock` task through web-enabled Claude; increased timeout to 120s
  - Modified `bridge/start.sh` — Added `unset CLAUDECODE` to prevent nested session detection error
  - Modified `app/build.gradle` — versionCode 65, versionName 3.43

## v3.42 (2026-03-08)
- **Fix: App Crash on Launch — SCHEDULE_EXACT_ALARM Permission**
  - Modified `AndroidManifest.xml` — Added `SCHEDULE_EXACT_ALARM` permission (required on Android 12+ for exact alarms)
  - Modified `ReminderHelper.java` — Added `safeSetExact()` wrapper: checks `canScheduleExactAlarms()` on API 31+, falls back to `setAndAllowWhileIdle()` on SecurityException; all 4 alarm schedulers now use this safe wrapper
  - Modified `app/build.gradle` — versionCode 64, versionName 3.42

## v3.41 (2026-03-08)
- **Fix: All Reminders — Use Exact Alarms for Doze Reliability**
  - Modified `ReminderHelper.java` — Replaced all `setRepeating()` with `setExactAndAllowWhileIdle()` for daily expense reminder, fitness reminder, and TODO check; added `scheduleNextDailyReminder()` and `scheduleNextFitnessReminder()` for receiver-driven rescheduling
  - Modified `ReminderReceiver.java` — Reschedules next daily expense reminder on receive
  - Modified `FitnessReminderReceiver.java` — Reschedules next fitness reminder on receive
  - Modified `TodoReminderReceiver.java` — Reschedules next TODO check on receive
  - Modified `app/build.gradle` — versionCode 63, versionName 3.41

## v3.40 (2026-03-08)
- **Fix: Water Reminder Not Firing + Settings Dialog Visibility**
  - Modified `ReminderHelper.java` — Replaced `setRepeating()` with `setExactAndAllowWhileIdle()` for water reminders (Doze-safe); added `scheduleNextWaterAlarm()` for one-shot exact scheduling
  - Modified `WaterReminderReceiver.java` — Reschedules next alarm at the start of `onReceive()` (since exact alarms are one-shot)
  - Modified `WaterActivity.java` — Settings dialog: switched to AppCompat dark theme, used `UIHelper.styledInput()` for all inputs, added editable start/end hour fields; custom amount dialog also uses dark theme
  - Modified `app/build.gradle` — versionCode 62, versionName 3.40

## v3.39 (2026-03-08)
- **Enhancement: Screenshot Expense — Tap Notification to Edit**
  - Modified `NotificationHelper.java` — Added `sendExpenseNotification(context, merchant, amount, category, expenseId)` overload; when expenseId > 0, notification includes PendingIntent opening AddExpenseActivity in edit mode
  - Modified `FloatingCaptureService.java` — Captures inserted expense ID from `db.insert()` and passes to notification
  - Modified `app/build.gradle` — versionCode 61, versionName 3.39

## v3.38 (2026-03-08)
- **New Feature: Remote Development Control (遠端開發)**
  - Added `RemoteDevActivity.java` — Remote dev UI with multi-line task input, project path picker (stored in SharedPreferences), run/reset buttons, monospace result display; sends tasks via Bridge to Slack Bot on work PC running Claude Code CLI
  - Modified `BridgeClient.java` — Added `remoteCode(task, project, callback)` with `RemoteCodeCallback` interface; 620s read timeout for long-running dev tasks; AppLog logging (tag: RemoteDev)
  - Modified `bridge.py` — Added `POST /remote-code` endpoint and `send_slack_and_wait()` function; posts task to Slack Bot DM channel via Slack API, polls `conversations.history` for bot reply (5-10s interval, 600s timeout)
  - Modified `bridge/config.json` — Added `slack_bot_token` and `slack_channel_id` fields
  - Modified `MainActivity.java` — Added 💻遠端開發 card in TOOLS section row 5 (next to 發票掃描記帳)
  - Modified `AndroidManifest.xml` — Registered RemoteDevActivity
  - Added `~/remote-dev-setup.txt` — Complete setup guide for work PC (Slack App creation, bot.py, config.json, auto-start)
  - Modified `app/build.gradle` — versionCode 60, versionName 3.38

## v3.37 (2026-03-08)
- **Enhancement: Add AppLog Logging to All Modules**
  - Modified `ExpenseActivity.java` — AppLog for page open, edit, delete (tag: Expense)
  - Modified `AddExpenseActivity.java` — AppLog for add/update expense, amount error, AI categorization (tag: Expense)
  - Modified `ReportActivity.java` — AppLog for report generation with stats (tag: Expense)
  - Modified `TodoActivity.java` — AppLog for complete/uncomplete, delete (tag: Todo)
  - Modified `AddTodoActivity.java` — AppLog for add/update todo, validation errors (tag: Todo)
  - Modified `CalendarActivity.java` — AppLog for sign-in, load calendars/events, sign-out, token failures (tag: Calendar)
  - Modified `AddCalendarEventActivity.java` — AppLog for AI parse, create event success/fail (tag: Calendar)
  - Modified `FitnessActivity.java` — AppLog for AI plan generation, workout complete, errors (tag: Fitness)
  - Modified `FitnessProfileActivity.java` — AppLog for profile save, input errors (tag: Fitness)
  - Modified `WorkoutDetailActivity.java` — AppLog for load details, complete workout, errors (tag: Fitness)
  - Modified `StockActivity.java` — AppLog for stock add/remove, AI analysis (tag: Stock)
  - Modified `CountdownActivity.java` — AppLog for delete countdown (tag: Countdown)
  - Modified `AddCountdownActivity.java` — AppLog for add/edit countdown (tag: Countdown)
  - Modified `HabitActivity.java` — AppLog for check-in toggle, delete habit (tag: Habit)
  - Modified `AddHabitActivity.java` — AppLog for add/edit habit (tag: Habit)
  - Modified `WaterActivity.java` — AppLog for water intake, settings changed (tag: Water)
  - Modified `GoogleAuthHelper.java` — AppLog for sign-in/out, token exchange (tag: Auth)
  - Modified `UpdateChecker.java` — AppLog for update check, download (tag: Update)
  - Modified `FloatingCaptureService.java` — AppLog for service start/stop, screenshot (tag: Capture)
  - Modified `BootReceiver.java` — AppLog for boot/package replace (tag: System)
  - Modified `app/build.gradle` — versionCode 59, versionName 3.37

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
