package com.mybot.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class UIHelper {

    // Color Palette - Modern Dark Theme
    public static final int BG_PRIMARY = Color.parseColor("#0F1923");      // Deep navy
    public static final int BG_CARD = Color.parseColor("#1A2733");         // Card background
    public static final int BG_CARD_ALT = Color.parseColor("#223344");     // Alternate card
    public static final int BG_INPUT = Color.parseColor("#1E2D3D");        // Input field bg
    public static final int BG_TOP_BAR = Color.parseColor("#162330");      // Top bar

    public static final int ACCENT_BLUE = Color.parseColor("#4FC3F7");     // Light blue accent
    public static final int ACCENT_GREEN = Color.parseColor("#66BB6A");    // Green (success/primary)
    public static final int ACCENT_RED = Color.parseColor("#EF5350");      // Red (expense)
    public static final int ACCENT_ORANGE = Color.parseColor("#FFA726");   // Orange (warning)
    public static final int ACCENT_PURPLE = Color.parseColor("#AB47BC");   // Purple (category)

    public static final int TEXT_PRIMARY = Color.parseColor("#ECEFF1");    // White-ish
    public static final int TEXT_SECONDARY = Color.parseColor("#90A4AE");  // Gray-blue
    public static final int TEXT_HINT = Color.parseColor("#546E7A");       // Dim hint

    public static final int DIVIDER = Color.parseColor("#263238");         // Subtle divider
    public static final int CARD_SHADOW = Color.parseColor("#08000000");   // Card shadow tint

    public static int dp(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    public static GradientDrawable roundRect(int color, int radiusDp, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(ctx, radiusDp));
        return gd;
    }

    public static GradientDrawable roundRectStroke(int fillColor, int strokeColor, int radiusDp, int strokeDp, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setStroke(dp(ctx, strokeDp), strokeColor);
        return gd;
    }

    public static LinearLayout pageRoot(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_PRIMARY);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    public static LinearLayout topBar(Context ctx, String titleText) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BG_TOP_BAR);
        int p = dp(ctx, 20);
        bar.setPadding(p, dp(ctx, 18), p, dp(ctx, 18));
        bar.setElevation(dp(ctx, 4));

        TextView title = new TextView(ctx);
        title.setText(titleText);
        title.setTextSize(22);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLetterSpacing(0.02f);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(title);
        return bar;
    }

    public static Button primaryButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setAllCaps(false);
        btn.setLetterSpacing(0.03f);
        btn.setBackground(roundRect(ACCENT_GREEN, 14, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(dp(ctx, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 54));
        lp.setMargins(0, dp(ctx, 10), 0, dp(ctx, 10));
        btn.setLayoutParams(lp);
        return btn;
    }

    public static Button cardButton(Context ctx, String text, String subtitle, int iconColor) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(TEXT_PRIMARY);
        btn.setTextSize(16);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        // Left accent border effect
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG_CARD);
        bg.setCornerRadius(dp(ctx, 14));
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(dp(ctx, 14));
        border.setStroke(dp(ctx, 1), Color.parseColor("#1E3040"));
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{bg, border});
        btn.setBackground(layers);

        btn.setStateListAnimator(null);
        btn.setElevation(dp(ctx, 3));
        int h = dp(ctx, 18);
        int v = dp(ctx, 22);
        btn.setPadding(v, h, v, h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
        btn.setLayoutParams(lp);
        return btn;
    }

    public static Button outlineButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(TEXT_SECONDARY);
        btn.setTextSize(15);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setBackground(roundRectStroke(Color.TRANSPARENT, TEXT_HINT, 14, 1, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50));
        lp.setMargins(0, dp(ctx, 8), 0, dp(ctx, 8));
        btn.setLayoutParams(lp);
        return btn;
    }

    public static EditText styledInput(Context ctx, String hint) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(TEXT_HINT);
        et.setTextColor(TEXT_PRIMARY);
        et.setTextSize(16);
        et.setBackground(roundRectStroke(BG_INPUT, Color.parseColor("#2E4050"), 14, 1, ctx));
        int pad = dp(ctx, 16);
        et.setPadding(pad, dp(ctx, 14), pad, dp(ctx, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
        et.setLayoutParams(lp);
        return et;
    }

    public static LinearLayout card(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(BG_CARD, 16, ctx));
        int pad = dp(ctx, 18);
        card.setPadding(pad, pad, pad, pad);
        card.setElevation(dp(ctx, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout sectionHeader(Context ctx, String text) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(dp(ctx, 4), dp(ctx, 16), dp(ctx, 4), dp(ctx, 10));

        View line = new View(ctx);
        line.setBackgroundColor(ACCENT_GREEN);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 16));
        lineLp.setMargins(0, 0, dp(ctx, 10), 0);
        line.setLayoutParams(lineLp);

        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(TEXT_SECONDARY);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setLetterSpacing(0.1f);

        wrapper.addView(line);
        wrapper.addView(label);
        return wrapper;
    }

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
        v.setLayoutParams(lp);
        return v;
    }

    public static TextView statusBadge(Context ctx, String text, int bgColor) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setBackground(roundRect(bgColor, 10, ctx));
        int h = dp(ctx, 10);
        int v = dp(ctx, 4);
        tv.setPadding(h, v, h, v);
        return tv;
    }

    /**
     * Feature card with large icon circle, title, and subtitle.
     * Used for main menu grid items.
     */
    public static LinearLayout featureCard(Context ctx, String icon, String title, String subtitle,
                                            int accentColor, int iconBgAlpha) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundRect(BG_CARD, 20, ctx));
        card.setElevation(dp(ctx, 4));
        int pad = dp(ctx, 16);
        card.setPadding(pad, dp(ctx, 20), pad, dp(ctx, 18));
        card.setClickable(true);
        card.setFocusable(true);

        // Icon circle with semi-transparent accent background
        TextView iconView = new TextView(ctx);
        iconView.setText(icon);
        iconView.setTextSize(30);
        iconView.setGravity(Gravity.CENTER);
        // Create a lighter version of the accent color for the icon background
        int bgColor = Color.argb(iconBgAlpha,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        iconView.setBackground(roundRect(bgColor, 20, ctx));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                dp(ctx, 60), dp(ctx, 60));
        iconLp.gravity = Gravity.CENTER;
        iconLp.setMargins(0, 0, 0, dp(ctx, 12));
        iconView.setLayoutParams(iconLp);

        // Title
        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setGravity(Gravity.CENTER);

        // Subtitle
        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(TEXT_HINT);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(ctx, 4), 0, 0);

        // Accent bottom line
        View bottomLine = new View(ctx);
        bottomLine.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 3));
        lineLp.gravity = Gravity.CENTER;
        lineLp.setMargins(0, dp(ctx, 12), 0, 0);
        bottomLine.setLayoutParams(lineLp);

        card.addView(iconView);
        card.addView(titleView);
        card.addView(subtitleView);
        card.addView(bottomLine);

        return card;
    }

    /**
     * Compact card for 3-column grid: small icon + label, no subtitle.
     */
    public static LinearLayout compactCard(Context ctx, String icon, String label, int accentColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundRect(BG_CARD, 16, ctx));
        card.setElevation(dp(ctx, 3));
        int pad = dp(ctx, 10);
        card.setPadding(pad, dp(ctx, 14), pad, dp(ctx, 12));
        card.setClickable(true);
        card.setFocusable(true);

        TextView iconView = new TextView(ctx);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        int bgColor = Color.argb(35,
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        iconView.setBackground(roundRect(bgColor, 14, ctx));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44));
        iconLp.gravity = Gravity.CENTER;
        iconLp.setMargins(0, 0, 0, dp(ctx, 8));
        iconView.setLayoutParams(iconLp);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(TEXT_PRIMARY);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);

        card.addView(iconView);
        card.addView(labelView);
        return card;
    }

    /**
     * Dashboard summary card with value and label.
     */
    public static LinearLayout dashboardCard(Context ctx, String icon, String value, String label, int accentColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(BG_CARD, 14, ctx));
        card.setElevation(dp(ctx, 3));
        int pad = dp(ctx, 12);
        card.setPadding(pad, pad, pad, pad);
        card.setClickable(true);
        card.setFocusable(true);

        // Top row: icon
        TextView iconView = new TextView(ctx);
        iconView.setText(icon);
        iconView.setTextSize(16);

        // Value
        TextView valueView = new TextView(ctx);
        valueView.setText(value);
        valueView.setTextSize(20);
        valueView.setTextColor(accentColor);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        valueView.setPadding(0, dp(ctx, 4), 0, dp(ctx, 2));
        valueView.setTag("dashboard_value"); // for dynamic update

        // Label
        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(TEXT_SECONDARY);
        labelView.setTag("dashboard_label");

        card.addView(iconView);
        card.addView(valueView);
        card.addView(labelView);
        return card;
    }

    public static Button smallButton(Context ctx, String text, int color) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(color);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setBackground(roundRectStroke(Color.TRANSPARENT, color, 10, 1, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        int pad = dp(ctx, 12);
        btn.setPadding(pad, 0, pad, 0);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        return btn;
    }
}
