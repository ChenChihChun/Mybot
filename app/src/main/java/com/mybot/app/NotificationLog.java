package com.mybot.app;

public class NotificationLog {
    public long timestamp;
    public String sourceApp;
    public String title;
    public String content;
    public String source; // "notification" or "sms"

    // AI analysis results
    public boolean analyzed;
    public boolean isExpense;
    public double amount;
    public String currency;
    public String category;
    public String merchant;
    public String description;
    public double confidence;
    public boolean offline;

    public NotificationLog(String sourceApp, String title, String content, String source) {
        this.timestamp = System.currentTimeMillis();
        this.sourceApp = sourceApp;
        this.title = title;
        this.content = content;
        this.source = source;
        this.analyzed = false;
        this.offline = false;
    }
}
