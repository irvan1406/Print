package com.cetakpro.print;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        // Tampilkan pesan singkat saat admin diaktifkan
        Toast.makeText(context, "VanNota aman terkunci", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        // Ini akan jalan jika seseorang mencoba mematikan mode admin.
        // Kita bisa kirim peringatan ke Telegram.
        TelegramSender.sendMessage(context, "⚠️ PERINGATAN!", "Seseorang mencoba membuka kunci VanNota!");
    }
}
