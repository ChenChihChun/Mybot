package com.mybot.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    public static final int ACCENT_BLUE = Color.parseColor("#4FC3F7");     // Light blue accent
    public static final int ACCENT_GREEN = Color.parseColor("#66BB6A");    // Green (success)
    public static final int ACCENT_RED = Color.parseColor("#EF5350");      // Red (expense)
    public static final int ACCENT_ORANGE = Color.parseColor("#FFA726");   // Orange (warning)
    public static final int ACCENT_PURPLE = Color.parseColor("#AB47BC");   // Purple (category)

    public static final int TEXT_PRIMARY = Color.parseColor("#ECEFF1");    // White-ish
    public static final int TEXT_SECONDARY = Color.parseColor("#90A4AE");  // Gray-blue
    public static final int TEXT_HINT = Color.parseColor("#546E7A");       // Dim hint

    public static final int DIVIDER = Color.parseColor("#263238");         // Subtle divider

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
        bar.setBackgroundColor(Color.parseColor("#162330"));
        int p = dp(ctx, 20);
        bar.setPadding(p, dp(ctx, 16), p, dp(ctx, 16));

        TextView title = new TextView(ctx);
        title.setText(titleText);
        title.setTextSize(20);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(title);
        return bar;
    }

    public static Button primaryButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setAllCaps(false);
        btn.setBackground(roundRect(ACCENT_BLUE, 12, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 52));
        lp.setMargins(0, dp(ctx, 8), 0, dp(ctx, 8));
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
        btn.setBackground(roundRectStroke(BG_CARD, iconColor, 16, 1, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        int h = dp(ctx, 16);
        int v = dp(ctx, 20);
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
        btn.setBackground(roundRectStroke(Color.TRANSPARENT, TEXT_HINT, 12, 1, ctx));
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48));
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
        et.setBackground(roundRectStroke(BG_INPUT, Color.parseColor("#2E4050"), 12, 1, ctx));
        int pad = dp(ctx, 16);
        et.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
        et.setLayoutParams(lp);
        return et;
    }

    public static LinearLayout card(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(BG_CARD, 14, ctx));
        int pad = dp(ctx, 16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, 4), 0, dp(ctx, 4));
        card.setLayoutParams(lp);
        return card;
    }

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)));
        return v;
    }

    public static TextView statusBadge(Context ctx, String text, int bgColor) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setBackground(roundRect(bgColor, 8, ctx));
        int h = dp(ctx, 8);
        int v = dp(ctx, 4);
        tv.setPadding(h, v, h, v);
        return tv;
    }
}
