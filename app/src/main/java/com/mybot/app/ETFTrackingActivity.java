package com.mybot.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 主動 ETF 追蹤圖卡（支援 00981A、00993A 等）。
 * 顯示經理人 14 個交易日內的買賣動向：
 *   - 最新持股
 *   - 14 天彙總：新增 / 出清 / 累計加碼最多 / 累計減碼最多
 *   - 每日 diff 時間軸
 * 通過 Intent extra "etf_code" 指定 ETF（預設 00981A）
 */
public class ETFTrackingActivity extends AppCompatActivity {

    public static final String EXTRA_ETF_CODE = "etf_code";

    private String etfCode = "00981A";
    private String etfName = "統一台股增長";

    private LinearLayout content;
    private TextView statusBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        // Get ETF code from intent
        String code = getIntent().getStringExtra(EXTRA_ETF_CODE);
        if (code != null && !code.isEmpty()) {
            etfCode = code.toUpperCase();
        }

        // Set ETF name based on code
        switch (etfCode) {
            case "00993A":
                etfName = "安聯台灣";
                break;
            case "00981A":
            default:
                etfName = "統一台股增長";
                break;
        }

        AppLog.i("ETFTracking", "開啟主動 ETF 追蹤: " + etfCode);
        buildUI();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "\uD83D\uDCCA " + etfCode + " 經理人動向"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));

        statusBar = new TextView(this);
        statusBar.setText("載入中...");
        statusBar.setTextColor(UIHelper.TEXT_HINT);
        statusBar.setTextSize(12);
        statusBar.setPadding(dp(4), dp(4), dp(4), dp(8));
        content.addView(statusBar);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void loadData() {
        new Thread(() -> {
            try {
                String trackingUrl = "http://127.0.0.1:8765/etf/" + etfCode + "/tracking";
                URL url = new URL(trackingUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> showError("HTTP " + code));
                    return;
                }

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("success", false)) {
                    String err = json.optString("error", "未知錯誤");
                    runOnUiThread(() -> showError(err));
                    return;
                }
                JSONObject data = json.getJSONObject("data");
                runOnUiThread(() -> renderData(data));
            } catch (java.net.ConnectException e) {
                runOnUiThread(() -> showError("Bridge 未啟動，請先啟動本地伺服器"));
            } catch (Exception e) {
                AppLog.e("ETFTracking", "載入失敗: " + e.getMessage());
                runOnUiThread(() -> showError("載入失敗: " + e.getMessage()));
            }
        }).start();
    }

    private void showError(String msg) {
        statusBar.setText("⚠️ " + msg);
        statusBar.setTextColor(UIHelper.ACCENT_RED);
    }

    private void renderData(JSONObject data) {
        content.removeAllViews();

        String etfCode = data.optString("etf_code", "00981A");
        String latestDate = data.optString("latest_date", "-");

        // 1. 摘要列：日期 + NAV
        JSONObject meta = data.optJSONObject("meta");
        TextView header = new TextView(this);
        StringBuilder hb = new StringBuilder();
        hb.append("資料日期：").append(latestDate);
        if (meta != null) {
            double nav = meta.optDouble("nav", 0) / 1e8;
            int n = meta.optInt("stock_n", 0);
            hb.append("　|　淨資產：").append(String.format("%.1f", nav)).append(" 億");
            hb.append("　|　持股 ").append(n).append(" 檔");
        }
        header.setText(hb.toString());
        header.setTextColor(UIHelper.TEXT_SECONDARY);
        header.setTextSize(12);
        header.setPadding(dp(4), 0, 0, dp(10));
        content.addView(header);

        JSONObject rollup = data.optJSONObject("rollup");
        JSONArray dates = rollup != null ? rollup.optJSONArray("dates") : null;
        int dayCount = dates != null ? dates.length() : 0;

        TextView dayInfo = new TextView(this);
        dayInfo.setText("📅 已累積 " + dayCount + " 個交易日（最多保留 14 天）");
        dayInfo.setTextColor(UIHelper.TEXT_HINT);
        dayInfo.setTextSize(11);
        dayInfo.setPadding(dp(4), 0, 0, dp(12));
        content.addView(dayInfo);

        // 2. 14 天彙總四大區塊
        if (rollup != null) {
            JSONArray newPos = rollup.optJSONArray("new_positions");
            JSONArray removed = rollup.optJSONArray("removed_positions");
            JSONArray topAdded = rollup.optJSONArray("top_added");
            JSONArray topReduced = rollup.optJSONArray("top_reduced");

            content.addView(buildSection("\uD83C\uDD95 新建倉",
                    "視窗內第一次出現且仍持有的個股", UIHelper.ACCENT_GREEN, formatNew(newPos)));

            content.addView(buildSection("\uD83D\uDDD1 已出清",
                    "視窗起始時持有但目前已賣光的個股", UIHelper.ACCENT_RED, formatRemoved(removed)));

            content.addView(buildSection("\uD83D\uDCC8 累計加碼最多",
                    "視窗內買進總張數最多的前 15 檔", UIHelper.ACCENT_BLUE, formatTopAdded(topAdded)));

            content.addView(buildSection("\uD83D\uDCC9 累計減碼最多",
                    "視窗內賣出總張數最多的前 10 檔", UIHelper.ACCENT_ORANGE, formatTopReduced(topReduced)));

            // 3. 每日 diff 時間軸
            JSONArray diffs = rollup.optJSONArray("diffs");
            if (diffs != null && diffs.length() > 0) {
                content.addView(sectionHeader("\u23F1 每日動向", UIHelper.ACCENT_PURPLE));
                for (int i = 0; i < diffs.length(); i++) {
                    JSONObject d = diffs.optJSONObject(i);
                    if (d != null) content.addView(buildDayCard(d));
                }
            }
        }

        // 4. 最新完整持股表
        JSONArray holdings = data.optJSONArray("holdings");
        if (holdings != null && holdings.length() > 0) {
            content.addView(sectionHeader("\uD83D\uDCC3 最新完整持股 (" + holdings.length() + " 檔)",
                    UIHelper.TEXT_SECONDARY));
            content.addView(buildHoldingsTable(holdings));
        }

        statusBar.setText("✓ " + latestDate + " 已更新");
        statusBar.setTextColor(UIHelper.ACCENT_GREEN);
        AppLog.i("ETFTracking", "渲染完成: " + dayCount + " 個交易日, "
                + (holdings != null ? holdings.length() : 0) + " 檔持股");
    }

    // ── Formatting helpers ──

    private List<String> formatNew(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null) continue;
            out.add(String.format("%s %s　%,.0f 張　%.2f%%　(%s 進場)",
                    x.optString("code"), x.optString("name"),
                    x.optDouble("lots", 0), x.optDouble("weight", 0),
                    x.optString("first_seen", "-")));
        }
        return out;
    }

    private List<String> formatRemoved(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null) continue;
            out.add(String.format("%s %s　原權重 %.2f%%",
                    x.optString("code"), x.optString("name"),
                    x.optDouble("weight_then", 0)));
        }
        return out;
    }

    private List<String> formatTopAdded(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null) continue;
            out.add(String.format("%s %s　+%,.0f 張　(%d 次加碼)　目前 %.2f%%",
                    x.optString("code"), x.optString("name"),
                    x.optDouble("net_delta_lots", 0),
                    x.optInt("add_count", 0),
                    x.optDouble("current_weight", 0)));
        }
        return out;
    }

    private List<String> formatTopReduced(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null) continue;
            String tag = x.optBoolean("still_held", true) ? "" : " (已出清)";
            out.add(String.format("%s %s　%,.0f 張　(%d 次減碼)%s",
                    x.optString("code"), x.optString("name"),
                    x.optDouble("net_delta_lots", 0),
                    x.optInt("sub_count", 0), tag));
        }
        return out;
    }

    // ── UI builders ──

    private LinearLayout buildSection(String title, String hint, int accent, List<String> lines) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 14, this));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title + "  (" + lines.size() + ")");
        t.setTextColor(accent);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        card.addView(t);

        TextView h = new TextView(this);
        h.setText(hint);
        h.setTextColor(UIHelper.TEXT_HINT);
        h.setTextSize(11);
        h.setPadding(0, dp(2), 0, dp(8));
        card.addView(h);

        if (lines.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("（無）");
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setTextSize(13);
            empty.setPadding(0, dp(2), 0, 0);
            card.addView(empty);
        } else {
            for (String line : lines) {
                TextView row = new TextView(this);
                row.setText("• " + line);
                row.setTextColor(UIHelper.TEXT_PRIMARY);
                row.setTextSize(13);
                row.setPadding(0, dp(3), 0, dp(3));
                card.addView(row);
            }
        }
        return card;
    }

    private TextView sectionHeader(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(14), 0, dp(8));
        return t;
    }

    private LinearLayout buildDayCard(JSONObject diff) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);

        String today = diff.optString("today", "-");
        String prev = diff.optString("prev", "-");
        TextView title = new TextView(this);
        title.setText(today + "（vs " + prev + "）");
        title.setTextColor(UIHelper.ACCENT_PURPLE);
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        addDiffLine(card, "🆕 新建倉", diff.optJSONArray("added"), false, UIHelper.ACCENT_GREEN);
        addDiffLine(card, "🗑 已出清", diff.optJSONArray("removed"), false, UIHelper.ACCENT_RED);
        addDiffLine(card, "📈 加碼", diff.optJSONArray("increased"), true, UIHelper.ACCENT_BLUE);
        addDiffLine(card, "📉 減碼", diff.optJSONArray("decreased"), true, UIHelper.ACCENT_ORANGE);

        return card;
    }

    private void addDiffLine(LinearLayout card, String label, JSONArray arr,
                             boolean showDelta, int color) {
        if (arr == null || arr.length() == 0) return;
        TextView lab = new TextView(this);
        lab.setText(label + " (" + arr.length() + ")");
        lab.setTextColor(color);
        lab.setTextSize(12);
        lab.setTypeface(null, Typeface.BOLD);
        lab.setPadding(0, dp(6), 0, dp(2));
        card.addView(lab);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(x.optString("code")).append(" ").append(x.optString("name"));
            if (showDelta) {
                double d = x.optDouble("delta_lots", 0);
                sb.append("　").append(d >= 0 ? "+" : "");
                sb.append(String.format("%,.0f", d)).append(" 張");
            } else {
                double w = x.optDouble("weight", 0);
                double lots = x.optDouble("lots", 0);
                if (lots > 0) sb.append("　").append(String.format("%,.0f", lots)).append(" 張");
                if (w > 0) sb.append("　").append(String.format("%.2f%%", w));
            }
            TextView row = new TextView(this);
            row.setText("• " + sb.toString());
            row.setTextColor(UIHelper.TEXT_PRIMARY);
            row.setTextSize(12);
            row.setPadding(dp(4), dp(2), 0, dp(2));
            card.addView(row);
        }
    }

    private LinearLayout buildHoldingsTable(JSONArray holdings) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));

        // Header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(makeCell("代號 名稱", 3, UIHelper.TEXT_HINT, true));
        header.addView(makeCell("張數", 2, UIHelper.TEXT_HINT, true));
        header.addView(makeCell("權重%", 2, UIHelper.TEXT_HINT, true));
        box.addView(header);

        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.optJSONObject(i);
            if (h == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            String name = h.optString("code") + " " + h.optString("name");
            double lots = h.optDouble("shares", 0) / 1000;
            String lotsStr = lots > 0 ? String.format("%,.0f", lots) : "-";
            row.addView(makeCell(name, 3, UIHelper.TEXT_PRIMARY, false));
            row.addView(makeCell(lotsStr, 2, UIHelper.TEXT_PRIMARY, false));
            row.addView(makeCell(String.format("%.2f", h.optDouble("weight", 0)),
                    2, UIHelper.TEXT_PRIMARY, false));
            box.addView(row);
        }
        return box;
    }

    private TextView makeCell(String text, int weight, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(12);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(2), dp(4), dp(2), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
