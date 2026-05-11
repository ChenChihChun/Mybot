package com.mybot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Receives shared images (screenshots) and uses AI to extract expense data.
 * User can share a LINE Pay/bank notification screenshot to auto-record expenses.
 */
public class ExpenseShareReceiver extends AppCompatActivity {

    private ExpenseDbHelper db;
    private LinearLayout container;
    private ProgressBar progressBar;
    private TextView statusText;
    private ImageView previewImage;
    private Uri sharedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ExpenseDbHelper(this);

        // Build UI
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(UIHelper.BG_PRIMARY);
        container.setGravity(Gravity.CENTER);
        int pad = UIHelper.dp(this, 24);
        container.setPadding(pad, pad, pad, pad);

        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setAdjustViewBounds(true);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 200));
        imgLp.setMargins(0, 0, 0, UIHelper.dp(this, 20));
        container.addView(previewImage, imgLp);

        progressBar = new ProgressBar(this);
        container.addView(progressBar);

        statusText = new TextView(this);
        statusText.setText("AI 分析中...");
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 16), 0, 0);
        container.addView(statusText);

        setContentView(container);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        // Handle intent
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            showError("無法識別分享內容");
            return;
        }

        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) {
            showError("僅支援圖片分享");
            return;
        }

        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri == null) {
            showError("無法讀取圖片");
            return;
        }

        sharedImageUri = imageUri;
        AppLog.i("ExpenseShare", "收到圖片分享: " + imageUri);
        processImage(imageUri);
    }

    private void processImage(Uri imageUri) {
        new Thread(() -> {
            try {
                // Read and compress image
                InputStream is = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();

                if (bitmap == null) {
                    runOnUiThread(() -> showError("無法解碼圖片"));
                    return;
                }

                // Show preview
                Bitmap preview = scaleBitmap(bitmap, 400);
                runOnUiThread(() -> previewImage.setImageBitmap(preview));

                // Compress to JPEG for API (max 1MB target)
                Bitmap scaled = scaleBitmap(bitmap, 1024);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                AppLog.i("ExpenseShare", "圖片壓縮完成: " + imageBytes.length + " bytes");

                // Get existing categories
                List<String> categories = db.getDistinctCategories();

                // Call Bridge AI
                runOnUiThread(() -> statusText.setText("AI 分析消費資訊中..."));

                BridgeClient.analyzeScreenshot(base64, categories, (responseJson, offline, error) -> {
                    if (error != null || responseJson == null) {
                        String msg = offline ? "Bridge 離線" : error;
                        showError("分析失敗: " + msg);
                        return;
                    }

                    try {
                        JSONObject resp = new JSONObject(responseJson);
                        if (!resp.optBoolean("success", false)) {
                            showError("分析失敗: " + resp.optString("error", "unknown"));
                            return;
                        }

                        JSONObject result = resp.optJSONObject("result");
                        if (result == null) {
                            showError("分析結果格式錯誤");
                            return;
                        }

                        boolean isExpense = result.optBoolean("is_expense", false);
                        if (!isExpense) {
                            showNoExpense();
                            return;
                        }

                        // Extract expense data
                        double amount = result.optDouble("amount", 0);
                        String currency = result.optString("currency", "TWD");
                        String merchant = result.optString("merchant", "");
                        String category = result.optString("category", "");
                        String description = result.optString("description", "");

                        if (amount <= 0) {
                            showNoExpense();
                            return;
                        }

                        AppLog.i("ExpenseShare", String.format("AI分析結果: %s $%.0f [%s]",
                                merchant, amount, category));

                        // Show confirmation dialog
                        showConfirmDialog(amount, currency, merchant, category, description);

                    } catch (Exception e) {
                        AppLog.e("ExpenseShare", "解析回應失敗: " + e.getMessage());
                        showError("解析失敗: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                AppLog.e("ExpenseShare", "處理圖片失敗: " + e.getMessage());
                runOnUiThread(() -> showError("處理失敗: " + e.getMessage()));
            }
        }).start();
    }

    private Bitmap scaleBitmap(Bitmap original, int maxDim) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxDim && h <= maxDim) return original;

        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        return Bitmap.createScaledBitmap(original, newW, newH, true);
    }

    private void showConfirmDialog(double amount, String currency, String merchant,
                                   String category, String description) {
        progressBar.setVisibility(android.view.View.GONE);
        statusText.setText("分析完成");

        String info = String.format("商家: %s\n金額: $%.0f %s\n類別: %s\n描述: %s",
                merchant.isEmpty() ? "(未知)" : merchant,
                amount, currency,
                category.isEmpty() ? "(未分類)" : category,
                description.isEmpty() ? "(無)" : description);

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("確認記帳")
                .setMessage(info)
                .setPositiveButton("記帳", (d, w) -> {
                    long id = db.insert(amount, currency, category, merchant,
                            description, "截圖分享", null);
                    AppLog.i("ExpenseShare", "記帳完成: id=" + id + " " + merchant + " $" + amount);

                    // Try to delete the screenshot after recording
                    deleteScreenshot();

                    Toast.makeText(this, "已記帳: " + merchant + " $" + (int) amount,
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void showNoExpense() {
        progressBar.setVisibility(android.view.View.GONE);
        statusText.setText("未偵測到消費資訊");

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("分析結果")
                .setMessage("這張圖片中未偵測到消費/付款資訊。\n\n請確認截圖包含金額、商家等消費資訊。")
                .setPositiveButton("確定", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void showError(String message) {
        progressBar.setVisibility(android.view.View.GONE);
        statusText.setText("錯誤");

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("錯誤")
                .setMessage(message)
                .setPositiveButton("確定", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void deleteScreenshot() {
        if (sharedImageUri == null) return;

        try {
            int deleted = getContentResolver().delete(sharedImageUri, null, null);
            if (deleted > 0) {
                AppLog.i("ExpenseShare", "截圖已刪除: " + sharedImageUri);
            } else {
                AppLog.w("ExpenseShare", "無法刪除截圖 (權限不足或非媒體URI): " + sharedImageUri);
            }
        } catch (SecurityException e) {
            AppLog.w("ExpenseShare", "刪除截圖權限不足: " + e.getMessage());
        } catch (Exception e) {
            AppLog.w("ExpenseShare", "刪除截圖失敗: " + e.getMessage());
        }
    }
}
