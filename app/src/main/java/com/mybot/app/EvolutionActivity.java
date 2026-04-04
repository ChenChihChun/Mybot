package com.mybot.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EvolutionActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private LinearLayout chipContainer;
    private TextView countLabel;
    private String selectedStatus = null; // null = all
    private List<JSONObject> proposals = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i("Evolution", "開啟自我演化");
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncFromBridge();
    }

    private void syncFromBridge() {
        new Thread(() -> {
            try {
                String urlStr = "http://127.0.0.1:8765/evolution/proposals";
                if (selectedStatus != null) {
                    urlStr += "?status=" + selectedStatus;
                }
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> Toast.makeText(this, "Bridge 連線失敗", Toast.LENGTH_SHORT).show());
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
                if (!json.optBoolean("success", false)) return;

                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                JSONArray arr = data.optJSONArray("proposals");
                List<JSONObject> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(arr.getJSONObject(i));
                    }
                }

                AppLog.i("Evolution", "載入 " + list.size() + " 個提案");
                runOnUiThread(() -> {
                    proposals = list;
                    refreshList();
                });
            } catch (java.net.ConnectException e) {
                // Bridge not running
            } catch (Exception e) {
                AppLog.w("Evolution", "同步失敗: " + e.getMessage());
            }
        }).start();
    }

    private void triggerRefresh(Button btn) {
        btn.setEnabled(false);
        btn.setText("研究中...");
        AppLog.i("Evolution", "手動觸發研究新方案");
        Toast.makeText(this, "開始研究新方案，約需 1-2 分鐘", Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/refresh");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write("{}".getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                conn.disconnect();

                AppLog.i("Evolution", "研究觸發結果: HTTP " + code);
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText("\uD83D\uDD2C 研究新方案");
                    if (code == 200) {
                        Toast.makeText(this, "研究已啟動，稍後重新整理即可看到新提案", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "觸發失敗: HTTP " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                AppLog.e("Evolution", "觸發研究失敗: " + e.getMessage());
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText("\uD83D\uDD2C 研究新方案");
                    Toast.makeText(this, "觸發失敗，請確認 Bridge 運行中", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);
        root.addView(UIHelper.topBar(this, "\uD83E\uDDEC 自我演化"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        // Status filter chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(4), 0, dp(8));

        chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipContainer.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chipContainer);
        content.addView(chipScroll);

        buildChips();

        // Research button
        Button refreshBtn = UIHelper.primaryButton(this, "\uD83D\uDD2C 研究新方案");
        refreshBtn.setOnClickListener(v -> triggerRefresh(refreshBtn));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(4), 0, dp(8));
        refreshBtn.setLayoutParams(btnLp);
        content.addView(refreshBtn);

        // Count label
        countLabel = new TextView(this);
        countLabel.setTextSize(12);
        countLabel.setTextColor(UIHelper.TEXT_HINT);
        countLabel.setPadding(dp(4), 0, 0, dp(8));
        content.addView(countLabel);

        // List container
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void buildChips() {
        chipContainer.removeAllViews();
        String[][] filters = {
                {null, "全部"},
                {"pending", "待審核"},
                {"approved", "已通過"},
                {"implementing", "實作中"},
                {"done", "完成"},
                {"failed", "失敗"},
                {"rejected", "已拒絕"},
        };

        for (String[] f : filters) {
            String status = f[0];
            String label = f[1];
            boolean selected = (selectedStatus == null && status == null)
                    || (selectedStatus != null && selectedStatus.equals(status));

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

            if (selected) {
                chip.setTextColor(Color.WHITE);
                chip.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 16, this));
            } else {
                chip.setTextColor(UIHelper.TEXT_SECONDARY);
                chip.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 16, 1, this));
            }

            int h = dp(14);
            int v = dp(6);
            chip.setPadding(h, v, h, v);
            chip.setOnClickListener(view -> {
                selectedStatus = status;
                buildChips();
                syncFromBridge();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(lp);
            chipContainer.addView(chip);
        }
    }

    private void refreshList() {
        listContainer.removeAllViews();
        countLabel.setText(proposals.size() + " 個提案");

        if (proposals.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚無提案\n系統每日 02:00 自動探索新方案");
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            listContainer.addView(empty);
            return;
        }

        for (JSONObject p : proposals) {
            listContainer.addView(buildProposalCard(p));
        }
    }

    private LinearLayout buildProposalCard(JSONObject p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 14, this));
        card.setElevation(dp(3));
        int pad = dp(14);
        card.setPadding(pad, dp(12), pad, dp(12));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardLp);

        String status = p.optString("status", "pending");
        String category = p.optString("category", "feature");
        String effort = p.optString("effort", "medium");
        String what = p.optString("what", "");
        String why = p.optString("why", "");

        // Header row: category + effort + status badges
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        headerRow.addView(UIHelper.statusBadge(this, category, getCategoryColor(category)));
        addBadgeSpacer(headerRow);
        headerRow.addView(UIHelper.statusBadge(this, effort, getEffortColor(effort)));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        headerRow.addView(spacer);

        headerRow.addView(UIHelper.statusBadge(this, getStatusLabel(status), getStatusColor(status)));
        card.addView(headerRow);

        // Title (what)
        TextView titleView = new TextView(this);
        titleView.setText(what);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTextSize(15);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, dp(8), 0, dp(4));
        titleView.setMaxLines(2);
        card.addView(titleView);

        // Why (subtitle)
        if (!why.isEmpty()) {
            TextView whyView = new TextView(this);
            String preview = why.length() > 100 ? why.substring(0, 100) + "..." : why;
            whyView.setText(preview);
            whyView.setTextColor(UIHelper.TEXT_SECONDARY);
            whyView.setTextSize(13);
            whyView.setLineSpacing(dp(2), 1f);
            whyView.setMaxLines(2);
            card.addView(whyView);
        }

        // Action row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(8), 0, 0);

        // Details button
        TextView detailBtn = new TextView(this);
        detailBtn.setText("詳情");
        detailBtn.setTextColor(UIHelper.ACCENT_BLUE);
        detailBtn.setTextSize(12);
        detailBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        detailBtn.setOnClickListener(v -> showDetailDialog(p));
        actionRow.addView(detailBtn);

        // Approve/reject buttons only for pending
        if ("pending".equals(status)) {
            TextView approveBtn = new TextView(this);
            approveBtn.setText("同意");
            approveBtn.setTextColor(UIHelper.ACCENT_GREEN);
            approveBtn.setTextSize(12);
            approveBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            approveBtn.setOnClickListener(v -> confirmApprove(p));
            actionRow.addView(approveBtn);

            TextView rejectBtn = new TextView(this);
            rejectBtn.setText("拒絕");
            rejectBtn.setTextColor(UIHelper.ACCENT_RED);
            rejectBtn.setTextSize(12);
            rejectBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            rejectBtn.setOnClickListener(v -> updateProposalStatus(p.optInt("id"), "rejected"));
            actionRow.addView(rejectBtn);
        }

        // Retry button for rejected/failed
        if ("rejected".equals(status) || "failed".equals(status)) {
            TextView retryBtn = new TextView(this);
            retryBtn.setText("重試");
            retryBtn.setTextColor(UIHelper.ACCENT_ORANGE);
            retryBtn.setTextSize(12);
            retryBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            retryBtn.setOnClickListener(v -> confirmApprove(p));
            actionRow.addView(retryBtn);
        }

        // Delete button (not for implementing)
        if (!"implementing".equals(status)) {
            TextView deleteBtn = new TextView(this);
            deleteBtn.setText("刪除");
            deleteBtn.setTextColor(UIHelper.ACCENT_RED);
            deleteBtn.setTextSize(12);
            deleteBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            deleteBtn.setOnClickListener(v -> confirmDelete(p));
            actionRow.addView(deleteBtn);
        }

        card.addView(actionRow);
        return card;
    }

    private void confirmDelete(JSONObject p) {
        new AlertDialog.Builder(this)
                .setTitle("刪除提案")
                .setMessage("確定要刪除「" + p.optString("what", "") + "」？")
                .setPositiveButton("刪除", (d, w) -> deleteProposal(p.optInt("id")))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteProposal(int id) {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/proposals/delete");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                JSONObject body = new JSONObject();
                body.put("id", id);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                conn.getResponseCode();
                conn.disconnect();

                AppLog.i("Evolution", "刪除提案 #" + id);
                runOnUiThread(() -> {
                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                    syncFromBridge();
                });
            } catch (Exception e) {
                AppLog.e("Evolution", "刪除失敗: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "刪除失敗", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmApprove(JSONObject p) {
        new AlertDialog.Builder(this)
                .setTitle("確認同意")
                .setMessage("同意後將自動開始實作：\n\n" + p.optString("what", ""))
                .setPositiveButton("同意實作", (d, w) -> updateProposalStatus(p.optInt("id"), "approved"))
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateProposalStatus(int id, String status) {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/proposals/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);

                JSONObject body = new JSONObject();
                body.put("id", id);
                body.put("status", status);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                conn.disconnect();

                String msg = "approved".equals(status) ? "已同意，開始實作..." : "已拒絕";
                AppLog.i("Evolution", (("approved".equals(status)) ? "同意" : "拒絕") + "提案 #" + id);

                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    syncFromBridge();
                });
            } catch (Exception e) {
                AppLog.e("Evolution", "更新狀態失敗: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "操作失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showDetailDialog(JSONObject p) {
        String what = p.optString("what", "");
        String why = p.optString("why", "");
        String how = p.optString("how", "");
        String status = p.optString("status", "pending");
        String category = p.optString("category", "");
        String effort = p.optString("effort", "");
        String implLog = p.optString("impl_log", "");
        String date = p.optString("date", "");

        AppLog.i("Evolution", "查看提案詳情: " + what);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(UIHelper.BG_PRIMARY);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        // Badges row
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
        badgeRow.addView(UIHelper.statusBadge(this, category, getCategoryColor(category)));
        addBadgeSpacer(badgeRow);
        badgeRow.addView(UIHelper.statusBadge(this, effort, getEffortColor(effort)));
        addBadgeSpacer(badgeRow);
        badgeRow.addView(UIHelper.statusBadge(this, getStatusLabel(status), getStatusColor(status)));
        layout.addView(badgeRow);

        // What (title)
        TextView titleView = new TextView(this);
        titleView.setText(what);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, dp(12), 0, dp(8));
        layout.addView(titleView);

        // Why section
        if (!why.isEmpty()) {
            addSection(layout, "\uD83D\uDCA1 為什麼", why, UIHelper.ACCENT_ORANGE);
        }

        // How section
        if (!how.isEmpty()) {
            addSection(layout, "\uD83D\uDD27 實作方式", how, UIHelper.ACCENT_BLUE);
        }

        // Implementation log (for done/failed)
        if (!implLog.isEmpty() && ("done".equals(status) || "failed".equals(status))) {
            addSection(layout, "\uD83D\uDCDD 實作記錄", implLog, UIHelper.TEXT_SECONDARY);
        }

        // Date
        if (!date.isEmpty()) {
            TextView dateView = new TextView(this);
            dateView.setText("提案日期: " + date);
            dateView.setTextColor(UIHelper.TEXT_HINT);
            dateView.setTextSize(11);
            dateView.setPadding(0, dp(12), 0, 0);
            layout.addView(dateView);
        }

        scroll.addView(layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scroll);

        if ("pending".equals(status)) {
            builder.setPositiveButton("同意實作", (d, w) -> {
                updateProposalStatus(p.optInt("id"), "approved");
            });
            builder.setNegativeButton("拒絕", (d, w) -> {
                updateProposalStatus(p.optInt("id"), "rejected");
            });
            builder.setNeutralButton("關閉", null);
        } else if ("rejected".equals(status) || "failed".equals(status)) {
            builder.setPositiveButton("重試", (d, w) -> {
                updateProposalStatus(p.optInt("id"), "approved");
            });
            builder.setNegativeButton("關閉", null);
        } else {
            builder.setPositiveButton("關閉", null);
        }

        builder.show();
    }

    private void addSection(LinearLayout parent, String label, String content, int labelColor) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(labelColor);
        labelView.setTextSize(13);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(0, dp(12), 0, dp(4));
        parent.addView(labelView);

        TextView contentView = new TextView(this);
        contentView.setText(content);
        contentView.setTextColor(UIHelper.TEXT_PRIMARY);
        contentView.setTextSize(14);
        contentView.setLineSpacing(dp(3), 1f);
        parent.addView(contentView);
    }

    private void addBadgeSpacer(LinearLayout row) {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
        row.addView(s);
    }

    private int getCategoryColor(String category) {
        if (category == null) return UIHelper.TEXT_SECONDARY;
        switch (category) {
            case "automation": return UIHelper.ACCENT_BLUE;
            case "feature": return UIHelper.ACCENT_GREEN;
            case "optimization": return UIHelper.ACCENT_ORANGE;
            case "security": return UIHelper.ACCENT_RED;
            case "ux": return UIHelper.ACCENT_PURPLE;
            default: return UIHelper.TEXT_SECONDARY;
        }
    }

    private int getEffortColor(String effort) {
        if (effort == null) return UIHelper.TEXT_SECONDARY;
        switch (effort) {
            case "low": return UIHelper.ACCENT_GREEN;
            case "medium": return UIHelper.ACCENT_ORANGE;
            case "high": return UIHelper.ACCENT_RED;
            default: return UIHelper.TEXT_SECONDARY;
        }
    }

    private int getStatusColor(String status) {
        if (status == null) return UIHelper.TEXT_SECONDARY;
        switch (status) {
            case "pending": return UIHelper.ACCENT_ORANGE;
            case "approved": return UIHelper.ACCENT_BLUE;
            case "implementing": return UIHelper.ACCENT_PURPLE;
            case "done": return UIHelper.ACCENT_GREEN;
            case "rejected": return Color.parseColor("#78909C");
            case "failed": return UIHelper.ACCENT_RED;
            default: return UIHelper.TEXT_SECONDARY;
        }
    }

    private String getStatusLabel(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "pending": return "待審核";
            case "approved": return "已通過";
            case "implementing": return "實作中";
            case "done": return "完成";
            case "rejected": return "已拒絕";
            case "failed": return "失敗";
            default: return status;
        }
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
