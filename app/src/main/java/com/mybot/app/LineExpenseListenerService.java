package com.mybot.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens to LINE notifications and auto-parses expense messages
 * from bank/payment LINE official accounts.
 *
 * Supported formats:
 *  - LINE Pay
 *  - Major TW banks: E.SUN, Cathay, Taishin, CTBC, Fubon, etc.
 *  - Generic: any LINE message containing 消費/扣款/付款 + $ amount
 */
public class LineExpenseListenerService extends NotificationListenerService {

    private static final String LINE_PACKAGE = "jp.naver.line.android";

    // Dedup: track (amount+merchant) -> timestamp to avoid double-entry
    private final Map<String, Long> recentEntries = new HashMap<>();
    private static final long DEDUP_WINDOW_MS = 60_000; // 60 seconds

    // ── Regex patterns (ordered by specificity) ──

    // LINE Pay: "已付款 NT$1,234 給 全家便利商店"
    private static final Pattern PAT_LINE_PAY =
            Pattern.compile("已付款\\s*NT?\\$?([\\d,]+)\\s*給\\s*(.+)");

    // LINE Pay 收款: "您已成功付款 $1,234 至 MERCHANT"
    private static final Pattern PAT_LINE_PAY2 =
            Pattern.compile("成功付款\\s*\\$?([\\d,]+)\\s*至\\s*(.+)");

    // 玉山: "信用卡消費 NT$1,234 商店：全家便利商店"
    private static final Pattern PAT_ESUN =
            Pattern.compile("消費\\s*NT\\$([\\d,]+)[^\\n]*商店[：:：]\\s*([^\\n]+)");

    // 台新: "於 全家便利商店 消費 NT$1,234"
    private static final Pattern PAT_TAISHIN =
            Pattern.compile("於\\s*([^\\s]+(?:\\s[^\\s]+)?)\\s*消費\\s*NT\\$([\\d,]+)");

    // 國泰/中信: "消費金額：NT$1,234\n消費商店：全家"
    private static final Pattern PAT_CATHAY =
            Pattern.compile("消費金額[：:]\\s*NT?\\$?([\\d,]+)[\\s\\S]*?消費商店[：:]\\s*([^\\n]+)");

    // 富邦: "扣款金額：NT$1,234\n消費商店名稱：全家"
    private static final Pattern PAT_FUBON =
            Pattern.compile("扣款金額[：:]\\s*NT?\\$?([\\d,]+)[\\s\\S]*?商店名稱[：:]\\s*([^\\n]+)");

    // 中信 format: "中國信託 MERCHANT NT$1,234"
    private static final Pattern PAT_CTBC =
            Pattern.compile("([\\u4e00-\\u9fff\\w\\s]{2,20})\\s+NT\\$([\\d,]+)\\s*消費");

    // Generic fallback: any message with "消費/扣款" + NT$ or $ amount
    private static final Pattern PAT_GENERIC_AMOUNT =
            Pattern.compile("(?:消費|扣款|付款)[^\\d$]*NT?\\$([\\d,]+)");

    // ── Category keyword map ──
    private static final String[][] CATEGORY_KEYWORDS = {
        {"餐飲", "麥當勞", "肯德基", "KFC", "星巴克", "STARBUCKS", "COFFEE", "咖啡",
                  "7-ELEVEN", "全家", "OK超商", "萊爾富", "HILIFE", "餐廳", "火鍋",
                  "燒肉", "壽司", "便當", "小吃", "飲料", "茶飲", "珍珠", "奶茶",
                  "鍋", "麵", "飯", "廚", "食品", "PIZZA", "漢堡", "早餐", "MCDONALD"},
        {"交通", "捷運", "MRT", "公車", "BUS", "UBER", "計程車", "TAXI", "高鐵",
                  "台鐵", "統聯", "客運", "加油", "停車", "ETC", "油站", "CPC",
                  "中油", "台灣大道"},
        {"購物", "百貨", "SOGO", "新光三越", "遠東", "全聯", "大潤發", "COSTCO",
                  "好市多", "IKEA", "SHOPEE", "蝦皮", "PCHOME", "MOMO", "博客來",
                  "BOOKS", "超市", "量販", "賣場"},
        {"娛樂", "電影", "影城", "KTV", "遊樂", "NETFLIX", "SPOTIFY", "APPLE",
                  "GOOGLE PLAY", "STEAM", "遊戲", "網飛", "迪士尼", "DISNEY"},
        {"醫療", "藥局", "診所", "醫院", "藥妝", "WATSONS", "屈臣氏", "康是美",
                  "COSMED", "健康", "牙醫", "眼科"},
        {"生活", "水電", "電信", "中華電信", "台灣大哥大", "遠傳", "台哥大",
                  "信義", "住宅", "租屋", "電費", "瓦斯", "自來水"},
        {"服飾", "UNIQLO", "H&M", "ZARA", "優衣庫", "服飾", "衣服", "鞋", "包包",
                  "飾品", "百貨"},
    };

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

