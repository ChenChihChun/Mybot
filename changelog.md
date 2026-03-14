# Changelog

## v3.79 (2026-03-14)
- **Expense: Category sync to Bridge + DB helpers for category management**
  - Modified `bridge.py` — Added `/categories` POST/GET endpoint to receive and store expense category data from app.
  - Modified `ExpenseDbHelper.java` — Added `getCategoryCounts()` (category with count, sorted by frequency) and `updateCategory()` (batch rename category) methods.
  - Modified `ExpenseActivity.java` — Added `syncCategoriesToBridge()` that auto-POSTs all categories with counts to Bridge on every page load.
  - Modified `app/build.gradle` — versionCode 102, versionName 3.79

## v3.78 (2026-03-14)
- **Expense: AI category classification — prefer existing categories over creating new ones**
  - Modified `bridge.py` — Added dedicated `categorize_expense` prompt in `build_prompt()` that receives existing category list and instructs AI to prefer them. Updated `analyze_notification` prompt with same logic.
  - Modified `BridgeClient.java` — `categorize()`, `analyzeScreenshot()`, `analyzeInvoice()` now accept `List<String> existingCategories` parameter and include dynamic category instructions in prompts.
  - Modified `AddExpenseActivity.java` — Passes `dbHelper.getCategories()` to `BridgeClient.categorize()`.
  - Modified `FloatingCaptureService.java` — Passes existing categories to `BridgeClient.analyzeScreenshot()`.
  - Modified `InvoiceActivity.java` — Passes existing categories to `BridgeClient.analyzeInvoice()`.
  - Modified `app/build.gradle` — versionCode 101, versionName 3.78

## v3.77 (2026-03-12)
- **Security: SecurePrefs — Improve encrypted storage fallback resilience**
  - Modified `SecurePrefs.java` — On EncryptedSharedPreferences init failure, now clears corrupted master key and retries before falling back to plaintext. Added `isEncryptionAvailable()` to let callers check encryption status. Fallback is now logged as error instead of silently degrading.
- **Security: GoogleAuthHelper — Token validation and retry mechanism**
  - Modified `GoogleAuthHelper.java` — `getCachedOrFreshToken()` now validates token format (`ya29.` prefix) and expiry > 0 before using cache. Invalid cached tokens are cleared explicitly. Added `getAccessTokenWithRetry()` with exponential backoff (0ms → 2s → 5s, max 2 retries) for token exchange failures.
- **Infra: Project hooks for automated quality checks**
  - Added `.claude/settings.json` — Project-level Claude Code hooks configuration
  - Added `.claude/hooks/protect-files.sh` — Blocks modification of SecurePrefs, .env, keystore files
  - Added `.claude/hooks/block-dangerous-git.sh` — Intercepts dangerous git commands (reset --hard, push --force, etc.)
  - Added `.claude/hooks/bridge-restart-reminder.sh` — Reminds to restart Bridge when bridge.py is modified
  - Added `.claude/hooks/applog-check.sh` — Warns when Java files lack AppLog calls
  - Added `.claude/hooks/security-audit-check.sh` — Triggers OWASP security audit reminder every 10 versionCodes
  - Modified `app/build.gradle` — versionCode 100, versionName 3.77

## v3.76 (2026-03-12)
- **Fix: Bridge — AI Calendar Event Parse prompt missing user text**
  - Modified `bridge.py` — Added dedicated `parse_calendar_event` handler in `build_prompt()` that includes user input text and today's date in the prompt. Previously fell through to generic handler that only sent instruction template without actual content.
- **Enhancement: Calendar — Auto-scroll to today, past days grayscale, per-calendar colors**
  - Modified `CalendarActivity.java` — Auto-scroll to today's date on initial entry from home page; past day headers and event cards rendered in grayscale with 0.6 alpha; each calendar gets a unique color from a 10-color palette displayed as left color bar on event cards and calendar name label; today's date header highlighted with "← 今天" marker
  - Modified `app/build.gradle` — versionCode 98, versionName 3.76

## v3.75 (2026-03-11)
- **Fix: Travel Planner — AI refine loading overlay & timeout increase**
  - Modified `TravelPlanActivity.java` — Added full-screen loading overlay with spinner and live elapsed timer during AI refine (📝分析修改需求 → 🔄重新規劃行程 → 💰計算費用與交通 → ✅即將完成), blocks touch input during processing
  - Modified `BridgeClient.java` — Increased refineItinerary HTTP read timeout from 200s to 400s
  - Modified `~/bridge/bridge.py` — Increased refine_itinerary Claude CLI timeout from 180s to 360s (6 min)
  - Modified `app/build.gradle` — versionCode 96→97, versionName 3.74→3.75

