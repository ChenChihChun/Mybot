package com.mybot.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TravelAchievementActivity extends AppCompatActivity {

    private TravelDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i("Travel", "TravelAchievementActivity\u958B\u555F");
        db = TravelDbHelper.getInstance(this);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(UIHelper.BG_TOP_BAR);
        int p = UIHelper.dp(this, 16);
        topBar.setPadding(p, UIHelper.dp(this, 12), p, UIHelper.dp(this, 12));
        topBar.setElevation(UIHelper.dp(this, 4));

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(20);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 12), 0);
        backBtn.setOnClickListener(v -> finish());

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83C\uDFC5 \u65C5\u904A\u6210\u5C31");
        titleTv.setTextSize(18);
        titleTv.setTextColor(UIHelper.TEXT_PRIMARY);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        topBar.addView(backBtn);
        topBar.addView(titleTv);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int cp = UIHelper.dp(this, 16);
        content.setPadding(cp, cp, cp, cp);

        // Stats summary
        int completed = db.getCompletedTripCount();
        int total = db.getTripCount();
        int unlocked = db.getUnlockedAchievementCount();
        List<String> regions = db.getVisitedRegions();

        LinearLayout statsCard = new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.HORIZONTAL);
        statsCard.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
        int sp = UIHelper.dp(this, 16);
        statsCard.setPadding(sp, sp, sp, sp);
        statsCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.setMargins(0, 0, 0, UIHelper.dp(this, 16));
        statsCard.setLayoutParams(statsLp);

        statsCard.addView(statItem("\uD83D\uDDFA\uFE0F", String.valueOf(completed), "\u5DF2\u5B8C\u6210\u65C5\u7A0B"));
        statsCard.addView(statItem("\uD83C\uDFC5", String.valueOf(unlocked), "\u5DF2\u89E3\u9396\u6210\u5C31"));
        statsCard.addView(statItem("\uD83D\uDCCD", String.valueOf(regions.size()) + "/7", "\u5DF2\u63A2\u7D22\u5340\u57DF"));

        content.addView(statsCard);

        // Achievement grid
        String[] allIds = TravelAchievementManager.getAllAchievementIds();

        // Section: Unlocked
        boolean hasUnlocked = false;
        for (String id : allIds) {
            if (db.isAchievementUnlocked(id)) { hasUnlocked = true; break; }
        }

        if (hasUnlocked) {
            content.addView(sectionHeader("\u2705 \u5DF2\u89E3\u9396"));
            for (String id : allIds) {
                if (db.isAchievementUnlocked(id)) {
                    content.addView(buildAchievementCard(id, true));
                }
            }
        }

        // Section: Locked
        content.addView(sectionHeader("\uD83D\uDD12 \u672A\u89E3\u9396"));
        for (String id : allIds) {
            if (!db.isAchievementUnlocked(id)) {
                content.addView(buildAchievementCard(id, false));
            }
        }

        scrollView.addView(content);
        root.addView(topBar);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private LinearLayout statItem(String icon, String value, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(20);
        iconTv.setGravity(Gravity.CENTER);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(22);
        valueTv.setTextColor(UIHelper.TEXT_PRIMARY);
        valueTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valueTv.setGravity(Gravity.CENTER);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(11);
        labelTv.setTextColor(UIHelper.TEXT_HINT);
        labelTv.setGravity(Gravity.CENTER);

        item.addView(iconTv);
        item.addView(valueTv);
        item.addView(labelTv);
        return item;
    }

    private LinearLayout buildAchievementCard(String achId, boolean unlocked) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(UIHelper.roundRect(unlocked ? UIHelper.BG_CARD : UIHelper.BG_CARD_ALT, 10, this));
        if (!unlocked) card.setAlpha(0.6f);
        int cp = UIHelper.dp(this, 14);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.setMargins(0, 0, 0, UIHelper.dp(this, 8));
        card.setLayoutParams(cLp);

        // Icon
        TextView iconTv = new TextView(this);
        iconTv.setText(unlocked ? TravelAchievementManager.getAchievementIcon(achId) : "\u2753");
        iconTv.setTextSize(28);
        iconTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UIHelper.dp(this, 48), UIHelper.dp(this, 48));
        iconLp.setMargins(0, 0, UIHelper.dp(this, 12), 0);
        iconTv.setLayoutParams(iconLp);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameTv = new TextView(this);
        nameTv.setText(TravelAchievementManager.getAchievementName(achId));
        nameTv.setTextSize(15);
        nameTv.setTextColor(unlocked ? UIHelper.TEXT_PRIMARY : UIHelper.TEXT_HINT);
        nameTv.setTypeface(null, Typeface.BOLD);

        TextView descTv = new TextView(this);
        descTv.setText(TravelAchievementManager.getAchievementDescription(achId));
        descTv.setTextSize(12);
        descTv.setTextColor(UIHelper.TEXT_SECONDARY);
        descTv.setPadding(0, UIHelper.dp(this, 2), 0, 0);

        info.addView(nameTv);
        info.addView(descTv);

        // Progress or date
        int maxProgress = TravelAchievementManager.getAchievementMaxProgress(achId);
        if (maxProgress > 1 && !unlocked) {
            int progress = db.getAchievementProgress(achId);
            TextView progressTv = new TextView(this);
            progressTv.setText(progress + "/" + maxProgress);
            progressTv.setTextSize(12);
            progressTv.setTextColor(UIHelper.ACCENT_BLUE);
            progressTv.setPadding(0, UIHelper.dp(this, 2), 0, 0);
            info.addView(progressTv);
        }

        card.addView(iconTv);
        card.addView(info);

        if (unlocked) {
            TextView check = new TextView(this);
            check.setText("\u2705");
            check.setTextSize(20);
            card.addView(check);
        }

        return card;
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        tv.setLayoutParams(lp);
        return tv;
    }
}
