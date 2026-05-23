package com.mybot.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

public class GpsMockActivity extends AppCompatActivity implements LocationListener {

    private static final int PERMISSION_LOCATION = 1001;

    private GpsMockDbHelper dbHelper;
    private LocationManager locationManager;
    private Geocoder geocoder;

    private TextView currentLocText;
    private EditText destInput;
    private TextView destResultText;
    private Spinner durationSpinner;
    private Button startStopBtn;
    private LinearLayout presetsContainer;
    private TextView statusText;

    private double currentLat = 0, currentLng = 0;
    private boolean hasLocation = false;

    private double destLat = 0, destLng = 0;
    private String destName = "";
    private boolean hasDestination = false;

    private final long[] DURATION_VALUES = {
            10 * 60 * 1000,   // 10 min
            30 * 60 * 1000,   // 30 min
            60 * 60 * 1000,   // 1 hr
            120 * 60 * 1000,  // 2 hr
    };
    private final String[] DURATION_LABELS = {"10 分鐘", "30 分鐘", "1 小時", "2 小時"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new GpsMockDbHelper(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        geocoder = new Geocoder(this, Locale.getDefault());

        LinearLayout root = UIHelper.pageRoot(this);

        // Top bar
        LinearLayout topBar = UIHelper.topBar(this, "GPS 模擬器");
        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(UIHelper.TEXT_PRIMARY);
        backBtn.setPadding(0, 0, UIHelper.dp(this, 16), 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, 0);
        root.addView(topBar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = UIHelper.dp(this, 16);
        content.setPadding(p, p, p, p);

        // Current location card
        content.addView(UIHelper.sectionHeader(this, "CURRENT LOCATION"));
        LinearLayout locCard = UIHelper.card(this);
        currentLocText = new TextView(this);
        currentLocText.setText("取得中...");
        currentLocText.setTextSize(14);
        currentLocText.setTextColor(UIHelper.TEXT_PRIMARY);
        locCard.addView(currentLocText);

        Button refreshLocBtn = UIHelper.smallButton(this, "重新定位", UIHelper.ACCENT_BLUE);
        refreshLocBtn.setOnClickListener(v -> requestLocation());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 36));
        btnLp.setMargins(0, UIHelper.dp(this, 8), 0, 0);
        refreshLocBtn.setLayoutParams(btnLp);
        locCard.addView(refreshLocBtn);

        content.addView(locCard);

        // Destination card
        content.addView(UIHelper.sectionHeader(this, "DESTINATION"));
        LinearLayout destCard = UIHelper.card(this);

        // Place search input
        TextView destLabel = new TextView(this);
        destLabel.setText("輸入地點名稱:");
        destLabel.setTextSize(14);
        destLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        destCard.addView(destLabel);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchRowLp.setMargins(0, UIHelper.dp(this, 4), 0, 0);
        searchRow.setLayoutParams(searchRowLp);

        destInput = UIHelper.styledInput(this, "例: 台北101、東京鐵塔");
        destInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchRow.addView(destInput);

        Button searchBtn = UIHelper.smallButton(this, "搜尋", UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams searchBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 44));
        searchBtnLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        searchBtn.setLayoutParams(searchBtnLp);
        searchBtn.setOnClickListener(v -> searchPlace());
        searchRow.addView(searchBtn);

        destCard.addView(searchRow);

        // Search result display
        destResultText = new TextView(this);
        destResultText.setText("尚未選擇目的地");
        destResultText.setTextSize(13);
        destResultText.setTextColor(UIHelper.TEXT_HINT);
        destResultText.setPadding(0, UIHelper.dp(this, 8), 0, UIHelper.dp(this, 8));
        destCard.addView(destResultText);

        // Duration spinner
        LinearLayout durRow = new LinearLayout(this);
        durRow.setOrientation(LinearLayout.HORIZONTAL);
        durRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams durRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durRowLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
        durRow.setLayoutParams(durRowLp);

        TextView durLabel = new TextView(this);
        durLabel.setText("移動時間:");
        durLabel.setTextSize(14);
        durLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        durLabel.setMinWidth(UIHelper.dp(this, 80));
        durRow.addView(durLabel);

        durationSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, DURATION_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durationSpinner.setAdapter(adapter);
        durationSpinner.setBackground(UIHelper.roundRectStroke(UIHelper.BG_INPUT, Color.parseColor("#2E4050"), 14, 1, this));
        durRow.addView(durationSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        destCard.addView(durRow);

        // Save preset button
        Button savePresetBtn = UIHelper.smallButton(this, "儲存為預設", UIHelper.ACCENT_GREEN);
        savePresetBtn.setOnClickListener(v -> showSavePresetDialog());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 36));
        saveLp.setMargins(0, UIHelper.dp(this, 8), 0, 0);
        savePresetBtn.setLayoutParams(saveLp);
        destCard.addView(savePresetBtn);

        content.addView(destCard);

        // Start/Stop button
        startStopBtn = UIHelper.primaryButton(this, "開始模擬");
        startStopBtn.setOnClickListener(v -> toggleMock());
        content.addView(startStopBtn);

        // Status text
        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        content.addView(statusText);

        // Presets section
        content.addView(UIHelper.sectionHeader(this, "PRESETS"));
        presetsContainer = new LinearLayout(this);
        presetsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(presetsContainer);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        loadPresets();
        checkAndRequestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
        requestLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            locationManager.removeUpdates(this);
        } catch (Exception ignored) {}
    }

    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocation();
            } else {
                currentLocText.setText("需要位置權限");
                currentLocText.setTextColor(UIHelper.ACCENT_RED);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            currentLocText.setText("需要位置權限");
            return;
        }

        currentLocText.setText("取得中...");
        currentLocText.setTextColor(UIHelper.TEXT_PRIMARY);

        try {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown == null) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnown != null) {
                updateCurrentLocation(lastKnown);
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
            } else {
                currentLocText.setText("請開啟 GPS");
                currentLocText.setTextColor(UIHelper.ACCENT_RED);
            }
        } catch (Exception e) {
            currentLocText.setText("取得位置失敗: " + e.getMessage());
            currentLocText.setTextColor(UIHelper.ACCENT_RED);
        }
    }

    private void updateCurrentLocation(Location loc) {
        currentLat = loc.getLatitude();
        currentLng = loc.getLongitude();
        hasLocation = true;
        currentLocText.setText(String.format("%.6f, %.6f", currentLat, currentLng));
        currentLocText.setTextColor(UIHelper.ACCENT_GREEN);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateCurrentLocation(location);
        try {
            locationManager.removeUpdates(this);
        } catch (Exception ignored) {}
    }

    private void searchPlace() {
        String query = destInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "請輸入地點名稱", Toast.LENGTH_SHORT).show();
            return;
        }

        destResultText.setText("搜尋中...");
        destResultText.setTextColor(UIHelper.TEXT_SECONDARY);

        new Thread(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, 5);
                runOnUiThread(() -> {
                    if (results == null || results.isEmpty()) {
                        destResultText.setText("找不到「" + query + "」");
                        destResultText.setTextColor(UIHelper.ACCENT_RED);
                        hasDestination = false;
                    } else if (results.size() == 1) {
                        // Single result, use directly
                        selectAddress(results.get(0));
                    } else {
                        // Multiple results, show picker
                        showAddressPicker(results);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    destResultText.setText("搜尋失敗: " + e.getMessage());
                    destResultText.setTextColor(UIHelper.ACCENT_RED);
                    hasDestination = false;
                    AppLog.e("GpsMock", "地點搜尋失敗: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showAddressPicker(List<Address> addresses) {
        String[] items = new String[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            Address addr = addresses.get(i);
            items[i] = formatAddress(addr);
        }

        new AlertDialog.Builder(this)
                .setTitle("選擇地點")
                .setItems(items, (dialog, which) -> selectAddress(addresses.get(which)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void selectAddress(Address address) {
        destLat = address.getLatitude();
        destLng = address.getLongitude();
        destName = formatAddress(address);
        hasDestination = true;

        destResultText.setText(String.format("%s\n(%.6f, %.6f)", destName, destLat, destLng));
        destResultText.setTextColor(UIHelper.ACCENT_GREEN);

        AppLog.i("GpsMock", "選擇目的地: " + destName);
    }

    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();

        // Try to get a meaningful name
        if (address.getFeatureName() != null && !address.getFeatureName().matches("\\d+")) {
            sb.append(address.getFeatureName());
        }

        if (address.getLocality() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getLocality());
        }

        if (address.getAdminArea() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getAdminArea());
        }

        if (address.getCountryName() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getCountryName());
        }

        if (sb.length() == 0) {
            // Fallback to address lines
            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(address.getAddressLine(i));
            }
        }

        return sb.length() > 0 ? sb.toString() : String.format("%.4f, %.4f", address.getLatitude(), address.getLongitude());
    }

    private void setDestination(double lat, double lng, String name) {
        destLat = lat;
        destLng = lng;
        destName = name;
        hasDestination = true;
        destInput.setText(name);
        destResultText.setText(String.format("%s\n(%.6f, %.6f)", name, lat, lng));
        destResultText.setTextColor(UIHelper.ACCENT_GREEN);
    }

    private void toggleMock() {
        if (GpsMockService.isRunning(this)) {
            Intent stopIntent = new Intent(this, GpsMockService.class);
            stopIntent.setAction(GpsMockService.ACTION_STOP);
            startService(stopIntent);
            updateButtonState();
            return;
        }

        if (!hasLocation) {
            Toast.makeText(this, "請先取得目前位置", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasDestination) {
            Toast.makeText(this, "請先搜尋並選擇目的地", Toast.LENGTH_SHORT).show();
            return;
        }

        long duration = DURATION_VALUES[durationSpinner.getSelectedItemPosition()];

        Intent intent = new Intent(this, GpsMockService.class);
        intent.putExtra(GpsMockService.EXTRA_START_LAT, currentLat);
        intent.putExtra(GpsMockService.EXTRA_START_LNG, currentLng);
        intent.putExtra(GpsMockService.EXTRA_END_LAT, destLat);
        intent.putExtra(GpsMockService.EXTRA_END_LNG, destLng);
        intent.putExtra(GpsMockService.EXTRA_DURATION_MS, duration);

        try {
            startForegroundService(intent);
            Toast.makeText(this, "開始模擬位置", Toast.LENGTH_SHORT).show();
            startStopBtn.postDelayed(this::updateButtonState, 500);
        } catch (SecurityException e) {
            showMockLocationSetupDialog();
        }
    }

    private void updateButtonState() {
        boolean running = GpsMockService.isRunning(this);
        if (running) {
            startStopBtn.setText("停止模擬");
            startStopBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_RED, 14, this));
            statusText.setText("模擬中... 點擊上方按鈕或通知欄停止");
            statusText.setTextColor(UIHelper.ACCENT_GREEN);
        } else {
            startStopBtn.setText("開始模擬");
            startStopBtn.setBackground(UIHelper.roundRect(UIHelper.ACCENT_GREEN, 14, this));
            statusText.setText("請在開發者選項中選擇此應用作為模擬位置應用");
            statusText.setTextColor(UIHelper.TEXT_SECONDARY);
        }
    }

    private void showMockLocationSetupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("設定模擬位置")
                .setMessage("請先在「設定 > 開發者選項 > 選取模擬位置應用程式」中選擇 Mybot。")
                .setPositiveButton("前往設定", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    } catch (Exception e) {
                        Toast.makeText(this, "無法開啟設定", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadPresets() {
        presetsContainer.removeAllViews();
        List<GpsMockDbHelper.Preset> presets = dbHelper.getAllPresets();

        if (presets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚無預設地點");
            empty.setTextColor(UIHelper.TEXT_HINT);
            empty.setTextSize(13);
            empty.setPadding(0, UIHelper.dp(this, 8), 0, 0);
            presetsContainer.addView(empty);
            return;
        }

        for (GpsMockDbHelper.Preset preset : presets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UIHelper.roundRect(UIHelper.BG_CARD, 12, this));
            int pad = UIHelper.dp(this, 12);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 4));
            row.setLayoutParams(rowLp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(this);
            name.setText(preset.name);
            name.setTextSize(14);
            name.setTextColor(UIHelper.TEXT_PRIMARY);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            TextView coords = new TextView(this);
            coords.setText(String.format("%.4f, %.4f", preset.lat, preset.lng));
            coords.setTextSize(12);
            coords.setTextColor(UIHelper.TEXT_SECONDARY);

            info.addView(name);
            info.addView(coords);

            Button useBtn = UIHelper.smallButton(this, "使用", UIHelper.ACCENT_BLUE);
            useBtn.setOnClickListener(v -> setDestination(preset.lat, preset.lng, preset.name));

            Button delBtn = UIHelper.smallButton(this, "刪除", UIHelper.ACCENT_RED);
            delBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("刪除預設")
                        .setMessage("確定要刪除「" + preset.name + "」？")
                        .setPositiveButton("刪除", (d, w) -> {
                            dbHelper.deletePreset(preset.id);
                            AppLog.i("GpsMock", "刪除預設: " + preset.name);
                            loadPresets();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            row.addView(info);
            row.addView(useBtn);
            row.addView(delBtn);

            presetsContainer.addView(row);
        }
    }

    private void showSavePresetDialog() {
        if (!hasDestination) {
            Toast.makeText(this, "請先搜尋並選擇目的地", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setHint("預設名稱");
        nameInput.setText(destName);

        new AlertDialog.Builder(this)
                .setTitle("儲存預設")
                .setView(nameInput)
                .setPositiveButton("儲存", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = String.format("%.2f, %.2f", destLat, destLng);
                    }
                    dbHelper.insertPreset(name, destLat, destLng);
                    AppLog.i("GpsMock", "儲存預設: " + name);
                    loadPresets();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