## v3.74 (2026-03-11)
- **Enhancement: Travel Planner — AI refine dialog with prompt suggestions**
  - Modified `TravelPlanActivity.java` — Redesigned AI refine dialog: added scrollable quick-select prompt chips (多加景點/精簡行程/換景點/多安排美食/親子友善/改自駕/控制預算/加入夜市), tapping chips appends to input, multi-line input field for custom instructions
  - Modified `app/build.gradle` — versionCode 95→96, versionName 3.73→3.74

## v3.73 (2026-03-11)
- **Enhancement: Travel Planner — Live progress timer & increased timeout**
  - Modified `CreateTripActivity.java` — Added real-time elapsed timer with stage indicators (🔍搜尋景點 → 🚌查詢交通 → 🍽搜尋美食 → 📝組合行程 → 💰計算預算 → ✅即將完成), updates every second showing elapsed time
  - Modified `BridgeClient.java` — Increased generateItinerary HTTP read timeout from 500s to 630s
  - Modified `~/bridge/bridge.py` — Increased generate_itinerary Claude CLI timeout from 480s to 600s (10 min)
  - Modified `app/build.gradle` — versionCode 94→95, versionName 3.72→3.73

## v3.72 (2026-03-11)
- **Fix: Travel Planner — Increase generate_itinerary timeout to prevent SocketTimeout**
  - Modified `BridgeClient.java` — Increased generateItinerary HTTP read timeout from 310s to 500s
  - Modified `~/bridge/bridge.py` — Increased generate_itinerary Claude CLI timeout from 300s to 480s
  - Modified `app/build.gradle` — versionCode 93→94, versionName 3.71→3.72

## v3.71 (2026-03-11)
- **Enhancement: Travel Planner — Add missing optimizeRoute method**
  - Modified `BridgeClient.java` — Added `optimizeRoute(String spotsJson, String transportMode, TravelCallback)` method to complete the 4-method travel API (bridge.py already had the route handler)
  - Modified `app/build.gradle` — versionCode 92→93, versionName 3.70→3.71

## v3.70 (2026-03-11)
- **New Feature: Taiwan Travel Planner (旅遊規劃大師)**
  - Added `TravelActivity.java` — Main trip list with status sections (planning/ongoing/completed), long-press management (start/complete/delete trips)
  - Added `CreateTripActivity.java` — Trip creation form with destination input, region quick-select chips, date picker, transport mode toggle, preference multi-select, accommodation type, AI itinerary generation
  - Added `TravelPlanActivity.java` — Full itinerary viewer with daily timeline, spot cards with Google Maps navigation, transport segments, meal recommendations, share and AI refine
  - Added `TravelBudgetActivity.java` — Budget management with estimated vs actual comparison, category breakdown, expense recording, over-budget warnings
  - Added `TravelAchievementActivity.java` — Achievement gallery with 17 achievements, stats summary, progress tracking
  - Added `TravelAchievementManager.java` — Achievement check logic with region mapping, auto-unlock on trip completion
  - Added `TravelDbHelper.java` — SQLite database (mybot_travel.db) with 4 tables: trips, trip_expenses, achievements, travel_stats
  - Modified `BridgeClient.java` — Added `generateItinerary()`, `refineItinerary()`, `searchAttractions()` with TravelCallback interface
  - Modified `MainActivity.java` — Added travel planning entry button in feature grid
  - Modified `AndroidManifest.xml` — Registered 5 new activities
  - Modified `~/bridge/bridge.py` — Added 4 new task types with detailed prompt engineering for Taiwan travel
  - Modified `app/build.gradle` — versionCode 91→92, versionName 3.69→3.70

## v3.69 (2026-03-11)
- **Enhancement: Expense — Category color differentiation**
  - Modified `UIHelper.java` — Added shared `CATEGORY_COLORS` array and `getCategoryColor(String)` method that maps category names to consistent colors via hashCode
  - Modified `ExpenseActivity.java` — Category badges now use per-category colors instead of fixed purple
  - Modified `ReportActivity.java` — Replaced local `CATEGORY_COLORS` array with shared `UIHelper.getCategoryColor()` for consistent colors between list and report views
  - Modified `app/build.gradle` — versionCode 91, versionName 3.69

