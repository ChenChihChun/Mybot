package com.mybot.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private LinearLayout progressCard;
    private TextView countLabel;
    private TextView stageText;
    private TextView stepsText;
    private TextView elapsedText;
    private Button refreshBtn;
    private String selectedStatus = null; // null = all
    private List<JSONObject> proposals = new ArrayList<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private long researchStartTime;

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
        checkResearchStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
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
        AppLog.i("Evolution", "開啟研究方向選擇");
        btn.setEnabled(false);
        // Fetch topics then show dialog
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/topics");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        Toast.makeText(this, "無法取得研究方向", Toast.LENGTH_SHORT).show();
                    });
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
                JSONObject data = json.optJSONObject("data");
                JSONArray topicsArr = data != null ? data.optJSONArray("topics") : null;
                int todayIndex = data != null ? data.optInt("today_index", 0) : 0;

                if (topicsArr == null || topicsArr.length() == 0) {
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        Toast.makeText(this, "無可用研究方向", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                List<String> topics = new ArrayList<>();
                for (int i = 0; i < topicsArr.length(); i++) {
                    topics.add(topicsArr.getString(i));
                }

                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    showTopicDialog(topics, todayIndex);
                });
            } catch (Exception e) {
                AppLog.e("Evolution", "取得研究方向失敗: " + e.getMessage());
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    Toast.makeText(this, "取得研究方向失敗", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showTopicDialog(List<String> topics, int todayIndex) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(UIHelper.BG_PRIMARY);
        int pad = dp(20);
        layout.setPadding(pad, dp(12), pad, pad);

        TextView hint = new TextView(this);
        hint.setText("今日預設方向已標記 ★");
        hint.setTextColor(UIHelper.TEXT_HINT);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(8));
        layout.addView(hint);

        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < topics.size(); i++) {
            RadioButton rb = new RadioButton(this);
            String label = topics.get(i);
            if (label.length() > 50) label = label.substring(0, 50) + "...";
            rb.setText((i == todayIndex ? "★ " : "") + label);
            rb.setTextColor(UIHelper.TEXT_PRIMARY);
            rb.setTextSize(13);
            rb.setId(i);
            rb.setPadding(dp(4), dp(6), dp(4), dp(6));
            if (i == todayIndex) rb.setChecked(true);
            radioGroup.addView(rb);
        }
        // Custom topic option
        RadioButton customRb = new RadioButton(this);
        customRb.setText("自訂方向...");
        customRb.setTextColor(UIHelper.ACCENT_BLUE);
        customRb.setTextSize(13);
        customRb.setId(topics.size());
        customRb.setPadding(dp(4), dp(6), dp(4), dp(6));
        radioGroup.addView(customRb);

        layout.addView(radioGroup);

        EditText customInput = new EditText(this);
        customInput.setHint("輸入自訂研究方向...");
        customInput.setTextColor(UIHelper.TEXT_PRIMARY);
        customInput.setHintTextColor(UIHelper.TEXT_HINT);
        customInput.setTextSize(14);
        customInput.setInputType(InputType.TYPE_CLASS_TEXT);
        customInput.setVisibility(View.GONE);
        customInput.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.TEXT_HINT, 8, 1, this));
        customInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, dp(8), 0, 0);
        customInput.setLayoutParams(inputLp);
        layout.addView(customInput);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            customInput.setVisibility(checkedId == topics.size() ? View.VISIBLE : View.GONE);
        });

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("選擇研究方向")
                .setView(scroll)
                .setPositiveButton("開始研究", (d, w) -> {
                    int selected = radioGroup.getCheckedRadioButtonId();
                    String topic;
                    if (selected == topics.size()) {
                        topic = customInput.getText().toString().trim();
                        if (topic.isEmpty()) {
                            Toast.makeText(this, "請輸入自訂方向", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else if (selected >= 0 && selected < topics.size()) {
                        topic = topics.get(selected);
                    } else {
                        topic = topics.get(todayIndex);
                    }
                    startResearch(topic);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startResearch(String topic) {
        AppLog.i("Evolution", "開始研究: " + topic);
        refreshBtn.setEnabled(false);
        refreshBtn.setText("研究中...");

        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/refresh");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("topic", topic);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();

                // Read response body for 409 message
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 200) {
                        AppLog.i("Evolution", "研究已觸發");
                        researchStartTime = System.currentTimeMillis();
                        showProgressCard(topic);
                        startPolling();
                    } else if (code == 409) {
                        // Already running — show progress
                        AppLog.i("Evolution", "研究已在進行中，顯示進度");
                        researchStartTime = System.currentTimeMillis();
                        showProgressCard(topic);
                        startPolling();
                    } else {
                        refreshBtn.setEnabled(true);
                        refreshBtn.setText("\uD83D\uDD2C 研究新方案");
                        Toast.makeText(this, "觸發失敗: HTTP " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                AppLog.e("Evolution", "觸發研究失敗: " + e.getMessage());
                runOnUiThread(() -> {
                    refreshBtn.setEnabled(true);
                    refreshBtn.setText("\uD83D\uDD2C 研究新方案");
                    Toast.makeText(this, "觸發失敗，請確認 Bridge 運行中", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showProgressCard(String topic) {
        if (progressCard != null) progressCard.setVisibility(View.VISIBLE);
        stageText.setText("初始化中...");
        stepsText.setText("○ Session A  ○ Session B  ○ 共識比對  ○ 完成");
        elapsedText.setText("已進行 0:00");
        if (topic != null && !topic.isEmpty()) {
            String shortTopic = topic.length() > 40 ? topic.substring(0, 40) + "..." : topic;
            stageText.setText("研究方向: " + shortTopic);
        }
    }

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                updateElapsed();
                fetchStatus();
                pollHandler.postDelayed(this, 5000);
            }
        };
        pollHandler.postDelayed(pollRunnable, 2000); // first poll after 2s
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void updateElapsed() {
        if (researchStartTime <= 0) return;
        long elapsed = (System.currentTimeMillis() - researchStartTime) / 1000;
        long min = elapsed / 60;
        long sec = elapsed % 60;
        if (elapsedText != null) {
            elapsedText.setText(String.format("已進行 %d:%02d", min, sec));
        }
    }

    private void fetchStatus() {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/refresh/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                if (code != 200) return;

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                String stage = data.optString("stage", "idle");
                String detail = data.optString("detail", "");

                runOnUiThread(() -> updateProgressUI(stage, detail));
            } catch (Exception e) {
                // Silently ignore polling errors
            }
        }).start();
    }

    private void updateProgressUI(String stage, String detail) {
        if (stageText == null) return;

        String stageLabel;
        String steps;
        String estimate = "";

        switch (stage) {
            case "starting":
                stageLabel = "初始化中...";
                steps = "○ Session A  ○ Session B  ○ 共識比對  ○ 完成";
                estimate = "預估 ~5 分鐘";
                break;
            case "session_a":
                stageLabel = detail.isEmpty() ? "第一組搜尋中..." : detail;
                steps = "● Session A  ○ Session B  ○ 共識比對  ○ 完成";
                estimate = "預估剩餘 ~4 分鐘";
                break;
            case "session_b":
                stageLabel = detail.isEmpty() ? "第二組搜尋中..." : detail;
                steps = "✓ Session A  ● Session B  ○ 共識比對  ○ 完成";
                estimate = "預估剩餘 ~2 分鐘";
                break;
            case "judging":
                stageLabel = detail.isEmpty() ? "共識比對中..." : detail;
                steps = "✓ Session A  ✓ Session B  ● 共識比對  ○ 完成";
                estimate = "即將完成";
                break;
            case "saving":
                stageLabel = "儲存結果中...";
                steps = "✓ Session A  ✓ Session B  ✓ 共識比對  ● 儲存";
                estimate = "即將完成";
                break;
            case "done":
                stageLabel = detail.isEmpty() ? "研究完成！" : detail;
                steps = "✓ Session A  ✓ Session B  ✓ 共識比對  ✓ 完成";
                stopPolling();
                if (progressCard != null) {
                    pollHandler.postDelayed(() -> {
                        progressCard.setVisibility(View.GONE);
                        refreshBtn.setEnabled(true);
                        refreshBtn.setText("\uD83D\uDD2C 研究新方案");
                    }, 3000);
                }
                syncFromBridge();
                Toast.makeText(this, "研究完成！已載入新提案", Toast.LENGTH_SHORT).show();
                return;
            case "error":
                stageLabel = detail.isEmpty() ? "研究失敗" : detail;
                steps = "✗ 發生錯誤";
                stopPolling();
                if (progressCard != null) {
                    pollHandler.postDelayed(() -> {
                        progressCard.setVisibility(View.GONE);
                        refreshBtn.setEnabled(true);
                        refreshBtn.setText("\uD83D\uDD2C 研究新方案");
                    }, 3000);
                }
                Toast.makeText(this, "研究失敗: " + detail, Toast.LENGTH_LONG).show();
                return;
            case "idle":
            default:
                // Not running, hide progress
                if (progressCard != null) progressCard.setVisibility(View.GONE);
                refreshBtn.setEnabled(true);
                refreshBtn.setText("\uD83D\uDD2C 研究新方案");
                stopPolling();
                return;
        }

        stageText.setText(stageLabel);
        stepsText.setText(steps);
        if (!estimate.isEmpty()) {
            elapsedText.setText(elapsedText.getText() + "  " + estimate);
        }
    }

    private void checkResearchStatus() {
        new Thread(() -> {
            try {
                URL url = new URL("http://127.0.0.1:8765/evolution/refresh/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                if (code != 200) return;

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                String stage = data.optString("stage", "idle");
                if (!"idle".equals(stage) && !"done".equals(stage) && !"error".equals(stage)) {
                    // Research is in progress, show progress card and start polling
                    String startedAt = data.optString("started_at", "");
                    runOnUiThread(() -> {
                        researchStartTime = System.currentTimeMillis();
                        // Try to compute from started_at if available
                        if (!startedAt.isEmpty()) {
                            try {
                                // Approximate: parse ISO and compute diff
                                long approx = System.currentTimeMillis() - 60000; // fallback
                                researchStartTime = approx;
                            } catch (Exception ignored) {}
                        }
                        showProgressCard(data.optString("topic", ""));
                        refreshBtn.setEnabled(false);
                        refreshBtn.setText("研究中...");
                        startPolling();
                        updateProgressUI(stage, data.optString("detail", ""));
                    });
                }
            } catch (Exception e) {
                // Silently ignore
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
        refreshBtn = UIHelper.primaryButton(this, "\uD83D\uDD2C 研究新方案");
        refreshBtn.setOnClickListener(v -> triggerRefresh(refreshBtn));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(4), 0, dp(8));
        refreshBtn.setLayoutParams(btnLp);
        content.addView(refreshBtn);

        // Progress card (hidden by default)
        progressCard = new LinearLayout(this);
        progressCard.setOrientation(LinearLayout.VERTICAL);
        progressCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 14, this));
        progressCard.setElevation(dp(3));
        int pPad = dp(14);
        progressCard.setPadding(pPad, dp(12), pPad, dp(12));
        progressCard.setVisibility(View.GONE);

        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pcLp.setMargins(0, 0, 0, dp(10));
        progressCard.setLayoutParams(pcLp);

        // Progress header
        TextView progressTitle = new TextView(this);
        progressTitle.setText("\uD83D\uDD2C 研究進行中");
        progressTitle.setTextColor(UIHelper.ACCENT_BLUE);
        progressTitle.setTextSize(14);
        progressTitle.setTypeface(null, Typeface.BOLD);
        progressCard.addView(progressTitle);

        stageText = new TextView(this);
        stageText.setTextColor(UIHelper.TEXT_PRIMARY);
        stageText.setTextSize(13);
        stageText.setPadding(0, dp(6), 0, dp(4));
        progressCard.addView(stageText);

        stepsText = new TextView(this);
        stepsText.setTextColor(UIHelper.TEXT_SECONDARY);
        stepsText.setTextSize(12);
        stepsText.setPadding(0, dp(2), 0, dp(4));
        progressCard.addView(stepsText);

        elapsedText = new TextView(this);
        elapsedText.setTextColor(UIHelper.TEXT_HINT);
        elapsedText.setTextSize(11);
        progressCard.addView(elapsedText);

        content.addView(progressCard);

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
        titleView.setTextIsSelectable(true);
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

        // Copy-all button
        Button copyBtn = new Button(this);
        copyBtn.setText("\uD83D\uDCCB 複製全部內容");
        copyBtn.setTextColor(Color.WHITE);
        copyBtn.setAllCaps(false);
        copyBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_BLUE, 12, this));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cblp.topMargin = dp(16);
        copyBtn.setLayoutParams(cblp);
        copyBtn.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append(what).append("\n\n");
            if (!why.isEmpty()) sb.append("【為什麼】\n").append(why).append("\n\n");
            if (!how.isEmpty()) sb.append("【實作方式】\n").append(how).append("\n\n");
            if (!implLog.isEmpty() && ("done".equals(status) || "failed".equals(status))) {
                sb.append("【實作記錄】\n").append(implLog).append("\n");
            }
            copyToClipboard("提案 #" + p.optInt("id"), sb.toString().trim());
        });
        layout.addView(copyBtn);

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
        contentView.setTextIsSelectable(true);
        parent.addView(contentView);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show();
        AppLog.i("Evolution", "複製提案內容: " + label);
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
