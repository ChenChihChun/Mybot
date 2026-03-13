package com.mybot.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class InvoiceActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_GALLERY = 1002;
    private static final int PERMISSION_CAMERA = 2001;

    private ImageView imagePreview;
    private TextView statusText;
    private LinearLayout resultContainer;
    private android.widget.Button analyzeBtn;
    private android.widget.Button saveBtn;

    private EditText merchantInput, dateInput, amountInput, categoryInput, itemsInput, paymentInput;

    private String base64Image;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "\uD83E\uDDFE \u767C\u7968\u6383\u63CF\u8A18\u5E33");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        // Image preview area
        LinearLayout imageCard = UIHelper.card(this);
        imageCard.setGravity(Gravity.CENTER);

        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imagePreview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 200));
        imagePreview.setLayoutParams(imgLp);
        imagePreview.setBackgroundColor(UIHelper.BG_INPUT);
        imageCard.addView(imagePreview);

        // Camera + Gallery buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.setMargins(0, UIHelper.dp(this, 10), 0, 0);
        btnRow.setLayoutParams(btnRowLp);

        TextView cameraBtn = createActionBtn("\uD83D\uDCF7 \u62CD\u7167");
        cameraBtn.setOnClickListener(v -> openCamera());
        TextView galleryBtn = createActionBtn("\uD83D\uDDBC\uFE0F \u76F8\u7C3F");
        galleryBtn.setOnClickListener(v -> openGallery());

        btnRow.addView(cameraBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(UIHelper.dp(this, 10), 0));
        btnRow.addView(spacer);
        btnRow.addView(galleryBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        imageCard.addView(btnRow);
        content.addView(imageCard);

        // Analyze button
        analyzeBtn = UIHelper.primaryButton(this, "\u958B\u59CB\u5206\u6790");
        analyzeBtn.setEnabled(false);
        analyzeBtn.setAlpha(0.5f);
        analyzeBtn.setOnClickListener(v -> analyzeInvoice());
        content.addView(analyzeBtn);

        // Status
        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        content.addView(statusText);

        // Result container (hidden until analysis completes)
        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setVisibility(View.GONE);

        LinearLayout resultCard = UIHelper.card(this);

        resultCard.addView(label("\u5546\u5BB6"));
        merchantInput = UIHelper.styledInput(this, "\u5546\u5BB6\u540D\u7A31");
        resultCard.addView(merchantInput);

        resultCard.addView(label("\u65E5\u671F"));
        dateInput = UIHelper.styledInput(this, "YYYY-MM-DD");
        resultCard.addView(dateInput);

        resultCard.addView(label("\u91D1\u984D"));
        amountInput = UIHelper.styledInput(this, "0");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        resultCard.addView(amountInput);

        resultCard.addView(label("\u985E\u5225"));
        categoryInput = UIHelper.styledInput(this, "\u985E\u5225");
        resultCard.addView(categoryInput);

        resultCard.addView(label("\u54C1\u9805"));
        itemsInput = UIHelper.styledInput(this, "\u54C1\u9805\u660E\u7D30");
        resultCard.addView(itemsInput);

        resultCard.addView(label("\u4ED8\u6B3E\u65B9\u5F0F"));
        paymentInput = UIHelper.styledInput(this, "\u73FE\u91D1/\u4FE1\u7528\u5361/...");
        resultCard.addView(paymentInput);

        resultContainer.addView(resultCard);

        // Save button
        saveBtn = UIHelper.primaryButton(this, "\u5132\u5B58\u5230\u6D88\u8CBB\u7D00\u9304");
        saveBtn.setOnClickListener(v -> saveToExpense());
        resultContainer.addView(saveBtn);

        content.addView(resultContainer);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(UIHelper.TEXT_SECONDARY);
        tv.setPadding(UIHelper.dp(this, 4), UIHelper.dp(this, 8), 0, UIHelper.dp(this, 2));
        return tv;
    }

    private TextView createActionBtn(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(UIHelper.ACCENT_BLUE);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(UIHelper.roundRectStroke(UIHelper.BG_CARD, UIHelper.ACCENT_BLUE, 12, 1, this));
        int hp = UIHelper.dp(this, 12);
        int vp = UIHelper.dp(this, 12);
        btn.setPadding(hp, vp, hp, vp);
        return btn;
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = new File(getCacheDir(), "invoice_" + System.currentTimeMillis() + ".jpg");
        cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        try {
            Bitmap bitmap = null;
            if (requestCode == REQUEST_CAMERA && cameraUri != null) {
                InputStream is = getContentResolver().openInputStream(cameraUri);
                bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
            } else if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
                InputStream is = getContentResolver().openInputStream(data.getData());
                bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
            }

            if (bitmap != null) {
                bitmap = resizeBitmap(bitmap, 1024);
                imagePreview.setImageBitmap(bitmap);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                analyzeBtn.setEnabled(true);
                analyzeBtn.setAlpha(1f);
                statusText.setText("\u5716\u7247\u5DF2\u8F09\u5165\uFF0C\u9EDE\u64CA\u300C\u958B\u59CB\u5206\u6790\u300D");
                resultContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "\u8F09\u5165\u5716\u7247\u5931\u6557: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxSize && h <= maxSize) return original;

        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        return Bitmap.createScaledBitmap(original, newW, newH, true);
    }

    private void analyzeInvoice() {
        if (base64Image == null || base64Image.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u5148\u9078\u64C7\u6216\u62CD\u651D\u5716\u7247", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("\u2699\uFE0F AI \u5206\u6790\u4E2D...");
        analyzeBtn.setEnabled(false);
        analyzeBtn.setAlpha(0.5f);
        resultContainer.setVisibility(View.GONE);

        List<String> existingCats = new ExpenseDbHelper(this).getCategories();
        BridgeClient.analyzeInvoice(base64Image, existingCats, (responseJson, offline, error) -> {
            analyzeBtn.setEnabled(true);
            analyzeBtn.setAlpha(1f);

            if (offline || error != null) {
                statusText.setText("\u2716 \u5206\u6790\u5931\u6557: " + (error != null ? error : "Bridge \u96E2\u7DDA"));
                statusText.setTextColor(UIHelper.ACCENT_RED);
                return;
            }

            try {
                JSONObject json = new JSONObject(responseJson);
                boolean success = json.optBoolean("success", false);
                if (!success) {
                    statusText.setText("\u2716 \u5206\u6790\u5931\u6557: " + json.optString("error", "\u672A\u77E5\u932F\u8AA4"));
                    statusText.setTextColor(UIHelper.ACCENT_RED);
                    return;
                }

                JSONObject result = json.optJSONObject("result");
                if (result == null) {
                    statusText.setText("\u2716 \u7121\u6CD5\u89E3\u6790\u7D50\u679C");
                    statusText.setTextColor(UIHelper.ACCENT_RED);
                    return;
                }

                boolean isInvoice = result.optBoolean("is_invoice", false);
                if (!isInvoice) {
                    statusText.setText("\u26A0\uFE0F \u672A\u5075\u6E2C\u5230\u767C\u7968/\u6D88\u8CBB\u8CC7\u8A0A");
                    statusText.setTextColor(UIHelper.ACCENT_ORANGE);
                    return;
                }

                // Fill fields
                merchantInput.setText(result.optString("merchant", ""));
                dateInput.setText(result.optString("date", ""));
                String total = result.has("total") ? String.valueOf(result.optDouble("total", 0)) : "";
                amountInput.setText(total);
                categoryInput.setText(result.optString("category", ""));
                itemsInput.setText(result.optString("items", ""));
                paymentInput.setText(result.optString("payment_method", ""));

                statusText.setText("\u2714 \u5206\u6790\u5B8C\u6210\uFF0C\u8ACB\u78BA\u8A8D\u8CC7\u8A0A");
                statusText.setTextColor(UIHelper.ACCENT_GREEN);
                resultContainer.setVisibility(View.VISIBLE);

            } catch (Exception e) {
                statusText.setText("\u2716 \u89E3\u6790\u5931\u6557: " + e.getMessage());
                statusText.setTextColor(UIHelper.ACCENT_RED);
            }
        });
    }

    private void saveToExpense() {
        String amountStr = amountInput.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "\u8ACB\u8F38\u5165\u91D1\u984D", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "\u91D1\u984D\u683C\u5F0F\u4E0D\u6B63\u78BA", Toast.LENGTH_SHORT).show();
            return;
        }

        String merchant = merchantInput.getText().toString().trim();
        String category = categoryInput.getText().toString().trim();
        String items = itemsInput.getText().toString().trim();
        String payment = paymentInput.getText().toString().trim();
        String description = items;
        if (!payment.isEmpty()) {
            description += " (" + payment + ")";
        }

        ExpenseDbHelper db = new ExpenseDbHelper(this);
        db.insert(amount, "TWD", category, merchant, description, "\u767C\u7968\u6383\u63CF", "");

        Toast.makeText(this, "\u2714 \u5DF2\u5132\u5B58\u5230\u6D88\u8CBB\u7D00\u9304", Toast.LENGTH_SHORT).show();
        AppLog.i("Invoice", String.format(Locale.US, "\u767C\u7968\u8A18\u5E33: %s $%.0f %s", merchant, amount, category));
        finish();
    }
}
