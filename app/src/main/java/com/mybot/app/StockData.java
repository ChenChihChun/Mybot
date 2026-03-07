package com.mybot.app;

import java.util.ArrayList;
import java.util.List;

public class StockData {

    public static class StockQuote {
        public String code;
        public String name;
        public double currentPrice;
        public double open;
        public double high;
        public double low;
        public double prevClose;
        public long volume;
        public String time;

        public double getChangePercent() {
            if (prevClose <= 0) return 0;
            return (currentPrice - prevClose) / prevClose * 100;
        }

        public double getChange() {
            return currentPrice - prevClose;
        }
    }

    public static class TickRecord {
        public long timestamp;
        public double price;
        public long volume;

        public TickRecord(long timestamp, double price, long volume) {
            this.timestamp = timestamp;
            this.price = price;
            this.volume = volume;
        }
    }

    public static class CandleBar {
        public long startTime;
        public double open;
        public double high;
        public double low;
        public double close;
        public long volume;

        public CandleBar(long startTime, double open, double high, double low, double close, long volume) {
            this.startTime = startTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    public static List<CandleBar> aggregateCandles(List<TickRecord> ticks, int intervalMinutes) {
        List<CandleBar> candles = new ArrayList<>();
        if (ticks == null || ticks.isEmpty()) return candles;

        long intervalMs = intervalMinutes * 60_000L;
        CandleBar current = null;

        for (TickRecord tick : ticks) {
            long slot = (tick.timestamp / intervalMs) * intervalMs;
            if (current == null || current.startTime != slot) {
                if (current != null) candles.add(current);
                current = new CandleBar(slot, tick.price, tick.price, tick.price, tick.price, tick.volume);
            } else {
                current.high = Math.max(current.high, tick.price);
                current.low = Math.min(current.low, tick.price);
                current.close = tick.price;
                current.volume += tick.volume;
            }
        }
        if (current != null) candles.add(current);
        return candles;
    }

    public static List<CandleBar> aggregateWeekly(List<CandleBar> dailyCandles) {
        List<CandleBar> weekly = new ArrayList<>();
        if (dailyCandles == null || dailyCandles.isEmpty()) return weekly;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        CandleBar current = null;
        int currentWeek = -1;
        int currentYear = -1;

        for (CandleBar day : dailyCandles) {
            cal.setTimeInMillis(day.startTime);
            int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
            int year = cal.get(java.util.Calendar.YEAR);

            if (current == null || week != currentWeek || year != currentYear) {
                if (current != null) weekly.add(current);
                current = new CandleBar(day.startTime, day.open, day.high, day.low, day.close, day.volume);
                currentWeek = week;
                currentYear = year;
            } else {
                current.high = Math.max(current.high, day.high);
                current.low = Math.min(current.low, day.low);
                current.close = day.close;
                current.volume += day.volume;
            }
        }
        if (current != null) weekly.add(current);
        return weekly;
    }

    public static List<CandleBar> aggregateMonthly(List<CandleBar> dailyCandles) {
        List<CandleBar> monthly = new ArrayList<>();
        if (dailyCandles == null || dailyCandles.isEmpty()) return monthly;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        CandleBar current = null;
        int currentMonth = -1;
        int currentYear = -1;

        for (CandleBar day : dailyCandles) {
            cal.setTimeInMillis(day.startTime);
            int month = cal.get(java.util.Calendar.MONTH);
            int year = cal.get(java.util.Calendar.YEAR);

            if (current == null || month != currentMonth || year != currentYear) {
                if (current != null) monthly.add(current);
                current = new CandleBar(day.startTime, day.open, day.high, day.low, day.close, day.volume);
                currentMonth = month;
                currentYear = year;
            } else {
                current.high = Math.max(current.high, day.high);
                current.low = Math.min(current.low, day.low);
                current.close = day.close;
                current.volume += day.volume;
            }
        }
        if (current != null) monthly.add(current);
        return monthly;
    }

    public static double[] calcMA(List<CandleBar> candles, int period) {
        double[] ma = new double[candles.size()];
        double sum = 0;
        for (int i = 0; i < candles.size(); i++) {
            sum += candles.get(i).close;
            if (i >= period) sum -= candles.get(i - period).close;
            if (i >= period - 1) {
                ma[i] = sum / period;
            } else {
                ma[i] = Double.NaN;
            }
        }
        return ma;
    }

    public static double[][] calcBollinger(List<CandleBar> candles, int period, double multiplier) {
        double[] middle = calcMA(candles, period);
        double[] upper = new double[candles.size()];
        double[] lower = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            if (Double.isNaN(middle[i])) {
                upper[i] = Double.NaN;
                lower[i] = Double.NaN;
                continue;
            }
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close - middle[i];
                sumSq += diff * diff;
            }
            double std = Math.sqrt(sumSq / period);
            upper[i] = middle[i] + multiplier * std;
            lower[i] = middle[i] - multiplier * std;
        }
        return new double[][]{upper, middle, lower};
    }

    public static double[] calcRSI(List<CandleBar> candles, int period) {
        double[] rsi = new double[candles.size()];
        if (candles.size() < 2) return rsi;

        double avgGain = 0, avgLoss = 0;

        for (int i = 0; i < candles.size(); i++) {
            if (i == 0) {
                rsi[i] = Double.NaN;
                continue;
            }

            double change = candles.get(i).close - candles.get(i - 1).close;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            if (i <= period) {
                avgGain += gain;
                avgLoss += loss;
                if (i == period) {
                    avgGain /= period;
                    avgLoss /= period;
                    if (avgLoss == 0) rsi[i] = 100;
                    else rsi[i] = 100 - 100 / (1 + avgGain / avgLoss);
                } else {
                    rsi[i] = Double.NaN;
                }
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;
                if (avgLoss == 0) rsi[i] = 100;
                else rsi[i] = 100 - 100 / (1 + avgGain / avgLoss);
            }
        }
        return rsi;
    }
}
