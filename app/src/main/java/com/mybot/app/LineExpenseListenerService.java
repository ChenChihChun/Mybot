package com.mybot.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens to LINE notifications and uses AI (Bridge/Haiku) to detect expense/transfer records.
 * Supports any message format — no regex required.
 */
public class LineExpenseListenerService extends NotificationListenerService {

    private static final String LINE_PACKAGE = "jp.naver.line.android";
    private static final float CONFIDENCE_THRESHOLD = 0.6f;
    private static final long DEDUP_WINDOW_MS = 60_000;

    // Dedup: content hash → timestamp, prevent double-fire from notification updates
    private final Map<Integer, Long> recentHashes = new HashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!LINE_PACKAGE.equals(sbn.getPackageName())) return;

        android.app.Notification notification = sbn.getNotification();
        if (notification == null) return;

        android.os.Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
        String text = extras.getString(android.app.Notification.EXTRA_TEXT, "");
        String bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT, "");

        String content = (bigText != null && !bigText.isEmpty()) ? bigText : text;

        // Coarse pre-filter: skip if no numeric content at all (e.g. plain chat messages)
        if (!mightBeFinancial(title, content)) return;

        // Dedup: skip if same content seen within 60 seconds (notification update re-fires)
        int hash = (title + content).hashCode();
        long now = System.currentTimeMillis();
        Long lastSeen = recentHashes.get(hash);
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
            AppLog.i("LineExpense", "略過重複通知 (dedup)");
            return;
        }
        recentHashes.put(hash, now);
        recentHashes.entrySet().removeIf(e -> (now - e.getValue()) > DEDUP_WINDOW_MS * 10);

        // Fetch existing categories for better classification
        ExpenseDbHelper db = new ExpenseDbHelper(this);
        List<String> existingCategories = db.getDistinctCategories();

        AppLog.i("LineExpense", "送 Bridge 分析: " + title);

        BridgeClient.analyzeNotification("LINE", title, content, existingCategories,
                (isExpense, amount, currency, category, merchant, description, confidence) -> {
                    if (!isExpense || amount <= 0 || confidence < CONFIDENCE_THRESHOLD) {
                        AppLog.i("LineExpense", String.format(
                                "略過 (is_expense=%b amount=%.0f conf=%.2f)", isExpense, amount, confidence));
                        return;
                    }

                    String finalMerchant = (merchant == null || merchant.isEmpty()) ? title : merchant;
                    String finalDesc = (description == null || description.isEmpty()) ? "LINE 自動記帳" : description;

                    ExpenseDbHelper expDb = new ExpenseDbHelper(LineExpenseListenerService.this);
                    long expenseId = expDb.insert(amount, currency, category, finalMerchant, finalDesc, "LINE", content.trim());

                    AppLog.i("LineExpense", String.format("自動記帳: %s $%.0f [%s] conf=%.2f",
                            finalMerchant, amount, category, confidence));

                    NotificationHelper.sendLineExpenseNotification(
                            LineExpenseListenerService.this,
                            finalMerchant, amount, category, expenseId);
                });
    }

    /**
     * Coarse pre-filter: skip obvious non-financial notifications (plain chat).
     * Only checks for presence of digits + currency/money-related chars.
     */
    private boolean mightBeFinancial(String title, String content) {
        String combined = (title + " " + content).toUpperCase();
        // Must contain at least one digit
        boolean hasDigit = combined.matches(".*\\d.*");
        // Must contain a money-related keyword or symbol
        boolean hasMoneyCue = combined.contains("$") || combined.contains("元")
                || combined.contains("金額") || combined.contains("轉帳")
                || combined.contains("消費") || combined.contains("扣款")
                || combined.contains("付款") || combined.contains("收款")
                || combined.contains("LINE PAY");
        return hasDigit && hasMoneyCue;
    }

    /** Check if this app is currently granted notification listener access */
    public static boolean isEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        ComponentName cn = new ComponentName(context, LineExpenseListenerService.class);
        for (String component : flat.split(":")) {
            try {
                if (cn.equals(ComponentName.unflattenFromString(component))) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