## v3.68 (2026-03-11)
- **Enhancement: Flight Watch — Price change notifications**
  - Modified `FlightCheckReceiver.java` — Notify on any price change: 📉 降價 (with %) when cheaper than last check, 📈 漲價 (with %) when more expensive; always updates last price for next comparison; target price notification still works independently
  - Modified `app/build.gradle` — versionCode 90, versionName 3.68

## v3.67 (2026-03-11)
- **Enhancement: Flight Watch — Show best combo on watch cards**
  - Modified `FlightActivity.java` — Watch cards now display best flight combo from last search: round-trip shows "🏆 最低組合" with outbound+inbound cheapest airlines/times/prices; one-way shows "🏆 最低" with cheapest airline/time/stops; displayed in ACCENT_GREEN below price row
  - Modified `app/build.gradle` — versionCode 89, versionName 3.67

## v3.66 (2026-03-11)
- **Fix: Flight Watch — Round-trip search and direction labels**
  - Modified `~/bridge/google_flights.py` — Round-trip now searches outbound and return as separate one-way queries then merges; fixes issue where Google Flights embedded HTML only returns 1 "best combo" for round-trip; each flight tagged with `direction` field ("outbound"/"inbound"); refactored into `_fetch_oneway_flights()` helper
  - Modified `FlightActivity.java` — Result dialog shows 【去程】/【回程】 labels; manual search calculates round-trip cheapest as outbound min + inbound min
  - Modified `FlightCheckReceiver.java` — Round-trip notification uses outbound min + inbound min for cheapest price comparison
  - Modified `app/build.gradle` — versionCode 88, versionName 3.66

## v3.65 (2026-03-11)
- **Enhancement: Flight Watch — Switch to Google Flights (zero cost, faster, more airlines)**
  - Added `~/bridge/google_flights.py` — Standalone Google Flights scraper using reverse-engineered protobuf TFS parameter + requests; parses embedded JSON from HTML script tags; supports one-way/round-trip, direct-only filter, returns all airlines including Starlux
  - Added `~/bridge/flights_pb2.py` + `~/bridge/flights.proto` — Protobuf definitions for Google Flights query encoding (Airport, FlightData, Info, Seat, Trip, Passenger)
  - Modified `~/bridge/bridge.py` — search_flights task now calls `google_flights.search_flights()` directly instead of Claude AI (Kiwi MCP or web search); zero API cost, ~1s response time vs ~50-90s before; 36 airlines coverage vs ~10 before
  - Modified `app/build.gradle` — versionCode 87, versionName 3.65

## v3.64 (2026-03-11)
- **Enhancement: Flight Watch — Direct Flight Filter**
  - Modified `FlightWatchDbHelper.java` — DB version 3: added `direct_only` (INTEGER) column; `insert()` accepts directOnly parameter; `onUpgrade()` handles v2→v3 migration; `FlightWatch` inner class has new `directOnly` field
  - Modified `FlightActivity.java` — Add dialog: "僅直飛（不接受轉機）" Switch toggle; watch cards show "直飛" label when enabled
  - Modified `BridgeClient.java` — `searchFlights()` and `searchFlightsSync()` pass `direct_only` parameter to Bridge
  - Modified `FlightCheckReceiver.java` — Passes `watch.directOnly` to `searchFlightsSync()`
  - Modified `~/bridge/bridge.py` — search_flights prompt includes direct-only constraint (max_stopovers=0) when enabled
  - Modified `app/build.gradle` — versionCode 86, versionName 3.64

## v3.63 (2026-03-11)
- **Enhancement: Flight Watch — Round-trip & Airline Filtering**
  - Modified `FlightWatchDbHelper.java` — DB version 2: added `preferred_airlines` (TEXT) and `round_trip` (INTEGER) columns; `insert()` now accepts roundTrip and preferredAirlines parameters; `FlightWatch` inner class has new fields; `onUpgrade()` handles v1→v2 migration with ALTER TABLE
  - Modified `FlightActivity.java` — Add dialog: round-trip Switch toggle (shows/hides return date fields), airline preference EditText, dark-themed dialog (BG_PRIMARY background, BG_CARD styled inputs); watch cards show round-trip/single-way label and preferred airlines; passes roundTrip and preferredAirlines to BridgeClient
  - Modified `BridgeClient.java` — `searchFlights()` and `searchFlightsSync()` now accept and pass `roundTrip` (boolean) and `preferredAirlines` (String) parameters to Bridge server
  - Modified `FlightCheckReceiver.java` — Passes `watch.roundTrip` and `watch.preferredAirlines` to `searchFlightsSync()`
  - Modified `~/bridge/bridge.py` — search_flights prompt updated for round-trip total price and airline preference filtering; MCP calls use `--model sonnet` and `--max-turns 10` for reliable Kiwi tool use; `call_claude_web()` also uses `--model sonnet` explicitly
  - Modified `app/build.gradle` — versionCode 85, versionName 3.63

