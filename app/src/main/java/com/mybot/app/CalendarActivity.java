package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CalendarActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = GoogleAuthHelper.RC_SIGN_IN;

    private LinearLayout contentLayout;
    private ScrollView scrollView;
    private Calendar currentMonth;
    private List<GoogleCalendarClient.CalendarInfo> calendars = new ArrayList<>();
    private List<GoogleCalendarClient.EventInfo> events = new ArrayList<>();
    private TextView cacheStatusText;
    private View todayAnchorView;
    private boolean needScrollToToday = true;

    // Color palette for different calendars
    private static final int[] CALENDAR_COLORS = {
            Color.parseColor("#4FC3F7"), // Light blue
            Color.parseColor("#66BB6A"), // Green
            Color.parseColor("#FFA726"), // Orange
            Color.parseColor("#AB47BC"), // Purple
            Color.parseColor("#EF5350"), // Red
            Color.parseColor("#26C6DA"), // Cyan
            Color.parseColor("#FFEE58"), // Yellow
            Color.parseColor("#EC407A"), // Pink
            Color.parseColor("#8D6E63"), // Brown
            Color.parseColor("#78909C"), // Blue grey
    };
    private final Map<String, Integer> calendarColorMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        currentMonth = Calendar.getInstance();

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "Google 日曆");

        Button btnRefresh = UIHelper.smallButton(this, "刷新", UIHelper.ACCENT_BLUE);
        Button btnSettings = UIHelper.smallButton(this, "設定", UIHelper.ACCENT_ORANGE);
        Button btnAdd = UIHelper.smallButton(this, "AI 新增", UIHelper.ACCENT_GREEN);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 36));
        btnLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        btnRefresh.setLayoutParams(btnLp);
        btnSettings.setLayoutParams(btnLp);
        btnAdd.setLayoutParams(btnLp);

        btnRefresh.setOnClickListener(v -> forceRefresh());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnAdd.setOnClickListener(v -> {
            if (!GoogleAuthHelper.isSignedIn(this)) {
                Toast.makeText(this, "請先登入 Google 帳號", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, AddCalendarEventActivity.class));
        });

        topBar.addView(btnRefresh);
        topBar.addView(btnSettings);
        topBar.addView(btnAdd);

        // Content
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        contentLayout.setPadding(p, p, p, p);

        scrollView.addView(contentLayout);

        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        checkSignInAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!GoogleAuthHelper.isSignedIn(this)) return;

        String monthKey = CalendarCache.monthKey(currentMonth);

        // Has cache — show it immediately
        if (CalendarCache.hasCalendars()) {
            calendars = CalendarCache.getCalendars();
        }
        if (CalendarCache.hasEvents(monthKey)) {
            events = CalendarCache.getEvents(monthKey);
            renderUI();
            // If stale, refresh silently in background
            if (!CalendarCache.isEventsValid(monthKey)) {
                refreshEventsBackground(monthKey);
            }
        } else if (!calendars.isEmpty()) {
            loadEvents(false);
        }
    }

    private void checkSignInAndLoad() {
        contentLayout.removeAllViews();

        String webClientId = GoogleAuthHelper.getWebClientId(this);
        if (webClientId.isEmpty()) {
            showSetupRequired();
            return;
        }

        if (!GoogleAuthHelper.isSignedIn(this)) {
            showSignInButton();
            return;
        }

        // Try cache first
        if (CalendarCache.hasCalendars() && CalendarCache.hasEvents(CalendarCache.monthKey(currentMonth))) {
            calendars = CalendarCache.getCalendars();
            events = CalendarCache.getEvents(CalendarCache.monthKey(currentMonth));
            renderUI();
            // Background refresh if stale
            if (!CalendarCache.isCalendarsValid() || !CalendarCache.isEventsValid(CalendarCache.monthKey(currentMonth))) {
                refreshAllBackground();
            }
            return;
        }

        // No cache — full load
        showLoading("載入日曆中...");
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token != null) {
                loadCalendarsAndEvents(token);
            } else {
                AppLog.e("Calendar", "Token refresh failed on initial load: " + error);
                GoogleAuthHelper.signOut(this, (s, e) -> showSignInButton());
            }
        });
    }

    private void forceRefresh() {
        if (!GoogleAuthHelper.isSignedIn(this)) {
            Toast.makeText(this, "尚未登入", Toast.LENGTH_SHORT).show();
            return;
        }
        CalendarCache.invalidateAllEvents();
        showLoading("重新載入中...");
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token != null) {
                loadCalendarsAndEvents(token);
            } else {
                AppLog.e("Calendar", "Token refresh failed on force refresh: " + error);
                showError("Token 失敗: " + error);
            }
        });
    }

    private void refreshAllBackground() {
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) return;
            GoogleCalendarClient.listCalendars(token, (cals, err) -> {
                if (cals != null) {
                    CalendarCache.putCalendars(cals);
                    calendars = cals;
                }
                refreshEventsBackground(CalendarCache.monthKey(currentMonth));
            });
        });
    }

    private void refreshEventsBackground(String monthKey) {
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) return;

            String[] range = getMonthRange(currentMonth);
            GoogleCalendarClient.listAllEvents(token, calendars, range[0], range[1], (evts, err) -> {
                if (evts != null) {
                    CalendarCache.putEvents(monthKey, evts);
                    // Only update UI if still on same month
                    if (monthKey.equals(CalendarCache.monthKey(currentMonth))) {
                        events = evts;
                        renderUI();
                    }
                }
            });
        });
    }

    private void showSetupRequired() {
        contentLayout.removeAllViews();
        LinearLayout card = UIHelper.card(this);

        TextView title = new TextView(this);
        title.setText("需要設定 Google Calendar");
        title.setTextSize(18);
        title.setTextColor(UIHelper.TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        TextView desc = new TextView(this);
        desc.setText("請先點擊右上角「設定」，輸入 Web Client ID 和 Client Secret。\n\n"
                + "GCP Console 設定步驟：\n"
                + "1. 建立 GCP 專案\n"
                + "2. 啟用 Google Calendar API\n"
                + "3. 設定 OAuth 同意畫面\n"
                + "4. 建立 Android 用戶端 ID（填入 SHA-1 + 套件名稱）\n"
                + "5. 建立 Web 用戶端 ID\n"
                + "6. 將 Web Client ID 和 Client Secret 填入設定");
        desc.setTextSize(14);
        desc.setTextColor(UIHelper.TEXT_SECONDARY);
        desc.setPadding(0, UIHelper.dp(this, 12), 0, 0);

        card.addView(title);
        card.addView(desc);
        contentLayout.addView(card);
    }

    private void showSignInButton() {
        contentLayout.removeAllViews();
        LinearLayout card = UIHelper.card(this);

        TextView label = new TextView(this);
        label.setText("請登入 Google 帳號以存取日曆");
        label.setTextSize(16);
        label.setTextColor(UIHelper.TEXT_PRIMARY);
        label.setGravity(Gravity.CENTER);

        Button btnSignIn = UIHelper.primaryButton(this, "Google 登入");
        btnSignIn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 14, this));
        btnSignIn.setOnClickListener(v -> {
            Intent signInIntent = GoogleAuthHelper.getSignInIntent(this);
            if (signInIntent != null) {
                startActivityForResult(signInIntent, RC_SIGN_IN);
            } else {
                Toast.makeText(this, "Web Client ID 未設定", Toast.LENGTH_SHORT).show();
            }
        });

        card.addView(label);
        card.addView(btnSignIn);
        contentLayout.addView(card);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            GoogleAuthHelper.handleSignInResult(data, (success, error) -> {
                if (success) {
                    AppLog.i("Calendar", "Google sign-in successful");
                    Toast.makeText(this, "登入成功", Toast.LENGTH_SHORT).show();
                    CalendarCache.clearAll();
                    checkSignInAndLoad();
                } else {
                    AppLog.e("Calendar", "Google sign-in failed: " + error);
                    Toast.makeText(this, "登入失敗: " + error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showLoading(String msg) {
        contentLayout.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(16);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, UIHelper.dp(this, 40), 0, 0);
        contentLayout.addView(tv);
    }

    private void showError(String msg) {
        contentLayout.removeAllViews();
        LinearLayout card = UIHelper.card(this);
        TextView tv = new TextView(this);
        tv.setText("Error: " + msg);
        tv.setTextSize(14);
        tv.setTextColor(UIHelper.ACCENT_RED);
        card.addView(tv);

        Button retry = UIHelper.primaryButton(this, "重試");
        retry.setOnClickListener(v -> checkSignInAndLoad());
        card.addView(retry);

        contentLayout.addView(card);
    }

    private void loadCalendarsAndEvents(String token) {
        GoogleCalendarClient.listCalendars(token, (cals, error) -> {
            if (cals != null) {
                AppLog.i("Calendar", "Loaded " + cals.size() + " calendars");
                calendars = cals;
                CalendarCache.putCalendars(cals);
                loadEvents(true);
            } else {
                AppLog.e("Calendar", "Failed to load calendars: " + error);
                showError("載入日曆失敗: " + error);
            }
        });
    }

    private void loadEvents(boolean showLoadingUI) {
        if (showLoadingUI) showLoading("載入事件中...");

        String monthKey = CalendarCache.monthKey(currentMonth);

        // Check cache first (for month switch)
        if (CalendarCache.isEventsValid(monthKey)) {
            events = CalendarCache.getEvents(monthKey);
            renderUI();
            return;
        }

        // Has stale cache — show it while refreshing
        if (CalendarCache.hasEvents(monthKey)) {
            events = CalendarCache.getEvents(monthKey);
            renderUI();
            refreshEventsBackground(monthKey);
            return;
        }

        // No cache at all — must load
        GoogleAuthHelper.getCachedOrFreshToken(this, (token, error) -> {
            if (token == null) {
                AppLog.e("Calendar", "Token refresh failed on load events: " + error);
                showError("Token 失敗: " + error);
                return;
            }

            String[] range = getMonthRange(currentMonth);
            GoogleCalendarClient.listAllEvents(token, calendars, range[0], range[1], (evts, err) -> {
                if (evts != null) {
                    AppLog.i("Calendar", "Loaded " + evts.size() + " events for " + monthKey);
                    events = evts;
                    CalendarCache.putEvents(monthKey, evts);
                    renderUI();
                } else {
                    AppLog.e("Calendar", "Failed to load events: " + err);
                    showError("載入事件失敗: " + err);
                }
            });
        });
    }

    private String[] getMonthRange(Calendar month) {
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);

        Calendar end = (Calendar) month.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return new String[]{sdf.format(start.getTime()), sdf.format(end.getTime())};
    }

    private void renderUI() {
        contentLayout.removeAllViews();

        // Month navigation
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setPadding(0, 0, 0, UIHelper.dp(this, 8));

        Button btnPrev = UIHelper.smallButton(this, "<", UIHelper.TEXT_SECONDARY);
        btnPrev.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            loadEvents(false);
        });

        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy 年 MM 月", Locale.TAIWAN);
        TextView monthLabel = new TextView(this);
        monthLabel.setText(monthFmt.format(currentMonth.getTime()));
        monthLabel.setTextSize(20);
        monthLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        monthLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        monthLabel.setGravity(Gravity.CENTER);
        monthLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnNext = UIHelper.smallButton(this, ">", UIHelper.TEXT_SECONDARY);
        btnNext.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            loadEvents(false);
        });

        navRow.addView(btnPrev);
        navRow.addView(monthLabel);
        navRow.addView(btnNext);
        contentLayout.addView(navRow);

        // Account info card
        LinearLayout acctCard = UIHelper.card(this);
        LinearLayout acctRow = new LinearLayout(this);
        acctRow.setOrientation(LinearLayout.HORIZONTAL);
        acctRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView acctLabel = new TextView(this);
        String email = GoogleAuthHelper.getAccount(this) != null
                ? GoogleAuthHelper.getAccount(this).getEmail() : "Unknown";
        acctLabel.setText(email);
        acctLabel.setTextSize(13);
        acctLabel.setTextColor(UIHelper.ACCENT_BLUE);
        acctLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView evtCount = new TextView(this);
        evtCount.setText(events.size() + " 個事件");
        evtCount.setTextSize(13);
        evtCount.setTextColor(UIHelper.TEXT_SECONDARY);

        acctRow.addView(acctLabel);
        acctRow.addView(evtCount);
        acctCard.addView(acctRow);

        // Default calendar info
        String defaultCal = GoogleAuthHelper.getDefaultCalendar(this);
        String calName = "primary";
        for (GoogleCalendarClient.CalendarInfo c : calendars) {
            if (c.id.equals(defaultCal)) {
                calName = c.summary;
                break;
            }
        }
        TextView calInfo = new TextView(this);
        calInfo.setText("預設日曆: " + calName);
        calInfo.setTextSize(12);
        calInfo.setTextColor(UIHelper.TEXT_HINT);
        calInfo.setPadding(0, UIHelper.dp(this, 4), 0, 0);
        acctCard.addView(calInfo);

        // Cache status
        String monthKey = CalendarCache.monthKey(currentMonth);
        long age = CalendarCache.getEventsAge(monthKey);
        String cacheInfo;
        if (age < 0) {
            cacheInfo = "未快取";
        } else if (age < 60000) {
            cacheInfo = "快取: " + (age / 1000) + " 秒前";
        } else {
            cacheInfo = "快取: " + (age / 60000) + " 分鐘前";
        }
        cacheStatusText = new TextView(this);
        cacheStatusText.setText(cacheInfo);
        cacheStatusText.setTextSize(11);
        cacheStatusText.setTextColor(CalendarCache.isEventsValid(monthKey) ?
                UIHelper.ACCENT_GREEN : UIHelper.ACCENT_ORANGE);
        cacheStatusText.setPadding(0, UIHelper.dp(this, 2), 0, 0);
        acctCard.addView(cacheStatusText);

        contentLayout.addView(acctCard);

        // Build calendar color map
        buildCalendarColorMap();

        // Events list
        contentLayout.addView(UIHelper.sectionHeader(this, "EVENTS"));

        todayAnchorView = null;
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        if (events.isEmpty()) {
            LinearLayout emptyCard = UIHelper.card(this);
            TextView emptyText = new TextView(this);
            emptyText.setText("本月沒有事件");
            emptyText.setTextSize(15);
            emptyText.setTextColor(UIHelper.TEXT_HINT);
            emptyText.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyText);
            contentLayout.addView(emptyCard);
        } else {
            String lastDate = "";
            for (GoogleCalendarClient.EventInfo ev : events) {
                String dateStr = formatDateHeader(ev.startTime);
                if (!dateStr.equals(lastDate)) {
                    lastDate = dateStr;
                    String rawDate = extractDate(ev.startTime);
                    boolean isPast = rawDate.compareTo(todayStr) < 0;
                    boolean isToday = rawDate.equals(todayStr);

                    TextView dateHeader = new TextView(this);
                    dateHeader.setText(dateStr + (isToday ? "  ← 今天" : ""));
                    dateHeader.setTextSize(13);
                    dateHeader.setTextColor(isPast ? UIHelper.TEXT_HINT :
                            isToday ? UIHelper.ACCENT_BLUE : UIHelper.ACCENT_GREEN);
                    dateHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    dateHeader.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 12),
                            0, UIHelper.dp(this, 4));
                    contentLayout.addView(dateHeader);

                    if (isToday && todayAnchorView == null) {
                        todayAnchorView = dateHeader;
                    }
                }
                boolean eventPast = extractDate(ev.startTime).compareTo(todayStr) < 0;
                contentLayout.addView(createEventCard(ev, eventPast));
            }
        }

        // Auto-scroll to today
        if (needScrollToToday && todayAnchorView != null) {
            needScrollToToday = false;
            View anchor = todayAnchorView;
            scrollView.post(() -> scrollView.smoothScrollTo(0, anchor.getTop() - UIHelper.dp(this, 8)));
        }
    }

    private void buildCalendarColorMap() {
        calendarColorMap.clear();
        int idx = 0;
        for (GoogleCalendarClient.CalendarInfo cal : calendars) {
            calendarColorMap.put(cal.id, CALENDAR_COLORS[idx % CALENDAR_COLORS.length]);
            idx++;
        }
    }

    private String extractDate(String dateTime) {
        if (dateTime == null) return "";
        return dateTime.substring(0, Math.min(10, dateTime.length()));
    }

    private LinearLayout createEventCard(GoogleCalendarClient.EventInfo ev, boolean isPast) {
        LinearLayout card = UIHelper.card(this);

        // Calendar color indicator (left border)
        int calColor = calendarColorMap.containsKey(ev.calendarId)
                ? calendarColorMap.get(ev.calendarId) : UIHelper.ACCENT_BLUE;
        int displayColor = isPast ? dimColor(calColor) : calColor;

        View colorBar = new View(this);
        colorBar.setBackgroundColor(displayColor);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 4), ViewGroup.LayoutParams.MATCH_PARENT);
        barLp.setMargins(0, 0, UIHelper.dp(this, 12), 0);

        // Wrap card content with a horizontal layout for the color bar
        LinearLayout innerRow = new LinearLayout(this);
        innerRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        String timeStr = ev.allDay ? "全天" : formatTime(ev.startTime);
        int badgeColor = isPast ? UIHelper.TEXT_HINT : displayColor;
        TextView timeBadge = UIHelper.statusBadge(this, timeStr, badgeColor);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(0, 0, UIHelper.dp(this, 10), 0);
        timeBadge.setLayoutParams(badgeLp);

        TextView titleTv = new TextView(this);
        titleTv.setText(ev.summary);
        titleTv.setTextSize(16);
        titleTv.setTextColor(isPast ? UIHelper.TEXT_HINT : UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        titleRow.addView(timeBadge);
        titleRow.addView(titleTv);
        contentCol.addView(titleRow);

        if (!ev.allDay && ev.endTime != null) {
            TextView duration = new TextView(this);
            duration.setText(formatTime(ev.startTime) + " - " + formatTime(ev.endTime));
            duration.setTextSize(12);
            duration.setTextColor(isPast ? UIHelper.TEXT_HINT : UIHelper.TEXT_SECONDARY);
            duration.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            contentCol.addView(duration);
        }

        if (ev.location != null && !ev.location.isEmpty()) {
            TextView loc = new TextView(this);
            loc.setText("📍 " + ev.location);
            loc.setTextSize(12);
            loc.setTextColor(isPast ? UIHelper.TEXT_HINT : UIHelper.TEXT_SECONDARY);
            loc.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            contentCol.addView(loc);
        }

        if (ev.calendarName != null && !ev.calendarName.isEmpty()) {
            TextView calLabel = new TextView(this);
            calLabel.setText(ev.calendarName);
            calLabel.setTextSize(11);
            calLabel.setTextColor(displayColor);
            calLabel.setPadding(0, UIHelper.dp(this, 4), 0, 0);
            contentCol.addView(calLabel);
        }

        innerRow.addView(colorBar, barLp);
        innerRow.addView(contentCol);
        card.addView(innerRow);

        // Dim the entire card for past events
        if (isPast) {
            card.setAlpha(0.6f);
        }

        return card;
    }

    private int dimColor(int color) {
        int r = (Color.red(color) + 128) / 3;
        int g = (Color.green(color) + 128) / 3;
        int b = (Color.blue(color) + 128) / 3;
        return Color.rgb(r, g, b);
    }

    private String formatDateHeader(String dateTime) {
        if (dateTime == null) return "";
        try {
            SimpleDateFormat sdf;
            if (dateTime.length() == 10) {
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            }
            Date date = sdf.parse(dateTime);
            SimpleDateFormat out = new SimpleDateFormat("MM/dd (E)", Locale.TAIWAN);
            return out.format(date);
        } catch (Exception e) {
            return dateTime.substring(0, Math.min(10, dateTime.length()));
        }
    }

    private String formatTime(String dateTime) {
        if (dateTime == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            Date date = sdf.parse(dateTime);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.US);
            return out.format(date);
        } catch (Exception e) {
            if (dateTime.length() > 11) {
                return dateTime.substring(11, Math.min(16, dateTime.length()));
            }
            return dateTime;
        }
    }

    private void showSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(UIHelper.BG_CARD);
        int p = UIHelper.dp(this, 20);
        layout.setPadding(p, p, p, p);

        // Web Client ID
        TextView labelId = new TextView(this);
        labelId.setText("Web Client ID");
        labelId.setTextSize(14);
        labelId.setTextColor(UIHelper.TEXT_PRIMARY);
        layout.addView(labelId);

        EditText inputId = UIHelper.styledInput(this, "xxxx.apps.googleusercontent.com");
        inputId.setText(GoogleAuthHelper.getWebClientId(this));
        inputId.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(inputId);

        // Client Secret
        TextView labelSecret = new TextView(this);
        labelSecret.setText("Client Secret");
        labelSecret.setTextSize(14);
        labelSecret.setTextColor(UIHelper.TEXT_PRIMARY);
        labelSecret.setPadding(0, UIHelper.dp(this, 12), 0, 0);
        layout.addView(labelSecret);

        EditText inputSecret = UIHelper.styledInput(this, "GOCSPX-...");
        inputSecret.setText(SecurePrefs.get(this)
                .getString(SecurePrefs.KEY_WEB_CLIENT_SECRET, ""));
        inputSecret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputSecret);

        // Default calendar selector
        if (!calendars.isEmpty()) {
            TextView labelCal = new TextView(this);
            labelCal.setText("預設日曆");
            labelCal.setTextSize(14);
            labelCal.setTextColor(UIHelper.TEXT_PRIMARY);
            labelCal.setPadding(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 4));
            layout.addView(labelCal);

            String currentDefault = GoogleAuthHelper.getDefaultCalendar(this);
            for (GoogleCalendarClient.CalendarInfo cal : calendars) {
                Button calBtn = new Button(this);
                calBtn.setText(cal.summary + (cal.primary ? " (主要)" : ""));
                calBtn.setTextColor(cal.id.equals(currentDefault) ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_SECONDARY);
                calBtn.setTextSize(14);
                calBtn.setAllCaps(false);
                calBtn.setBackground(UIHelper.roundRectStroke(
                        cal.id.equals(currentDefault) ? Color.parseColor("#1A3A2A") : Color.TRANSPARENT,
                        cal.id.equals(currentDefault) ? UIHelper.ACCENT_GREEN : UIHelper.TEXT_HINT,
                        10, 1, this));
                calBtn.setStateListAnimator(null);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 44));
                clp.setMargins(0, UIHelper.dp(this, 4), 0, 0);
                calBtn.setLayoutParams(clp);
                calBtn.setOnClickListener(v -> {
                    GoogleAuthHelper.saveDefaultCalendar(this, cal.id);
                    Toast.makeText(this, "預設日曆: " + cal.summary, Toast.LENGTH_SHORT).show();
                });
                layout.addView(calBtn);
            }
        }

        // Cache management
        TextView labelCache = new TextView(this);
        labelCache.setText("快取管理");
        labelCache.setTextSize(14);
        labelCache.setTextColor(UIHelper.TEXT_PRIMARY);
        labelCache.setPadding(0, UIHelper.dp(this, 16), 0, UIHelper.dp(this, 4));
        layout.addView(labelCache);

        Button btnClearCache = new Button(this);
        btnClearCache.setText("清除所有快取");
        btnClearCache.setTextColor(UIHelper.ACCENT_ORANGE);
        btnClearCache.setTextSize(14);
        btnClearCache.setAllCaps(false);
        btnClearCache.setBackground(UIHelper.roundRectStroke(
                Color.TRANSPARENT, UIHelper.ACCENT_ORANGE, 10, 1, this));
        btnClearCache.setStateListAnimator(null);
        LinearLayout.LayoutParams cacheLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 44));
        cacheLp.setMargins(0, UIHelper.dp(this, 4), 0, 0);
        btnClearCache.setLayoutParams(cacheLp);
        btnClearCache.setOnClickListener(v -> {
            CalendarCache.clearAll();
            Toast.makeText(this, "快取已清除", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnClearCache);

        // Sign out button
        if (GoogleAuthHelper.isSignedIn(this)) {
            Button btnSignOut = new Button(this);
            btnSignOut.setText("登出 Google");
            btnSignOut.setTextColor(UIHelper.ACCENT_RED);
            btnSignOut.setTextSize(14);
            btnSignOut.setAllCaps(false);
            btnSignOut.setBackground(UIHelper.roundRectStroke(
                    Color.TRANSPARENT, UIHelper.ACCENT_RED, 10, 1, this));
            btnSignOut.setStateListAnimator(null);
            LinearLayout.LayoutParams solp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 44));
            solp.setMargins(0, UIHelper.dp(this, 12), 0, 0);
            btnSignOut.setLayoutParams(solp);
            btnSignOut.setOnClickListener(v -> {
                GoogleAuthHelper.signOut(this, (success, err) -> {
                    AppLog.i("Calendar", "Google signed out");
                    CalendarCache.clearAll();
                    Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show();
                    checkSignInAndLoad();
                });
            });
            layout.addView(btnSignOut);
        }

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Google Calendar 設定")
                .setView(layout)
                .setPositiveButton("儲存", (d, w) -> {
                    String webId = inputId.getText().toString().trim();
                    String secret = inputSecret.getText().toString().trim();
                    String oldId = GoogleAuthHelper.getWebClientId(this);
                    GoogleAuthHelper.saveWebClientId(this, webId);
                    SecurePrefs.get(this).edit()
                            .putString(SecurePrefs.KEY_WEB_CLIENT_SECRET, secret).apply();
                    Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
                    if (!webId.equals(oldId) && GoogleAuthHelper.isSignedIn(this)) {
                        CalendarCache.clearAll();
                        GoogleAuthHelper.signOut(this, (s, e) -> checkSignInAndLoad());
                    } else {
                        checkSignInAndLoad();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
