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
        }
    }
}