## v3.62 (2026-03-10)
- **Enhancement: Flight Watch — UX Improvements**
  - Modified `FlightActivity.java` — Airport picker dialog now uses dark theme (BG_PRIMARY background, BG_CARD_ALT item cards, ACCENT_BLUE codes, ACCENT_ORANGE country headers); search input styled with BG_INPUT background; each airport is a distinct card row for better readability
  - Modified `FlightActivity.java` — Target price now optional: leave empty = auto-track mode (notify on first result + every 10% price drop); watch cards show "自動追蹤降價" for auto-track watches
  - Modified `FlightActivity.java` — Month mode now uses custom year-month picker (4x3 month grid with year navigation arrows) instead of full DatePicker; stores "YYYY-MM" format; label changes to "出發月份（搜尋整月最低價）" when month mode selected
  - Modified `FlightCheckReceiver.java` — Updated notification logic: auto-track mode sends first-result notification to establish baseline, then notifies on >10% drops with exact percentage; target mode still notifies at target price
  - Modified `~/bridge/bridge.py` — Month mode prompt now instructs Kiwi to search date_from=YYYY-MM-01 to date_to=month end for true whole-month search
  - Modified `app/build.gradle` — versionCode 84, versionName 3.62

## v3.61 (2026-03-10)
- **Enhancement: Flight Watch — Airport Picker with Chinese Names**
  - Modified `FlightActivity.java` — Replaced origin/destination EditText with tap-to-select airport picker dialog; added 60+ airports with IATA code, Chinese city name, country, and flag emoji; airports grouped by region (台灣→日本→韓國→東南亞→港澳中國→歐洲→美洲→大洋洲→中東); live search filter by city/country/code; watch cards now show city names (e.g. "TPE 台北桃園 → NRT 東京成田"); added `getAirportLabel()` and `showAirportPicker()` helper methods
  - Modified `app/build.gradle` — versionCode 83, versionName 3.61
- **Enhancement: Bridge — AI Model Cost Optimization**
  - Modified `~/bridge/bridge.py` — Added `TASK_MODEL_MAP` routing simple tasks to Haiku (analyze_notification, summarize_sms, categorize_expense, categorize_knowledge, parse_calendar_event, analyze_expense_screenshot, analyze_invoice); complex tasks keep default Sonnet (workout_plan, summarize_video, analyze_stock, search_flights); added `model` parameter to `call_claude()`, `call_claude_vision()`, `_call_claude_vision_async()`; response includes `model` field for tracking; estimated ~50% API cost reduction

## v3.60 (2026-03-10)
- **New Feature: Flight Price Watch (航班監控)**
  - Added `FlightWatchDbHelper.java` — SQLite database (`mybot_flight.db`) for flight watch entries with CRUD operations; stores origin, destination, dates, target price, search mode (date/month), last check results; SharedPrefs toggle for global flight check enable/disable
  - Added `FlightActivity.java` — Full UI with status card (6-hour check toggle), watch list (route, dates, target/lowest price, last check time), add dialog (IATA origin/dest, date picker, search mode toggle, target price), manual search with loading indicator, result detail dialog showing flight list
  - Added `FlightCheckReceiver.java` — BroadcastReceiver with `goAsync()` for background flight checks; iterates enabled watches sequentially (2s gap); calls Bridge sync API; notifies when price ≤ target or drops >10% from last check
  - Modified `BridgeClient.java` — Added `FlightSearchCallback` interface, `searchFlights()` async method (130s timeout), `searchFlightsSync()` for receiver use
  - Modified `ReminderHelper.java` — Added flight check scheduling (FLIGHT_REQUEST_CODE=9100, 6-hour interval), `scheduleFlightCheck()`, `scheduleNextFlightCheck()`, `cancelFlightCheck()`, `restoreFlightIfEnabled()`
  - Modified `BootReceiver.java` — Added `restoreFlightIfEnabled()` in `restoreAlarms()`
  - Modified `MainActivity.java` — Replaced Row 4 placeholder with "✈ 航班監控" card (ACCENT_ORANGE); added `restoreFlightIfEnabled()` in onCreate
  - Modified `AndroidManifest.xml` — Registered `FlightActivity` and `FlightCheckReceiver`
  - Added `~/bridge/mcp_flight.json` — Kiwi.com MCP config (remote HTTP MCP, no API key)
  - Modified `~/bridge/bridge.py` — Added `search_flights` task (120s timeout); added `call_claude_mcp()` function using `--mcp-config` with Kiwi MCP and `--max-turns 5`; added search_flights prompt builder and routing in `/analyze`
  - Modified `app/build.gradle` — versionCode 82, versionName 3.60

