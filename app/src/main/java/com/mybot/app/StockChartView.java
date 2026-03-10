package com.mybot.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.List;

public class StockChartView extends View {

    private List<StockData.CandleBar> candles;
    private double[] ma5, ma10, ma20;
    private double[][] bollinger; // [upper, middle, lower]
    private double[] rsi5, rsi10;
    private double currentPrice = 0;
    private double volMaLastValue = 0;

    // Visibility toggles
    public boolean showMA = true;
    public boolean showBBand = true;
    public boolean showRSI = true;
    public boolean showVol = true;

    // Zoom & pan
    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private boolean isDragging = false;
    private int activePointerId = -1;

    private final Paint paintUp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDown = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMa5 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMa10 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMa20 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBBand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBBandFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRsi5 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRsi10 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPriceLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintVolUp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintVolDown = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRefLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintVolMa = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int COLOR_UP = Color.parseColor("#EF5350");    // 紅漲
    private static final int COLOR_DOWN = Color.parseColor("#66BB6A");  // 綠跌
    private static final int COLOR_MA5 = Color.parseColor("#4FC3F7");
    private static final int COLOR_MA10 = Color.parseColor("#FFA726");
    private static final int COLOR_MA20 = Color.parseColor("#AB47BC");
    private static final int COLOR_BBAND = Color.parseColor("#80DEEA");
    private static final int COLOR_RSI5 = Color.parseColor("#4FC3F7");
    private static final int COLOR_RSI10 = Color.parseColor("#FFA726");
    private static final int COLOR_VOL_MA = Color.parseColor("#FFD54F");  // 黃色
    private static final int COLOR_GRID = Color.parseColor("#1E3040");
    private static final int COLOR_TEXT = Color.parseColor("#90A4AE");
    private static final int COLOR_PRICE_LINE = Color.parseColor("#4FC3F7");

    private float density;

    public StockChartView(Context context) {
        super(context);
        init();
    }