        String fullText = (bigText != null && !bigText.isEmpty()) ? bigText : text;
        fullText = (title != null ? title + "\n" : "") + fullText;

        if (!isExpenseMessage(fullText)) return;

        ParsedExpense expense = parseExpense(fullText);
        if (expense == null || expense.amount <= 0) return;

        // Dedup check
        String key = expense.merchant + ":" + (int) expense.amount;
        long now = System.currentTimeMillis();
        Long lastTime = recentEntries.get(key);
        if (lastTime != null && (now - lastTime) < DEDUP_WINDOW_MS) {
            AppLog.i("LineExpense", "略過重複消費通知: " + key);
            return;
        }
        recentEntries.put(key, now);
        // Clean up old entries
        recentEntries.entrySet().removeIf(e -> (now - e.getValue()) > DEDUP_WINDOW_MS * 10);

        // Save to DB
        ExpenseDbHelper db = new ExpenseDbHelper(this);
        long expenseId = db.insert(expense.amount, "TWD", expense.category,
                expense.merchant, expense.description, "LINE", fullText.trim());

        AppLog.i("LineExpense", String.format("自動記帳: %s $%.0f [%s]",
                expense.merchant, expense.amount, expense.category));

        // Push notification — click to open edit screen
        NotificationHelper.sendLineExpenseNotification(this,
                expense.merchant, expense.amount, expense.category, expenseId);
    }

    private boolean isExpenseMessage(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String t = text.toUpperCase();
        return (t.contains("消費") || t.contains("扣款") || t.contains("付款") || t.contains("LINE PAY"))
                && (t.contains("NT$") || t.contains("$") || t.contains("金額"));
    }

    private ParsedExpense parseExpense(String text) {
        ParsedExpense result = null;

        result = tryPattern(PAT_LINE_PAY, text, 1, 2);
        if (result == null) result = tryPattern(PAT_LINE_PAY2, text, 1, 2);
        if (result == null) result = tryPattern(PAT_ESUN, text, 1, 2);
        if (result == null) result = tryPatternMerchantFirst(PAT_TAISHIN, text, 2, 1);
        if (result == null) result = tryPattern(PAT_CATHAY, text, 1, 2);
        if (result == null) result = tryPattern(PAT_FUBON, text, 1, 2);
        if (result == null) result = tryPatternMerchantFirst(PAT_CTBC, text, 2, 1);
        if (result == null) result = tryGeneric(text);

        if (result != null) {
            result.merchant = cleanMerchant(result.merchant);
            result.category = classifyCategory(result.merchant);
            result.description = "LINE 自動記帳";
        }
        return result;
    }

    private ParsedExpense tryPattern(Pattern pat, String text, int amtGroup, int merchantGroup) {
        Matcher m = pat.matcher(text);
        if (!m.find()) return null;
        try {
            double amount = parseAmount(m.group(amtGroup));
            String merchant = m.groupCount() >= merchantGroup ? m.group(merchantGroup).trim() : "";
            if (amount <= 0) return null;
            ParsedExpense e = new ParsedExpense();
            e.amount = amount;
            e.merchant = merchant;
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private ParsedExpense tryPatternMerchantFirst(Pattern pat, String text, int amtGroup, int merchantGroup) {
        return tryPattern(pat, text, amtGroup, merchantGroup);
    }

    private ParsedExpense tryGeneric(String text) {
        Matcher m = PAT_GENERIC_AMOUNT.matcher(text);
        if (!m.find()) return null;
        double amount = parseAmount(m.group(1));
        if (amount <= 0) return null;
        ParsedExpense e = new ParsedExpense();
        e.amount = amount;
        e.merchant = "";
        return e;
    }

    private double parseAmount(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String cleanMerchant(String merchant) {
        if (merchant == null) return "";
        // Remove trailing punctuation or extra whitespace
        return merchant.replaceAll("[。，、\\s]+$", "").trim();
    }

    private String classifyCategory(String merchant) {
        if (merchant == null || merchant.isEmpty()) return "未分類";
        String upper = merchant.toUpperCase();
        for (String[] entry : CATEGORY_KEYWORDS) {
            String category = entry[0];
            for (int i = 1; i < entry.length; i++) {
                if (upper.contains(entry[i].toUpperCase())) {
                    return category;
                }
            }
        }
        return "未分類";
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

    private static class ParsedExpense {
        double amount;
        String merchant = "";
        String category = "";
        String description = "";
    }
}