## v3.59 (2026-03-10)
- **Security Hardening — First Security Audit (versionCode 81)**
  - Full OWASP Mobile Top 10 audit performed on all 54 Java files. Fixed 6 of 15 findings.
  - **[HIGH] Network Security**: Added `res/xml/network_security_config.xml` — cleartext traffic now restricted to localhost only (127.0.0.1/localhost for Bridge); all other connections require HTTPS. Removed global `usesCleartextTraffic="true"` from AndroidManifest.
  - **[HIGH] OAuth Secrets Encrypted**: Added `SecurePrefs.java` — wrapper around `EncryptedSharedPreferences` (AES256-GCM) for storing OAuth client_secret, access_token, token_expiry; auto-migrates from legacy plaintext `calendar_prefs` and removes old keys.
  - Modified `GoogleAuthHelper.java` — All token read/write now uses `SecurePrefs.get()` instead of plaintext SharedPreferences; email in log masked (e.g. "che***@gmail.com")
  - Modified `CalendarActivity.java` — Settings dialog reads/writes client_secret via `SecurePrefs`
  - **[MEDIUM] Backup Disabled**: `AndroidManifest.xml` — `allowBackup` set to `false` to prevent `adb backup` extraction of databases and preferences
  - **APK Cleanup**: Added `UpdateChecker.cleanOldApks()` — deletes `mybot-v*.apk` files from Downloads on app startup; called from `MainActivity.onCreate`
  - Modified `app/build.gradle` — Added `androidx.security:security-crypto:1.0.0` dependency; versionCode 81, versionName 3.59
  - **Remaining items for future audits**: Bridge auth token (#9), APK download checksum (#2), R8 minification (#7), targetSdk upgrade (#8), SQLCipher (#6)

## v3.58 (2026-03-10)
- **Change: Dashboard — Replace Stock Card with Fitness Streak**
  - Modified `MainActivity.java` — Replaced "台股追蹤" dashboard card with "健身連續" card showing workout streak days from `FitnessDbHelper.getStreak()`; updated `loadDashboardData()` to accept 4th parameter for fitness card; displays streak as "N天" format
  - Modified `app/build.gradle` — versionCode 80, versionName 3.58

## v3.57 (2026-03-10)
- **Fix: Dashboard Knowledge Count Not Displaying**
  - Modified `MainActivity.java` — Wrapped each DB query in separate try-catch to prevent one failure from blocking all dashboard updates; added `updateDashValue()` with fallback to direct child index access if `findViewWithTag` fails; added AppLog error logging for dashboard data loading
  - Modified `UIHelper.java` — Added `clickable` and `focusable` to `dashboardCard()` for proper click handling
  - Modified `app/build.gradle` — versionCode 79, versionName 3.57

## v3.56 (2026-03-10)
- **Redesign: Home Page — Section-Based Compact Layout with Dashboard**
  - Modified `MainActivity.java` — Replaced 2-column large feature cards with 3-column compact icon grid; added DASHBOARD section with 2x2 summary cards (today's expenses, pending todos, knowledge count, stock shortcut) with async data loading; FEATURES section now uses compact cards (44dp icon + label); TOOLS section also 3-column compact; added `gridRow3()`, `addCompact()`, `loadDashboardData()` helper methods
  - Modified `UIHelper.java` — Added `compactCard()` method (small 44dp icon circle + 12sp label, 3-per-row); added `dashboardCard()` method (icon + large value + label, with `dashboard_value` tag for dynamic updates)
  - Modified `app/build.gradle` — versionCode 78, versionName 3.56

## v3.55 (2026-03-10)
- **New Feature: Knowledge Base — Save & Browse AI-Categorized Knowledge**
  - Added `KnowledgeDbHelper.java` — SQLite database helper for knowledge entries; stores title, summary, key_points, source_url, AI-determined category, created_at; supports getAll, getByCategory, search (title/summary/key_points LIKE), getAllCategories, getCount
  - Added `KnowledgeActivity.java` — Standalone browse/search UI with search bar, horizontal category filter chips (auto-populated from DB), knowledge cards with category badge, date, truncated summary, action buttons (source link, detail dialog, delete with confirmation); detail dialog shows full summary, key points, source URL
  - Modified `YouTubeActivity.java` — Added "儲存到知識庫" button in result area alongside existing copy button; calls BridgeClient.categorizeKnowledge() for AI auto-categorization then saves to KnowledgeDbHelper; button shows loading state and success confirmation
  - Modified `BridgeClient.java` — Added `KnowledgeCategoryCallback` interface and `categorizeKnowledge()` method; sends title+summary to Bridge for AI classification into predefined categories (科技/投資/健康/教育/etc.); falls back to "其他" on error
  - Modified `bridge.py` — Added `categorize_knowledge` task type with 30s timeout; added prompt builder that classifies content into knowledge categories and returns JSON
  - Modified `MainActivity.java` — Added "知識庫" feature card (blue accent) in new row 6 under Features section
  - Modified `AndroidManifest.xml` — Registered `KnowledgeActivity`
  - Modified `app/build.gradle` — versionCode 77, versionName 3.55

## v3.54 (2026-03-10)
- **Fix: YouTube Summarizer — Tag Wrapping + Adaptive Summary Length**
  - Modified `YouTubeActivity.java` — Tags now use single `TextView` with natural line wrapping instead of horizontal `LinearLayout` that overflowed
  - Modified `bridge.py` — Summary prompt now scales with transcript length: short videos get 100-150 word summaries with 3-6 points; long videos (30k+ chars, ~90min) get 500-800 word summaries with 15-25 detailed points grouped by topic
  - Modified `app/build.gradle` — versionCode 76, versionName 3.54

## v3.53 (2026-03-10)
- **New Feature: YouTube Video Summarizer**
  - Added `YouTubeActivity.java` — New standalone feature page for YouTube video summarization; paste URL → fetch subtitles → AI generates summary with key points and topic tags; includes paste button, loading state, copy-to-clipboard, dark theme cards
  - Modified `BridgeClient.java` — Added `VideoSummaryCallback` interface and `summarizeVideo()` method; handles JSON result parsing with fallback to text
  - Modified `MainActivity.java` — Added "影片摘要" feature card (red accent) in row 5 alongside Music
  - Modified `AndroidManifest.xml` — Registered `YouTubeActivity`
  - Modified `bridge.py` — Added `fetch_youtube_transcript()` using `youtube_transcript_api` (prefers zh-Hant > en, manual > auto-generated); added `summarize_video` task type with 180s timeout; yt-dlp for title extraction
  - Modified `app/build.gradle` — versionCode 75, versionName 3.53
  - **Dependencies**: `youtube-transcript-api` (pip), `yt-dlp` (pip) on Termux

## v3.52 (2026-03-10)
- **Enhancement: Volume MA5 Line on Stock Chart**
  - Modified `StockChartView.java` — Added volume 5-period moving average as yellow dashed line over volume bars; displays MA value label on right margin (formatted as 萬/億); added `paintVolMa` paint, `volMaLastValue` field, `formatVolume()` helper
  - Modified `app/build.gradle` — versionCode 74, versionName 3.52

## v3.51 (2026-03-10)
- **Enhancement: Stock Chart Cache — Smart Refresh Tied to Market Hours**
  - Modified `StockCache.java` — Rewrote `isFresh()` logic: during market hours (9:00-13:30) cache is stale if fetched before today's open; after close (≥13:30) cache is stale if fetched before close (triggers one final re-fetch for closing data); weekend/pre-open logic unchanged
  - Modified `StockActivity.java` — `fetchQuotes()` now checks cache freshness on each cycle for historical periods, triggers `loadHistoricalData()` when stale (market open + post-close); periodically saves in-memory real-time candle updates back to cache every 5 minutes during market hours
  - Modified `app/build.gradle` — versionCode 73, versionName 3.51

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