    public StockChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float oldScale = scaleFactor;
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f));
                // Adjust translateX to zoom around focus point
                float focusX = detector.getFocusX();
                translateX = focusX - (focusX - translateX) * (scaleFactor / oldScale);
                clampTranslateX();
                invalidate();
                return true;
            }
        });

        paintUp.setColor(COLOR_UP);
        paintUp.setStyle(Paint.Style.FILL);
        paintDown.setColor(COLOR_DOWN);
        paintDown.setStyle(Paint.Style.FILL);

        paintMa5.setColor(COLOR_MA5);
        paintMa5.setStyle(Paint.Style.STROKE);
        paintMa5.setStrokeWidth(1.5f * density);

        paintMa10.setColor(COLOR_MA10);
        paintMa10.setStyle(Paint.Style.STROKE);
        paintMa10.setStrokeWidth(1.5f * density);

        paintMa20.setColor(COLOR_MA20);
        paintMa20.setStyle(Paint.Style.STROKE);
        paintMa20.setStrokeWidth(1.5f * density);

        paintBBand.setColor(COLOR_BBAND);
        paintBBand.setStyle(Paint.Style.STROKE);
        paintBBand.setStrokeWidth(1f * density);

        paintBBandFill.setColor(Color.argb(25, 128, 222, 234));
        paintBBandFill.setStyle(Paint.Style.FILL);

        paintRsi5.setColor(COLOR_RSI5);
        paintRsi5.setStyle(Paint.Style.STROKE);
        paintRsi5.setStrokeWidth(1.5f * density);

        paintRsi10.setColor(COLOR_RSI10);
        paintRsi10.setStyle(Paint.Style.STROKE);
        paintRsi10.setStrokeWidth(1.5f * density);

        paintGrid.setColor(COLOR_GRID);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(0.5f * density);

        paintText.setColor(COLOR_TEXT);
        paintText.setTextSize(10 * density);

        paintPriceLine.setColor(COLOR_PRICE_LINE);
        paintPriceLine.setStyle(Paint.Style.STROKE);
        paintPriceLine.setStrokeWidth(1f * density);
        paintPriceLine.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));

        paintVolUp.setColor(Color.argb(180, 239, 83, 80));    // 紅漲
        paintVolUp.setStyle(Paint.Style.FILL);
        paintVolDown.setColor(Color.argb(180, 102, 187, 106));  // 綠跌
        paintVolDown.setStyle(Paint.Style.FILL);

        paintRefLine.setColor(Color.parseColor("#2E4050"));
        paintRefLine.setStyle(Paint.Style.STROKE);
        paintRefLine.setStrokeWidth(0.5f * density);
        paintRefLine.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        paintVolMa.setColor(COLOR_VOL_MA);
        paintVolMa.setStyle(Paint.Style.STROKE);
        paintVolMa.setStrokeWidth(1.2f * density);
        paintVolMa.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));

        setBackgroundColor(Color.parseColor("#0F1923"));
    }

    public void setData(List<StockData.CandleBar> candles, double[] ma5, double[] ma10, double[] ma20,
                        double[][] bollinger, double[] rsi5, double[] rsi10, double currentPrice) {
        this.candles = candles;
        this.ma5 = ma5;
        this.ma10 = ma10;
        this.ma20 = ma20;
        this.bollinger = bollinger;
        this.rsi5 = rsi5;
        this.rsi10 = rsi10;
        this.currentPrice = currentPrice;
        invalidate();
    }

    public void resetZoom() {
        scaleFactor = 1.0f;
        translateX = 0f;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastTouchX = event.getX();
                isDragging = false;
                // Request parent to not intercept touch (for ScrollView)
                getParent().requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) break;
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex < 0) break;
                float x = event.getX(pointerIndex);
                float dx = x - lastTouchX;
                if (Math.abs(dx) > 3 * density) isDragging = true;
                if (isDragging && scaleFactor > 1.0f) {
                    translateX += dx;
                    clampTranslateX();
                    invalidate();
                }
                lastTouchX = x;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = -1;
                isDragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                int actionIndex = event.getActionIndex();
                if (event.getPointerId(actionIndex) == activePointerId) {
                    int newIndex = actionIndex == 0 ? 1 : 0;
                    if (newIndex < event.getPointerCount()) {
                        activePointerId = event.getPointerId(newIndex);
                        lastTouchX = event.getX(newIndex);
                    }
                }
                break;
        }
        return true;
    }

    private void clampTranslateX() {
        float w = getWidth();
        float rightMargin = 55 * density;
        float margin = 8 * density;
        float chartW = w - margin - rightMargin;
        float scaledW = chartW * scaleFactor;
        float minTx = -(scaledW - chartW);
        float maxTx = 0;
        if (translateX < minTx) translateX = minTx;
        if (translateX > maxTx) translateX = maxTx;
    }

    // Calculate region heights based on which panels are visible
    private float getKHeightRatio() {
        if (showVol && showRSI) return 0.55f;
        if (showVol || showRSI) return 0.70f;
        return 0.95f;
    }

    private float getVolHeightRatio() {
        if (!showVol) return 0f;
        if (showRSI) return 0.20f;
        return 0.25f;
    }

    private float getRsiHeightRatio() {
        if (!showRSI) return 0f;
        if (showVol) return 0.20f;
        return 0.25f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (candles == null || candles.isEmpty()) {
            paintText.setTextSize(14 * density);
            paintText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Loading...", getWidth() / 2f, getHeight() / 2f, paintText);
            paintText.setTextAlign(Paint.Align.LEFT);
            paintText.setTextSize(10 * density);
            return;
        }

        float w = getWidth();
        float h = getHeight();
        float margin = 8 * density;
        float rightMargin = 55 * density;
        float chartW = w - margin - rightMargin;

        // Dynamic region sizing
        float gap = 8 * density;
        float usableH = h - margin * 2;
        float kRatio = getKHeightRatio();
        float volRatio = getVolHeightRatio();
        float rsiRatio = getRsiHeightRatio();

        float kTop = margin;
        float kHeight = usableH * kRatio;
        float kBottom = kTop + kHeight;

        float volTop = kBottom + (showVol ? gap : 0);
        float volHeight = usableH * volRatio;
        float volBottom = volTop + volHeight;

        float rsiTop = (showVol ? volBottom : kBottom) + (showRSI ? gap : 0);
        float rsiHeight = usableH * rsiRatio;
        float rsiBottom = rsiTop + rsiHeight;

        int count = candles.size();
        float scaledChartW = chartW * scaleFactor;
        float candleWidth = scaledChartW / Math.max(count, 1);
        float bodyWidth = Math.max(candleWidth * 0.6f, 2 * density);

        // Find price range
        double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
        long maxVol = 0;
        for (StockData.CandleBar c : candles) {
            minPrice = Math.min(minPrice, c.low);
            maxPrice = Math.max(maxPrice, c.high);
            maxVol = Math.max(maxVol, c.volume);
        }
        if (showBBand && bollinger != null) {
            for (int i = 0; i < count; i++) {
                if (!Double.isNaN(bollinger[0][i])) maxPrice = Math.max(maxPrice, bollinger[0][i]);
                if (!Double.isNaN(bollinger[2][i])) minPrice = Math.min(minPrice, bollinger[2][i]);
            }
        }
        double pricePad = (maxPrice - minPrice) * 0.05;
        if (pricePad < 0.5) pricePad = 0.5;
        minPrice -= pricePad;
        maxPrice += pricePad;
        double priceRange = maxPrice - minPrice;
        if (priceRange <= 0) priceRange = 1;

        // Clip chart area
        canvas.save();
        canvas.clipRect(margin, 0, w - rightMargin, h);

        // Apply zoom/pan transform for candle area
        canvas.save();
        canvas.translate(translateX, 0);

        // Draw grid lines for K area (unscaled X, so draw before translate — actually draw after clip)
        // We'll draw grid outside the translate
        canvas.restore(); // restore translate for grid

        // Draw grid lines
        for (int i = 0; i <= 3; i++) {
            float y = kTop + kHeight * i / 3f;
            canvas.drawLine(margin, y, w - rightMargin, y, paintGrid);
        }

        // Re-apply translate for scaled content
        canvas.save();
        canvas.translate(translateX, 0);

        // Draw Bollinger bands fill
        if (showBBand && bollinger != null) {
            Path fillPath = new Path();
            boolean started = false;
            for (int i = 0; i < count; i++) {
                if (Double.isNaN(bollinger[0][i])) continue;
                float x = margin + candleWidth * i + candleWidth / 2;
                float yU = kTop + (float) ((maxPrice - bollinger[0][i]) / priceRange * kHeight);
                if (!started) { fillPath.moveTo(x, yU); started = true; }
                else fillPath.lineTo(x, yU);
            }
            for (int i = count - 1; i >= 0; i--) {
                if (Double.isNaN(bollinger[2][i])) continue;
                float x = margin + candleWidth * i + candleWidth / 2;
                float yL = kTop + (float) ((maxPrice - bollinger[2][i]) / priceRange * kHeight);
                fillPath.lineTo(x, yL);
            }
            fillPath.close();
            canvas.drawPath(fillPath, paintBBandFill);
            drawLine(canvas, bollinger[0], margin, candleWidth, kTop, kHeight, maxPrice, priceRange, count, paintBBand);
            drawLine(canvas, bollinger[2], margin, candleWidth, kTop, kHeight, maxPrice, priceRange, count, paintBBand);
        }

        // Draw MA lines
        if (showMA) {
            if (ma5 != null) drawLine(canvas, ma5, margin, candleWidth, kTop, kHeight, maxPrice, priceRange, count, paintMa5);
            if (ma10 != null) drawLine(canvas, ma10, margin, candleWidth, kTop, kHeight, maxPrice, priceRange, count, paintMa10);
            if (ma20 != null) drawLine(canvas, ma20, margin, candleWidth, kTop, kHeight, maxPrice, priceRange, count, paintMa20);
        }

        // Draw candles
        for (int i = 0; i < count; i++) {
            StockData.CandleBar c = candles.get(i);
            boolean up = c.close >= c.open;
            Paint paint = up ? paintUp : paintDown;

            float x = margin + candleWidth * i + candleWidth / 2;
            float yOpen = kTop + (float) ((maxPrice - c.open) / priceRange * kHeight);
            float yClose = kTop + (float) ((maxPrice - c.close) / priceRange * kHeight);
            float yHigh = kTop + (float) ((maxPrice - c.high) / priceRange * kHeight);
            float yLow = kTop + (float) ((maxPrice - c.low) / priceRange * kHeight);

            canvas.drawLine(x, yHigh, x, yLow, paint);
            float top = Math.min(yOpen, yClose);
            float bottom = Math.max(yOpen, yClose);
            if (bottom - top < 1) bottom = top + 1;
            RectF body = new RectF(x - bodyWidth / 2, top, x + bodyWidth / 2, bottom);
            if (up) {
                Paint strokePaint = new Paint(paint);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(1 * density);
                canvas.drawRect(body, strokePaint);
            } else {
                canvas.drawRect(body, paint);
            }
        }

        // Volume bars + MA5
        if (showVol && maxVol > 0) {
            for (int i = 0; i < count; i++) {
                StockData.CandleBar c = candles.get(i);
                boolean up = c.close >= c.open;
                float x = margin + candleWidth * i + candleWidth / 2;
                float barH = (float) ((double) c.volume / maxVol * volHeight);
                RectF bar = new RectF(x - bodyWidth / 2, volBottom - barH, x + bodyWidth / 2, volBottom);
                canvas.drawRect(bar, up ? paintVolUp : paintVolDown);
            }

            // Volume MA5 dashed line
            int vMaPeriod = 5;
            Path volMaPath = new Path();
            boolean volMaStarted = false;
            double lastVolMa = 0;
            long volSum = 0;
            for (int i = 0; i < count; i++) {
                volSum += candles.get(i).volume;
                if (i >= vMaPeriod) volSum -= candles.get(i - vMaPeriod).volume;
                if (i >= vMaPeriod - 1) {
                    double avg = (double) volSum / vMaPeriod;
                    float x = margin + candleWidth * i + candleWidth / 2;
                    float y = volBottom - (float) (avg / maxVol * volHeight);
                    if (!volMaStarted) { volMaPath.moveTo(x, y); volMaStarted = true; }
                    else volMaPath.lineTo(x, y);
                    lastVolMa = avg;
                }
            }
            canvas.drawPath(volMaPath, paintVolMa);
            volMaLastValue = lastVolMa;
        }

        // RSI lines (scaled)
        if (showRSI) {
            drawRsiLine(canvas, rsi5, margin, candleWidth, rsiTop, rsiHeight, count, paintRsi5);
            drawRsiLine(canvas, rsi10, margin, candleWidth, rsiTop, rsiHeight, count, paintRsi10);
        }

        canvas.restore(); // restore translate
        canvas.restore(); // restore clip

        // Draw price labels (right margin, not clipped/translated)
        for (int i = 0; i <= 3; i++) {
            float y = kTop + kHeight * i / 3f;
            double price = maxPrice - priceRange * i / 3;
            canvas.drawText(formatPrice(price), w - rightMargin + 4 * density, y + 4 * density, paintText);
        }

        // Current price dashed line (full width, not translated)
        if (currentPrice > 0 && currentPrice >= minPrice && currentPrice <= maxPrice) {
            float yPrice = kTop + (float) ((maxPrice - currentPrice) / priceRange * kHeight);
            Path dashPath = new Path();
            dashPath.moveTo(margin, yPrice);
            dashPath.lineTo(w - rightMargin, yPrice);
            canvas.drawPath(dashPath, paintPriceLine);
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(COLOR_PRICE_LINE);
            labelPaint.setTextSize(10 * density);
            canvas.drawText(formatPrice(currentPrice), w - rightMargin + 4 * density, yPrice + 4 * density, labelPaint);
        }

        // Volume grid lines + MA label
        if (showVol) {
            canvas.drawLine(margin, volTop, w - rightMargin, volTop, paintGrid);
            canvas.drawLine(margin, volBottom, w - rightMargin, volBottom, paintGrid);

            // Volume MA5 value label on right margin
            if (volMaLastValue > 0 && maxVol > 0) {
                float yMa = volBottom - (float) (volMaLastValue / maxVol * volHeight);
                paintText.setTextSize(8 * density);
                paintText.setColor(COLOR_VOL_MA);
                canvas.drawText(formatVolume(volMaLastValue), w - rightMargin + 4 * density, yMa + 3 * density, paintText);
                paintText.setColor(COLOR_TEXT);
                paintText.setTextSize(10 * density);
            }
        }

        // RSI grid + ref lines
        if (showRSI) {
            canvas.drawLine(margin, rsiTop, w - rightMargin, rsiTop, paintGrid);
            canvas.drawLine(margin, rsiBottom, w - rightMargin, rsiBottom, paintGrid);
            float y70 = rsiTop + rsiHeight * (100 - 70) / 100f;
            float y30 = rsiTop + rsiHeight * (100 - 30) / 100f;
            canvas.drawLine(margin, y70, w - rightMargin, y70, paintRefLine);
            canvas.drawLine(margin, y30, w - rightMargin, y30, paintRefLine);
            paintText.setTextSize(9 * density);
            canvas.drawText("70", w - rightMargin + 4 * density, y70 + 3 * density, paintText);
            canvas.drawText("30", w - rightMargin + 4 * density, y30 + 3 * density, paintText);
            paintText.setTextSize(10 * density);

            // RSI labels
            paintText.setTextSize(9 * density);
            float rsiLabelX = margin + 4 * density;
            paintText.setColor(COLOR_RSI5);
            canvas.drawText("RSI5", rsiLabelX, rsiTop + 12 * density, paintText);
            rsiLabelX += 38 * density;
            paintText.setColor(COLOR_RSI10);
            canvas.drawText("RSI10", rsiLabelX, rsiTop + 12 * density, paintText);
            paintText.setColor(COLOR_TEXT);
            paintText.setTextSize(10 * density);
        }

        // Legend
        float legendY = kTop + 12 * density;
        float legendX = margin + 4 * density;
        paintText.setTextSize(9 * density);
        if (showMA) {
            paintText.setColor(COLOR_MA5);
            canvas.drawText("MA5", legendX, legendY, paintText);
            legendX += 35 * density;
            paintText.setColor(COLOR_MA10);
            canvas.drawText("MA10", legendX, legendY, paintText);
            legendX += 40 * density;
            paintText.setColor(COLOR_MA20);
            canvas.drawText("MA20", legendX, legendY, paintText);
            legendX += 42 * density;
        }
        if (showBBand) {
            paintText.setColor(COLOR_BBAND);
            canvas.drawText("BBand", legendX, legendY, paintText);
        }
        paintText.setColor(COLOR_TEXT);
        paintText.setTextSize(10 * density);

        // Zoom indicator
        if (scaleFactor > 1.05f) {
            paintText.setTextSize(9 * density);
            paintText.setColor(UIHelper.TEXT_HINT);
            String zoomText = String.format("%.1fx", scaleFactor);
            canvas.drawText(zoomText, w - rightMargin - 30 * density, kTop + 12 * density, paintText);
            paintText.setColor(COLOR_TEXT);
            paintText.setTextSize(10 * density);
        }
    }

    private void drawRsiLine(Canvas canvas, double[] data, float margin, float candleWidth,
                             float rsiTop, float rsiHeight, int count, Paint paint) {
        if (data == null) return;
        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < count && i < data.length; i++) {
            if (Double.isNaN(data[i])) continue;
            float x = margin + candleWidth * i + candleWidth / 2;
            float y = rsiTop + rsiHeight * (float) (100 - data[i]) / 100f;
            if (!started) { path.moveTo(x, y); started = true; }
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private void drawLine(Canvas canvas, double[] values, float margin, float candleWidth,
                          float top, float height, double maxPrice, double priceRange, int count, Paint paint) {
        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < count && i < values.length; i++) {
            if (Double.isNaN(values[i])) continue;
            float x = margin + candleWidth * i + candleWidth / 2;
            float y = top + (float) ((maxPrice - values[i]) / priceRange * height);
            if (!started) { path.moveTo(x, y); started = true; }
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private String formatPrice(double price) {
        if (price >= 100) return String.format("%.0f", price);
        if (price >= 10) return String.format("%.1f", price);
        return String.format("%.2f", price);
    }

    private String formatVolume(double vol) {
        if (vol >= 1_0000_0000) return String.format("%.1f億", vol / 1_0000_0000);
        if (vol >= 1_0000) return String.format("%.0f萬", vol / 1_0000);
        return String.format("%.0f", vol);
    }
}
