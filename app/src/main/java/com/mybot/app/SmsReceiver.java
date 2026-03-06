package com.mybot.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) {
            return;
        }

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            String sender = smsMessage.getDisplayOriginatingAddress();
            String body = smsMessage.getDisplayMessageBody();

            String message = "[簡訊] 來自 " + sender + ": " + body;
            NotificationHelper.sendNotification(context, "Mybot - 簡訊監聽", message);

            // Log and analyze
            NotificationLog log = new NotificationLog(sender, "簡訊", body, "簡訊");
            MonitorActivity.logs.add(log);
            context.sendBroadcast(new Intent(MonitorActivity.ACTION_NEW_LOG));

            BridgeClient.analyze(log, result -> {
                context.sendBroadcast(new Intent(MonitorActivity.ACTION_NEW_LOG));
                if (result.analyzed && !result.offline
                        && result.isExpense && result.confidence >= 0.7) {
                    ExpenseDbHelper db = new ExpenseDbHelper(context);
                    db.insert(result.amount, result.currency, result.category,
                            result.merchant, result.description, "簡訊", body);
                    String pushMsg = String.format("已記錄消費: %s $%.0f [%s]",
                            result.merchant, result.amount, result.category);
                    NotificationHelper.sendNotification(context, "Mybot - 消費記錄", pushMsg);
                }
            });
        }
    }
}
