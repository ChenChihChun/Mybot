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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.CheckBox;
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
    private EditText startInput;
    private TextView startResultText;
    private EditText destInput;
    private TextView destResultText;
    private Spinner durationSpinner;
    private CheckBox followRoadsCheck;
    private Button startStopBtn;
    private LinearLayout presetsContainer;
    private TextView statusText;

    private double currentLat = 0, currentLng = 0;
    private boolean hasLocation = false;

    private double startLat = 0, startLng = 0;
    private String startName = "";
    private boolean hasStartPoint = false;
    private boolean useCurrentAsStart = true;

    private double destLat = 0, destLng = 0;
    private String destName = "";
    private boolean hasDestination = false;

    private Handler uiHandler;
    private TextView mockStatusText;

    private final Runnable statusUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            // Check for errors
            String error = GpsMockService.getLastError();
            if (error != null) {
                GpsMockService.clearLastError();
                Toast.makeText(GpsMockActivity.this, error, Toast.LENGTH_LONG).show();
                showMockLocationSetupDialog();
            }

            if (GpsMockService.isRunning()) {
                double lat = GpsMockService.getCurrentLat();
                double lng = GpsMockService.getCurrentLng();
                int progress = (int) (GpsMockService.getProgress() * 100);
                if (GpsMockService.hasArrived()) {
                    mockStatusText.setText(String.format("已到達: %.4f, %.4f", lat, lng));
                } else {
                    mockStatusText.setText(String.format("模擬位置: %.4f, %.4f (%d%%)", lat, lng, progress));
                }
                mockStatusText.setTextColor(UIHelper.ACCENT_GREEN);
                uiHandler.postDelayed(this, 1000);
            } else {
                mockStatusText.setText("");
            }
            updateButtonState();
        }
    };

    private final long[] DURATION_VALUES = {
            10 * 60 * 1000,   // 10 min
            30 * 60 * 1000,   // 30 min
            60 * 60 * 1000,   // 1 hr
            120 * 60 * 1000,  // 2 hr
            -1,               // custom
    };
    private final String[] DURATION_LABELS = {"10 分鐘", "30 分鐘", "1 小時", "2 小時", "自訂..."};
    private long customDurationMs = 60 * 60 * 1000; // default 1 hour

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UIHelper.BG_TOP_BAR);

        dbHelper = new GpsMockDbHelper(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        geocoder = new Geocoder(this, Locale.getDefault());
        uiHandler = new Handler(Looper.getMainLooper());

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

        // Start point card
        content.addView(UIHelper.sectionHeader(this, "START POINT"));
        LinearLayout startCard = UIHelper.card(this);

        // Current GPS location row
        TextView gpsLabel = new TextView(this);
        gpsLabel.setText("目前 GPS 位置:");
        gpsLabel.setTextSize(14);
        gpsLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        startCard.addView(gpsLabel);

        LinearLayout gpsRow = new LinearLayout(this);
        gpsRow.setOrientation(LinearLayout.HORIZONTAL);
        gpsRow.setGravity(Gravity.CENTER_VERTICAL);

        currentLocText = new TextView(this);
        currentLocText.setText("取得中...");
        currentLocText.setTextSize(14);
        currentLocText.setTextColor(UIHelper.TEXT_PRIMARY);
        currentLocText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        gpsRow.addView(currentLocText);

        Button useGpsBtn = UIHelper.smallButton(this, "使用", UIHelper.ACCENT_GREEN);
        useGpsBtn.setOnClickListener(v -> {
            if (hasLocation) {
                useCurrentAsStart = true;
                hasStartPoint = true;
                startLat = currentLat;
                startLng = currentLng;
                startName = "目前位置";
                startResultText.setText(String.format("使用目前位置 (%.4f, %.4f)", currentLat, currentLng));
                startResultText.setTextColor(UIHelper.ACCENT_GREEN);
                startInput.setText("");
            } else {
                Toast.makeText(this, "尚未取得 GPS 位置", Toast.LENGTH_SHORT).show();
            }
        });
        gpsRow.addView(useGpsBtn);

        Button refreshLocBtn = UIHelper.smallButton(this, "重新定位", UIHelper.ACCENT_BLUE);
        refreshLocBtn.setOnClickListener(v -> requestLocation());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshLp.setMargins(UIHelper.dp(this, 4), 0, 0, 0);
        refreshLocBtn.setLayoutParams(refreshLp);
        gpsRow.addView(refreshLocBtn);

        startCard.addView(gpsRow);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(UIHelper.DIVIDER);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIHelper.dp(this, 1));
        divLp.setMargins(0, UIHelper.dp(this, 12), 0, UIHelper.dp(this, 12));
        divider.setLayoutParams(divLp);
        startCard.addView(divider);

        // Custom start point search
        TextView customLabel = new TextView(this);
        customLabel.setText("或搜尋自訂起點:");
        customLabel.setTextSize(14);
        customLabel.setTextColor(UIHelper.TEXT_SECONDARY);
        startCard.addView(customLabel);

        LinearLayout startSearchRow = new LinearLayout(this);
        startSearchRow.setOrientation(LinearLayout.HORIZONTAL);
        startSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams startSearchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startSearchLp.setMargins(0, UIHelper.dp(this, 4), 0, 0);
        startSearchRow.setLayoutParams(startSearchLp);

        startInput = UIHelper.styledInput(this, "例: 台北車站");
        startInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        startSearchRow.addView(startInput);

        Button startSearchBtn = UIHelper.smallButton(this, "搜尋", UIHelper.ACCENT_BLUE);
        LinearLayout.LayoutParams startSearchBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UIHelper.dp(this, 44));
        startSearchBtnLp.setMargins(UIHelper.dp(this, 8), 0, 0, 0);
        startSearchBtn.setLayoutParams(startSearchBtnLp);
        startSearchBtn.setOnClickListener(v -> searchStartPoint());
        startSearchRow.addView(startSearchBtn);

        startCard.addView(startSearchRow);

        // Start point result
        startResultText = new TextView(this);
        startResultText.setText("預設使用目前 GPS 位置");
        startResultText.setTextSize(13);
        startResultText.setTextColor(UIHelper.TEXT_HINT);
        startResultText.setPadding(0, UIHelper.dp(this, 8), 0, 0);
        startCard.addView(startResultText);

        content.addView(startCard);

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
        durationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (DURATION_VALUES[position] == -1) {
                    showCustomDurationDialog();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        durRow.addView(durationSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        destCard.addView(durRow);

        // Follow roads checkbox
        followRoadsCheck = new CheckBox(this);
        followRoadsCheck.setText("沿道路移動（使用 OSRM 路線）");
        followRoadsCheck.setTextColor(UIHelper.TEXT_PRIMARY);
        followRoadsCheck.setTextSize(14);
        followRoadsCheck.setChecked(true);
        followRoadsCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(UIHelper.ACCENT_BLUE));
        LinearLayout.LayoutParams followLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        followLp.setMargins(0, UIHelper.dp(this, 8), 0, 0);
        followRoadsCheck.setLayoutParams(followLp);
        destCard.addView(followRoadsCheck);

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

        // Real-time mock status
        mockStatusText = new TextView(this);
        mockStatusText.setTextSize(13);
        mockStatusText.setTextColor(UIHelper.ACCENT_GREEN);
        mockStatusText.setGravity(Gravity.CENTER);
        mockStatusText.setTypeface(Typeface.MONOSPACE);
        mockStatusText.setPadding(0, UIHelper.dp(this, 4), 0, UIHelper.dp(this, 8));
        content.addView(mockStatusText);

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
        // Start status polling
        uiHandler.post(statusUpdateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(statusUpdateRunnable);
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

    private void searchStartPoint() {
        String query = startInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "請輸入起點名稱", Toast.LENGTH_SHORT).show();
            return;
        }

        startResultText.setText("搜尋中...");
        startResultText.setTextColor(UIHelper.TEXT_SECONDARY);

        new Thread(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, 5);
                runOnUiThread(() -> {
                    if (results == null || results.isEmpty()) {
                        startResultText.setText("找不到「" + query + "」");
                        startResultText.setTextColor(UIHelper.ACCENT_RED);
                        hasStartPoint = false;
                    } else if (results.size() == 1) {
                        selectStartAddress(results.get(0));
                    } else {
                        showStartAddressPicker(results);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    startResultText.setText("搜尋失敗: " + e.getMessage());
                    startResultText.setTextColor(UIHelper.ACCENT_RED);
                    hasStartPoint = false;
                    AppLog.e("GpsMock", "起點搜尋失敗: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showStartAddressPicker(List<Address> addresses) {
        String[] items = new String[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            items[i] = formatAddress(addresses.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("選擇起點")
                .setItems(items, (dialog, which) -> selectStartAddress(addresses.get(which)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void selectStartAddress(Address address) {
        startLat = address.getLatitude();
        startLng = address.getLongitude();
        startName = formatAddress(address);
        hasStartPoint = true;
        useCurrentAsStart = false;

        startResultText.setText(String.format("%s\n(%.6f, %.6f)", startName, startLat, startLng));
        startResultText.setTextColor(UIHelper.ACCENT_GREEN);

        AppLog.i("GpsMock", "選擇起點: " + startName);
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
        if (GpsMockService.isRunning()) {
            Intent stopIntent = new Intent(this, GpsMockService.class);
            stopIntent.setAction(GpsMockService.ACTION_STOP);
            startService(stopIntent);
            uiHandler.removeCallbacks(statusUpdateRunnable);
            mockStatusText.setText("");
            updateButtonState();
            Toast.makeText(this, "已停止模擬", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine start point
        double actualStartLat, actualStartLng;
        if (hasStartPoint) {
            actualStartLat = startLat;
            actualStartLng = startLng;
        } else if (hasLocation) {
            // Default to current GPS
            actualStartLat = currentLat;
            actualStartLng = currentLng;
        } else {
            Toast.makeText(this, "請先設定起點（使用 GPS 或搜尋地點）", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasDestination) {
            Toast.makeText(this, "請先搜尋並選擇目的地", Toast.LENGTH_SHORT).show();
            return;
        }

        int durationIdx = durationSpinner.getSelectedItemPosition();
        long duration = DURATION_VALUES[durationIdx] == -1 ? customDurationMs : DURATION_VALUES[durationIdx];
        double distance = haversineDistance(actualStartLat, actualStartLng, destLat, destLng);

        String startDesc = hasStartPoint ? startName : "目前位置";
        AppLog.i("GpsMock", String.format("準備模擬: %s -> %s, 距離 %.1fkm, 時間 %d分鐘",
                startDesc, destName, distance / 1000, duration / 60000));

        Intent intent = new Intent(this, GpsMockService.class);
        intent.putExtra(GpsMockService.EXTRA_START_LAT, actualStartLat);
        intent.putExtra(GpsMockService.EXTRA_START_LNG, actualStartLng);
        intent.putExtra(GpsMockService.EXTRA_END_LAT, destLat);
        intent.putExtra(GpsMockService.EXTRA_END_LNG, destLng);
        intent.putExtra(GpsMockService.EXTRA_DURATION_MS, duration);
        intent.putExtra(GpsMockService.EXTRA_FOLLOW_ROADS, followRoadsCheck.isChecked());

        startForegroundService(intent);
        Toast.makeText(this, "開始模擬位置", Toast.LENGTH_SHORT).show();

        // Start status update polling
        uiHandler.postDelayed(() -> {
            updateButtonState();
            if (GpsMockService.isRunning()) {
                uiHandler.post(statusUpdateRunnable);
            }
        }, 500);
    }

    private void updateButtonState() {
        boolean running = GpsMockService.isRunning();
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
            mockStatusText.setText("");
        }
    }

    private static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
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

    private void showCustomDurationDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("分鐘數");
        input.setText(String.valueOf(customDurationMs / 60000));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("自訂移動時間")
                .setMessage("請輸入移動時間（分鐘）")
                .setView(input)
                .setPositiveButton("確定", (d, w) -> {
                    try {
                        int minutes = Integer.parseInt(input.getText().toString().trim());
                        if (minutes < 1) minutes = 1;
                        if (minutes > 1440) minutes = 1440; // max 24 hours
                        customDurationMs = minutes * 60 * 1000L;
                        Toast.makeText(this, "已設定 " + minutes + " 分鐘", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "請輸入有效數字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", (d, w) -> {
                    // Reset to default option
                    durationSpinner.setSelection(0);
                })
                .show();
    }
}
