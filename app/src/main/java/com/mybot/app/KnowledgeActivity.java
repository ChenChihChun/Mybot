package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class KnowledgeActivity extends AppCompatActivity {

    private KnowledgeDbHelper dbHelper;
    private LinearLayout listContainer;
    private LinearLayout chipContainer;
    private EditText searchInput;
    private String selectedCategory = null; // null = all
    private TextView countLabel;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new KnowledgeDbHelper(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        AppLog.i("Knowledge", "開啟知識庫");
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        root.addView(UIHelper.topBar(this, "\uD83D\uDCDA 知識庫"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        // Search bar
        searchInput = UIHelper.styledInput(this, "\uD83D\uDD0D 搜尋知識...");
        searchInput.setSingleLine(true);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                refreshList();
            }
        });
        content.addView(searchInput);

        // Category filter chips (horizontal scroll)
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(4), 0, dp(8));

        chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipContainer.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chipContainer);
        content.addView(chipScroll);

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

    private void refreshList() {
        listContainer.removeAllViews();

        String query = searchInput.getText().toString().trim();
        List<KnowledgeDbHelper.Knowledge> items;

        if (!query.isEmpty()) {
            items = dbHelper.search(query);
        } else if (selectedCategory != null) {
            items = dbHelper.getByCategory(selectedCategory);
        } else {
            items = dbHelper.getAll();
        }

        // Update chips
        refreshChips();

        // Update count
        countLabel.setText(items.size() + " 筆知識");

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(query.isEmpty() ? "尚無儲存的知識\n從影片摘要儲存第一筆吧！" : "找不到符合的結果");
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            listContainer.addView(empty);
            return;
        }

        for (KnowledgeDbHelper.Knowledge item : items) {
            listContainer.addView(buildKnowledgeCard(item));
        }
    }

    private void refreshChips() {
        chipContainer.removeAllViews();

        List<String> categories = dbHelper.getAllCategories();

        // "All" chip
        chipContainer.addView(buildChip("全部", selectedCategory == null, v -> {
            selectedCategory = null;
            refreshList();
        }));

        for (String cat : categories) {
            chipContainer.addView(buildChip(cat, cat.equals(selectedCategory), v -> {
                selectedCategory = cat;
                searchInput.setText("");
                refreshList();
            }));
        }
    }

    private TextView buildChip(String text, boolean selected, View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(text);
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
        chip.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);

        return chip;
    }

    private LinearLayout buildKnowledgeCard(KnowledgeDbHelper.Knowledge item) {
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

        // Header row: category badge + date
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        if (item.category != null && !item.category.isEmpty()) {
            TextView badge = UIHelper.statusBadge(this, item.category, getCategoryColor(item.category));
            headerRow.addView(badge);
        }

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        headerRow.addView(spacer);

        TextView dateText = new TextView(this);
        dateText.setText(DATE_FORMAT.format(new Date(item.createdAt)));
        dateText.setTextSize(11);
        dateText.setTextColor(UIHelper.TEXT_HINT);
        headerRow.addView(dateText);

        card.addView(headerRow);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTextSize(15);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, dp(8), 0, dp(4));
        titleView.setMaxLines(2);
        card.addView(titleView);

        // Summary (truncated)
        if (item.summary != null && !item.summary.isEmpty()) {
            TextView summaryView = new TextView(this);
            String previewText = item.summary.length() > 120
                    ? item.summary.substring(0, 120) + "..."
                    : item.summary;
            summaryView.setText(previewText);
            summaryView.setTextColor(UIHelper.TEXT_SECONDARY);
            summaryView.setTextSize(13);
            summaryView.setLineSpacing(dp(2), 1f);
            summaryView.setMaxLines(3);
            card.addView(summaryView);
        }

        // Action row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(8), 0, 0);

        // Open source URL
        if (item.sourceUrl != null && !item.sourceUrl.isEmpty()) {
            TextView srcBtn = new TextView(this);
            srcBtn.setText("來源");
            srcBtn.setTextColor(UIHelper.ACCENT_BLUE);
            srcBtn.setTextSize(12);
            srcBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
            srcBtn.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl));
                    startActivity(intent);
                    AppLog.i("Knowledge", "開啟來源: " + item.sourceUrl);
                } catch (Exception e) {
                    Toast.makeText(this, "無法開啟連結", Toast.LENGTH_SHORT).show();
                }
            });
            actionRow.addView(srcBtn);
        }

        // Expand / detail
        TextView detailBtn = new TextView(this);
        detailBtn.setText("詳情");
        detailBtn.setTextColor(UIHelper.ACCENT_GREEN);
        detailBtn.setTextSize(12);
        detailBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        detailBtn.setOnClickListener(v -> showDetailDialog(item));
        actionRow.addView(detailBtn);

        // Delete
        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("刪除");
        deleteBtn.setTextColor(UIHelper.ACCENT_RED);
        deleteBtn.setTextSize(12);
        deleteBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        deleteBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("刪除知識")
                    .setMessage("確定要刪除「" + item.title + "」？")
                    .setPositiveButton("刪除", (d, w) -> {
                        dbHelper.delete(item.id);
                        AppLog.i("Knowledge", "刪除知識: " + item.title);
                        Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show();
                        refreshList();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        actionRow.addView(deleteBtn);

        card.addView(actionRow);

        return card;
    }

    private void showDetailDialog(KnowledgeDbHelper.Knowledge item) {
        AppLog.i("Knowledge", "查看詳情: " + item.title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(UIHelper.BG_PRIMARY);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        // Category
        if (item.category != null && !item.category.isEmpty()) {
            TextView catView = UIHelper.statusBadge(this, item.category, getCategoryColor(item.category));
            layout.addView(catView);
        }

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextColor(UIHelper.TEXT_PRIMARY);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, dp(12), 0, dp(8));
        layout.addView(titleView);

        // Summary
        if (item.summary != null && !item.summary.isEmpty()) {
            TextView sumLabel = new TextView(this);
            sumLabel.setText("\uD83D\uDCCB 摘要");
            sumLabel.setTextColor(UIHelper.ACCENT_BLUE);
            sumLabel.setTextSize(13);
            sumLabel.setTypeface(null, Typeface.BOLD);
            sumLabel.setPadding(0, dp(8), 0, dp(4));
            layout.addView(sumLabel);

            TextView sumView = new TextView(this);
            sumView.setText(item.summary);
            sumView.setTextColor(UIHelper.TEXT_PRIMARY);
            sumView.setTextSize(14);
            sumView.setLineSpacing(dp(3), 1f);
            layout.addView(sumView);
        }

        // Key points
        if (item.keyPoints != null && !item.keyPoints.isEmpty()) {
            TextView ptLabel = new TextView(this);
            ptLabel.setText("\uD83D\uDD11 重點");
            ptLabel.setTextColor(UIHelper.ACCENT_ORANGE);
            ptLabel.setTextSize(13);
            ptLabel.setTypeface(null, Typeface.BOLD);
            ptLabel.setPadding(0, dp(12), 0, dp(4));
            layout.addView(ptLabel);

            TextView ptView = new TextView(this);
            ptView.setText(item.keyPoints);
            ptView.setTextColor(UIHelper.TEXT_PRIMARY);
            ptView.setTextSize(13);
            ptView.setLineSpacing(dp(2), 1f);
            layout.addView(ptView);
        }

        // Source URL
        if (item.sourceUrl != null && !item.sourceUrl.isEmpty()) {
            TextView srcView = new TextView(this);
            srcView.setText("\uD83D\uDD17 " + item.sourceUrl);
            srcView.setTextColor(UIHelper.ACCENT_BLUE);
            srcView.setTextSize(12);
            srcView.setPadding(0, dp(12), 0, 0);
            srcView.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)));
                } catch (Exception e) {
                    Toast.makeText(this, "無法開啟連結", Toast.LENGTH_SHORT).show();
                }
            });
            layout.addView(srcView);
        }

        // Date
        TextView dateView = new TextView(this);
        dateView.setText("儲存時間: " + DATE_FORMAT.format(new Date(item.createdAt)));
        dateView.setTextColor(UIHelper.TEXT_HINT);
        dateView.setTextSize(11);
        dateView.setPadding(0, dp(12), 0, 0);
        layout.addView(dateView);

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("關閉", null)
                .show();
    }

    private int getCategoryColor(String category) {
        if (category == null) return UIHelper.TEXT_SECONDARY;
        switch (category) {
            case "科技": return UIHelper.ACCENT_BLUE;
            case "投資": case "財經": return UIHelper.ACCENT_ORANGE;
            case "健康": case "醫療": return UIHelper.ACCENT_GREEN;
            case "教育": case "學習": return UIHelper.ACCENT_PURPLE;
            case "娛樂": return UIHelper.ACCENT_RED;
            case "商業": case "創業": return Color.parseColor("#FFB74D");
            case "生活": return Color.parseColor("#4DB6AC");
            case "心理": case "心靈": return Color.parseColor("#CE93D8");
            default: return UIHelper.TEXT_SECONDARY;
        }
    }

    private int dp(int v) {
        return UIHelper.dp(this, v);
    }
}
