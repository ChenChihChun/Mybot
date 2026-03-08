package com.mybot.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class RemoteDevActivity extends AppCompatActivity {

    private static final String PREFS = "remote_dev_prefs";
    private static final String KEY_PROJECTS = "projects";
    private static final String KEY_SELECTED = "selected_project";

    private EditText taskInput;
    private TextView projectLabel;
    private TextView statusText;
    private TextView resultText;
    private Button runBtn;
    private Button newSessionBtn;
    private String selectedProject = "";
    private String[] projects = {};
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i("RemoteDev", "頁面開啟");

        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        loadProjects();

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "遠端開發");
        root.addView(topBar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UIHelper.dp(this, 16);
        content.setPadding(pad, pad, pad, pad);

        // Project selector
        content.addView(UIHelper.sectionHeader(this, "PROJECT"));

        LinearLayout projectRow = new LinearLayout(this);
        projectRow.setOrientation(LinearLayout.HORIZONTAL);
        projectRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams projectRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        projectRowLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 12));
        projectRow.setLayoutParams(projectRowLp);

        projectLabel = new TextView(this);
        projectLabel.setTextSize(14);
        projectLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        projectLabel.setTypeface(Typeface.MONOSPACE);
        projectLabel.setSingleLine(true);
        updateProjectLabel();
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        projectLabel.setLayoutParams(labelLp);
        projectLabel.setOnClickListener(v -> showProjectPicker());

        TextView editBtn = new TextView(this);
        editBtn.setText("選擇");
        editBtn.setTextSize(13);
        editBtn.setTextColor(UIHelper.ACCENT_BLUE);
        editBtn.setPadding(UIHelper.dp(this, 12), UIHelper.dp(this, 8),
                UIHelper.dp(this, 4), UIHelper.dp(this, 8));
        editBtn.setOnClickListener(v -> showProjectPicker());

        TextView manageBtn = new TextView(this);
        manageBtn.setText("管理");
        manageBtn.setTextSize(13);
        manageBtn.setTextColor(UIHelper.TEXT_SECONDARY);
        manageBtn.setPadding(UIHelper.dp(this, 8), UIHelper.dp(this, 8),
                UIHelper.dp(this, 4), UIHelper.dp(this, 8));
        manageBtn.setOnClickListener(v -> showManageProjects());

        projectRow.addView(projectLabel);
        projectRow.addView(editBtn);
        projectRow.addView(manageBtn);
        content.addView(projectRow);

        // Task input
        content.addView(UIHelper.sectionHeader(this, "TASK"));

        taskInput = new EditText(this);
        taskInput.setHint("輸入任務描述...\n例: 修改 login 頁面加上記住密碼功能");
        taskInput.setMinLines(4);
        taskInput.setMaxLines(10);
        taskInput.setGravity(Gravity.TOP | Gravity.START);
        taskInput.setTextSize(14);
        taskInput.setTextColor(UIHelper.TEXT_PRIMARY);
        taskInput.setHintTextColor(UIHelper.TEXT_HINT);
        taskInput.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT, Color.parseColor("#2E4050"), 14, 1, this));
        int inputPad = UIHelper.dp(this, 14);
        taskInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        LinearLayout.LayoutParams taskLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        taskLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 12));
        taskInput.setLayoutParams(taskLp);
        content.addView(taskInput);

        // Buttons row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        btnRow.setLayoutParams(btnRowLp);

        // Run button
        runBtn = UIHelper.primaryButton(this, "執行");
        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(0, UIHelper.dp(this, 48), 1);
        runLp.setMargins(0, 0, UIHelper.dp(this, 8), 0);
        runBtn.setLayoutParams(runLp);
        runBtn.setOnClickListener(v -> executeTask());

        // /new session button
        newSessionBtn = UIHelper.outlineButton(this, "/new 重置");
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(0, UIHelper.dp(this, 48), 1);
        newLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        newSessionBtn.setLayoutParams(newLp);
        newSessionBtn.setOnClickListener(v -> resetSession());

        btnRow.addView(runBtn);
        btnRow.addView(newSessionBtn);
        content.addView(btnRow);

        // Status
        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_HINT);
        statusText.setPadding(0, 0, 0, UIHelper.dp(this, 8));
        content.addView(statusText);

        // Result area
        content.addView(UIHelper.sectionHeader(this, "RESULT"));

        resultText = new TextView(this);
        resultText.setTextSize(12);
        resultText.setTextColor(UIHelper.TEXT_PRIMARY);
        resultText.setTypeface(Typeface.MONOSPACE);
        resultText.setBackground(UIHelper.roundRectStroke(
                Color.parseColor("#0D1B2A"), Color.parseColor("#1E3040"), 12, 1, this));
        int rPad = UIHelper.dp(this, 12);
        resultText.setPadding(rPad, rPad, rPad, rPad);
        resultText.setMovementMethod(ScrollingMovementMethod.getInstance());
        resultText.setHorizontallyScrolling(false);
        resultText.setText("等待執行...");
        resultText.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 16));
        resultText.setLayoutParams(resultLp);
        content.addView(resultText);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void loadProjects() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_PROJECTS, "");
        if (!raw.isEmpty()) {
            projects = raw.split("\n");
        } else {
            projects = new String[0];
        }
        selectedProject = prefs.getString(KEY_SELECTED, "");
    }

    private void saveProjects() {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString(KEY_PROJECTS, String.join("\n", projects));
        editor.putString(KEY_SELECTED, selectedProject);
        editor.apply();
    }

    private void updateProjectLabel() {
        if (selectedProject.isEmpty()) {
            projectLabel.setText("(未選擇 — 使用 Bot 預設)");
            projectLabel.setTextColor(UIHelper.TEXT_HINT);
        } else {
            projectLabel.setText(selectedProject);
            projectLabel.setTextColor(UIHelper.TEXT_PRIMARY);
        }
    }

    private void showProjectPicker() {
        if (projects.length == 0) {
            showManageProjects();
            return;
        }

        String[] items = new String[projects.length + 1];
        items[0] = "(Bot 預設專案)";
        System.arraycopy(projects, 0, items, 1, projects.length);

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("選擇專案")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        selectedProject = "";
                    } else {
                        selectedProject = projects[which - 1];
                    }
                    saveProjects();
                    updateProjectLabel();
                    AppLog.i("RemoteDev", "選擇專案: " + (selectedProject.isEmpty() ? "(預設)" : selectedProject));
                })
                .show();
    }

    private void showManageProjects() {
        EditText input = new EditText(this);
        input.setHint("每行一個專案路徑\n例: C:\\Users\\me\\projects\\web-app");
        input.setMinLines(5);
        input.setTextSize(13);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(UIHelper.TEXT_PRIMARY);
        input.setHintTextColor(UIHelper.TEXT_HINT);
        input.setBackgroundColor(UIHelper.BG_INPUT);
        int p = UIHelper.dp(this, 12);
        input.setPadding(p, p, p, p);
        if (projects.length > 0) {
            input.setText(String.join("\n", projects));
        }

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("管理專案路徑")
                .setMessage("輸入 Windows 工作電腦上的專案路徑，每行一個")
                .setView(input)
                .setPositiveButton("儲存", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        projects = new String[0];
                    } else {
                        String[] lines = text.split("\n");
                        java.util.List<String> clean = new java.util.ArrayList<>();
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) clean.add(trimmed);
                        }
                        projects = clean.toArray(new String[0]);
                    }
                    saveProjects();
                    AppLog.i("RemoteDev", "專案路徑更新: " + projects.length + "個");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void executeTask() {
        String task = taskInput.getText().toString().trim();
        if (task.isEmpty()) {
            android.widget.Toast.makeText(this, "請輸入任務描述", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (running) {
            android.widget.Toast.makeText(this, "任務執行中，請等待", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        running = true;
        statusText.setText("⏳ 執行中... (最長 10 分鐘)");
        statusText.setTextColor(UIHelper.ACCENT_ORANGE);
        resultText.setText("等待 Bot 回覆...");
        runBtn.setEnabled(false);
        runBtn.setAlpha(0.5f);
        AppLog.i("RemoteDev", "執行任務: " + (task.length() > 100 ? task.substring(0, 100) + "..." : task));

        BridgeClient.remoteCode(task, selectedProject, (result, offline, error) -> {
            running = false;
            runBtn.setEnabled(true);
            runBtn.setAlpha(1f);

            if (result != null) {
                statusText.setText("✅ 完成");
                statusText.setTextColor(UIHelper.ACCENT_GREEN);
                // Strip markdown code fences from result
                String display = result;
                if (display.startsWith("```")) {
                    display = display.replaceAll("^```[\\w]*\\n?", "").replaceAll("\\n?```$", "");
                }
                resultText.setText(display);
                AppLog.i("RemoteDev", "任務完成: " + result.length() + "字");
            } else if (offline) {
                statusText.setText("❌ Bridge 離線");
                statusText.setTextColor(UIHelper.ACCENT_RED);
                resultText.setText("無法連線到 Bridge Server\n" + (error != null ? error : ""));
                AppLog.e("RemoteDev", "Bridge離線: " + error);
            } else {
                statusText.setText("❌ 錯誤");
                statusText.setTextColor(UIHelper.ACCENT_RED);
                resultText.setText(error != null ? error : "未知錯誤");
                AppLog.e("RemoteDev", "任務錯誤: " + error);
            }
        });
    }

    private void resetSession() {
        if (running) {
            android.widget.Toast.makeText(this, "任務執行中，請等待", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // Send /new command to bot via bridge
        statusText.setText("⏳ 重置 Session...");
        statusText.setTextColor(UIHelper.ACCENT_ORANGE);
        AppLog.i("RemoteDev", "重置Session");

        BridgeClient.remoteCode("/new", selectedProject, (result, offline, error) -> {
            if (result != null) {
                statusText.setText("✅ Session 已重置");
                statusText.setTextColor(UIHelper.ACCENT_GREEN);
                resultText.setText("新的對話已開始");
            } else {
                statusText.setText("❌ 重置失敗");
                statusText.setTextColor(UIHelper.ACCENT_RED);
                resultText.setText(error != null ? error : "未知錯誤");
            }
        });
    }
}
