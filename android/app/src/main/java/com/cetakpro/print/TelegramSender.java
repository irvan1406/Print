package com.cetakpro.print;

import android.content.Context;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TelegramSender {
    private static final String TAG = "TelegramSender";
    
    private static final String BOT_TOKEN = "8662155042:AAFRLkduh9r2FoOtt3TkqmTxNqAmWleibew";
    private static final String CHAT_ID = "1286411089";
    
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    public static void sendMessage(Context context, String title, String message) {
        new Thread(() -> {
            try {
                String fullText = title + "\n" + message;
                
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
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error: " + e.getMessage());
            }
        }).start();
    }
                  }
