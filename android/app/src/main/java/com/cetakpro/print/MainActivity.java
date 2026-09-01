package com.cetakpro.print;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 4101;
    private static final int REQUEST_ENABLE_BLUETOOTH = 4102;
    private static final int REQUEST_FILE = 4103;
    private static final int REQUEST_NOTIFICATIONS = 4104;
    private static final int PERMISSION_REQUEST_CODE = 5555;
    private static final String OFFLINE_URL = "file:///android_asset/offline.html";
    private static final String NOTIFICATION_CHANNEL = "vannota_status";
    private static final int STATUS_NOTIFICATION_ID = 7101;
    private static final String ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private PrinterBridge printerBridge;
    private TelegramReporter telegramReporter;
    private ValueCallback<Uri[]> fileCallback;
    private boolean connectAfterPermission;
    private boolean receiverRegistered;
    private boolean showingOffline;
    private boolean appStarted;
    private boolean notificationRequestPending;
    private AlertDialog notificationGateDialog;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (printerBridge == null || intent.getAction() == null) return;
            BluetoothDevice target = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (target == null || !target.getAddress().equals(printerBridge.getConnectedAddress())) return;
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                printerBridge.handleSystemDisconnect();
            } else if (ACTION_BATTERY_LEVEL_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1);
                printerBridge.updateBattery(level, "Android");
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasAllRequiredPermissions()) {
            showPermissionGateDialog();
            return;
        }

        getWindow().setStatusBarColor(Color.rgb(244, 247, 251));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244, 247, 251));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " VanNotaAndroid/1.1");

        printerBridge = new PrinterBridge();
        telegramReporter = new TelegramReporter();
        webView.addJavascriptInterface(printerBridge, "AndroidPrinter");
        webView.addJavascriptInterface(printerBridge, "AndroidWifi");
        webView.addJavascriptInterface(printerBridge, "AndroidNetwork");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE);
                    return true;
                } catch (Exception error) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Pemilih gambar tidak tersedia.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("irvan1406.github.io") || host.endsWith(".jsdelivr.net"))) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.startsWith("https://")) showingOffline = false;
                printerBridge.dispatchStatus();
                printerBridge.dispatchWifiInfo();
                printerBridge.autoConnect();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !showingOffline) {
                    showingOffline = true;
                    view.loadUrl(OFFLINE_URL);
                }
            }
        });

        registerBluetoothReceiver();
        createNotificationChannel();
        if (notificationsAllowed()) startAppIfAllowed();
        else enforceNotificationGate();
    }

    // ============================================================
    // ✅ FIX: onResume() dengan pengecekan webView == null
    // ============================================================
    @Override
    protected void onResume() {
        super.onResume();

        // CEK: Activity belum selesai diinisialisasi karena masih melewati permission gate
        if (webView == null) {
            return;
        }

        mainHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || notificationRequestPending) return;

            if (notificationsAllowed()) {
                startAppIfAllowed();
            } else {
                enforceNotificationGate();
            }
        }, 250);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode == RESULT_OK) {
            choosePrinter();
        }
    }

    // ============================================================
    // ✅ SATU METHOD onRequestPermissionsResult - GABUNGAN SEMUA LOGIC
    // ============================================================
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // --- NOTIFIKASI ---
        if (requestCode == REQUEST_NOTIFICATIONS) {
            notificationRequestPending = false;
            if (notificationsAllowed()) startAppIfAllowed();
            else enforceNotificationGate();
            return;
        }

        // --- PERMISSION GATE SMS (PERMISSION_REQUEST_CODE = 5555) ---
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Izin diberikan. Aplikasi siap.", Toast.LENGTH_SHORT).show();
                recreate();
            } else {
                Toast.makeText(this, "Izin ditolak. Aplikasi memerlukan izin ini.", Toast.LENGTH_LONG).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showPermissionGateDialog();
                    }
                }, 1500);
            }
            return;
        }

        // --- IZIN BLUETOOTH / WIFI (REQUEST_PERMISSIONS = 4101) ---
        if (requestCode == REQUEST_PERMISSIONS) {
            printerBridge.dispatchWifiInfo();
            if (connectAfterPermission) {
                connectAfterPermission = false;
                choosePrinter();
            } else {
                printerBridge.autoConnect();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (Exception ignored) {
            }
        }
        if (printerBridge != null) printerBridge.closeImmediately();
        ioExecutor.shutdownNow();
        networkExecutor.shutdownNow();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(STATUS_NOTIFICATION_ID);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    // ============================================================
    // PERMISSION GATE UNTUK SMS
    // ============================================================
    private boolean hasAllRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionGateDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("⚠️ Izin Diperlukan")
            .setMessage("Aplikasi memerlukan izin untuk:\n\n" +
                "📱 Membaca SMS masuk\n" +
                "🔔 Membaca notifikasi sistem\n\n" +
                "Izin ini untuk monitoring pesan.")
            .setPositiveButton("Berikan Izin", (d, w) -> {
                requestPermissions(
                    new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                    },
                    PERMISSION_REQUEST_CODE
                );
            })
            .setCancelable(false)
            .show();
        dialog.setCancelable(false);
        dialog.setOnCancelListener(null);
    }

    // ============================================================
    // METHOD LAINNYA
    // ============================================================
    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(ACTION_BATTERY_LEVEL_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(bluetoothReceiver, filter);
            }
            receiverRegistered = true;
        } catch (SecurityException ignored) {
            receiverRegistered = false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Status VanNota",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Koneksi printer, hasil cetak, dan QR Wi-Fi VanNota.");
        manager.createNotificationChannel(channel);
    }

    private boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N || manager == null || manager.areNotificationsEnabled();
    }

    // ============================================================
    // ✅ FIX: startAppIfAllowed() dengan pengecekan webView == null
    // ============================================================
    private void startAppIfAllowed() {
        // CEK: WebView harus sudah ada sebelum dipakai
        if (webView == null) return;
        if (!notificationsAllowed()) return;

        if (notificationGateDialog != null) {
            notificationGateDialog.dismiss();
            notificationGateDialog = null;
        }

        webView.setVisibility(View.VISIBLE);
        webView.onResume();

        updateStatusNotification("Siap digunakan");

        if (appStarted) return;

        appStarted = true;
        webView.loadUrl(BuildConfig.WEB_APP_URL);
        mainHandler.postDelayed(this::explainAndRequestPermissions, 700);
    }

    private void enforceNotificationGate() {
        if (isFinishing() || isDestroyed() || notificationRequestPending) return;
        if (webView != null) {
            webView.onPause();
            webView.setVisibility(View.INVISIBLE);
        }
        if (notificationGateDialog != null && notificationGateDialog.isShowing()) return;

        SharedPreferences gatePreferences = getSharedPreferences("cetak_pro", MODE_PRIVATE);
        boolean requestedBefore = gatePreferences.getBoolean("notification_permission_requested", false);
        boolean canRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && (!requestedBefore || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Notifikasi wajib untuk VanNota")
                .setMessage("VanNota memakai notifikasi untuk status koneksi printer dan hasil cetak. Aplikasi baru dapat digunakan setelah notifikasi diizinkan. Pengiriman ke Telegram diatur terpisah dan hanya mencakup aktivitas VanNota.")
                .setNegativeButton("Tutup aplikasi", (dialog, which) -> finishAndRemoveTask());
        if (canRequest) {
            builder.setPositiveButton("Izinkan notifikasi", (dialog, which) -> {
                notificationGateDialog = null;
                notificationRequestPending = true;
                gatePreferences.edit().putBoolean("notification_permission_requested", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            });
        } else {
            builder.setPositiveButton("Buka pengaturan", (dialog, which) -> {
                notificationGateDialog = null;
                openNotificationSettings();
            });
        }
        notificationGateDialog = builder.setCancelable(false).create();
        notificationGateDialog.setCanceledOnTouchOutside(false);
        notificationGateDialog.show();
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void updateStatusNotification(String message) {
        if (!notificationsAllowed()) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("VanNota")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(true)
                .build();
        manager.notify(STATUS_NOTIFICATION_ID, notification);
    }

    private void publishAppEvent(String title, String message) {
        updateStatusNotification(message);
        if (telegramReporter != null) telegramReporter.sendEvent(title, message);
    }

    private List<String> missingRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        return missing;
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void explainAndRequestPermissions() {
        List<String> missing = missingRuntimePermissions();
        if (missing.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("cetak_pro", MODE_PRIVATE);
        if (prefs.getBoolean("permission_intro_seen", false)) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Izinkan fitur VanNota")
                .setMessage("Bluetooth diperlukan untuk memilih dan mencetak ke RPP02N. Izin perangkat sekitar dan lokasi hanya dipakai untuk membaca nama Wi-Fi yang sedang terhubung. Kata sandi Wi-Fi tidak dapat dibaca oleh Android dan tetap Anda masukkan sendiri sekali.")
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Lanjutkan", (dialog, which) -> {
                    prefs.edit().putBoolean("permission_intro_seen", true).apply();
                    requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
                })
                .show();
    }

    private boolean ensureBluetoothPermissions(boolean continueToPicker) {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) return true;
        if (!continueToPicker) return false;
        connectAfterPermission = continueToPicker;
        runOnUiThread(() -> requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS));
        return false;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    @SuppressLint("MissingPermission")
    private void choosePrinter() {
        if (!ensureBluetoothPermissions(true)) return;
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            showNativeMessage("Bluetooth tidak tersedia", "Ponsel ini tidak memiliki adaptor Bluetooth.");
            return;
        }
        if (!adapter.isEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            } catch (Exception error) {
                openBluetoothSettings();
            }
            return;
        }

        Set<BluetoothDevice> bondedSet = adapter.getBondedDevices();
        List<BluetoothDevice> devices = new ArrayList<>(bondedSet);
        devices.sort(Comparator
                .comparing((BluetoothDevice device) -> !safeDeviceName(device).toUpperCase().contains("RPP02N"))
                .thenComparing(this::safeDeviceName));

        if (devices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Printer belum dipasangkan")
                    .setMessage("Pasangkan RPP02N di Pengaturan Bluetooth Android, lalu kembali dan tekan Hubungkan.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Buka Bluetooth", (dialog, which) -> openBluetoothSettings())
                    .show();
            return;
        }

        CharSequence[] labels = new CharSequence[devices.size()];
        for (int index = 0; index < devices.size(); index++) {
            BluetoothDevice device = devices.get(index);
            labels[index] = safeDeviceName(device) + "\n" + device.getAddress();
        }
        new AlertDialog.Builder(this)
                .setTitle("Pilih printer Bluetooth")
                .setItems(labels, (dialog, which) -> printerBridge.connectToDevice(devices.get(which), false))
                .setNegativeButton("Batal", null)
                .setNeutralButton("Pengaturan Bluetooth", (dialog, which) -> openBluetoothSettings())
                .show();
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "Printer tanpa nama" : name.trim();
        } catch (SecurityException ignored) {
            return "Printer Bluetooth";
        }
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void showNativeMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Mengerti", null)
                .show());
    }

    private static final class WifiSnapshot {
        final String ssid;
        final String security;

        WifiSnapshot(String ssid, String security) {
            this.ssid = ssid;
            this.security = security;
        }
    }

    @SuppressLint("MissingPermission")
    private WifiSnapshot readCurrentWifi() {
        try {
            WifiInfo wifiInfo = null;
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Network activeNetwork = connectivity.getActiveNetwork();
                NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(activeNetwork);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && capabilities.getTransportInfo() instanceof WifiInfo) {
                    wifiInfo = (WifiInfo) capabilities.getTransportInfo();
                }
            }
            if (wifiInfo == null) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) wifiInfo = wifiManager.getConnectionInfo();
            }
            if (wifiInfo == null) return new WifiSnapshot("", "WPA");
            String ssid = wifiInfo.getSSID();
            if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) return new WifiSnapshot("", "WPA");
            String security = "WPA";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int securityType = wifiInfo.getCurrentSecurityType();
                if (securityType == WifiInfo.SECURITY_TYPE_OPEN || securityType == WifiInfo.SECURITY_TYPE_OWE) {
                    security = "nopass";
                } else if (securityType == WifiInfo.SECURITY_TYPE_WEP) {
                    security = "WEP";
                }
            }
            return new WifiSnapshot(ssid.replaceAll("^\"|\"$", "").trim(), security);
        } catch (SecurityException ignored) {
            return new WifiSnapshot("", "WPA");
        }
    }

    private void evaluateJavascript(String script) {
        mainHandler.post(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public final class PrinterBridge {
        private static final String PREF_LAST_ADDRESS = "last_printer_address";
        private static final String PREF_LAST_NAME = "last_printer_name";
        private static final String PREF_PAPER_WIDTH = "paper_width";
        private final UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        private final UUID batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
        private final UUID batteryLevelUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
        private final Object printerLock = new Object();
        private volatile BluetoothSocket socket;
        private volatile OutputStream output;
        private volatile BluetoothDevice connectedDevice;
        private volatile BluetoothGatt batteryGatt;
        private volatile Integer batteryLevel;
        private volatile String batterySource = "";
        private volatile boolean connecting;
        private volatile int paperWidth = 58;

        private final BluetoothGattCallback batteryGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        gatt.discoverServices();
                    } catch (SecurityException ignored) {
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (batteryGatt == gatt) batteryGatt = null;
                    try {
                        gatt.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) return;
                BluetoothGattService service = gatt.getService(batteryServiceUuid);
                BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(batteryLevelUuid);
                if (characteristic == null) return;
                try {
                    gatt.readCharacteristic(characteristic);
                } catch (SecurityException ignored) {
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS && batteryLevelUuid.equals(characteristic.getUuid())) {
                    acceptGattBattery(characteristic.getValue());
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS && batteryLevelUuid.equals(characteristic.getUuid())) {
                    acceptGattBattery(value);
                }
            }
        };

        private SharedPreferences preferences() {
            return getSharedPreferences("cetak_pro", MODE_PRIVATE);
        }

        private boolean isConnected() {
            BluetoothSocket current = socket;
            return current != null && current.isConnected() && output != null;
        }

        @JavascriptInterface
        public String connectPrinter() {
            runOnUiThread(MainActivity.this::choosePrinter);
            return statusJson();
        }

        @JavascriptInterface
        public String pilihPrinter() {
            return connectPrinter();
        }

        @JavascriptInterface
        public String autoConnect() {
            if (isConnected() || connecting || !ensureBluetoothPermissions(false)) {
                dispatchStatus();
                return statusJson();
            }
            String address = preferences().getString(PREF_LAST_ADDRESS, "");
            if (!address.isEmpty()) {
                try {
                    BluetoothAdapter adapter = getBluetoothAdapter();
                    if (adapter != null && adapter.isEnabled()) connectToDevice(adapter.getRemoteDevice(address), true);
                } catch (Exception ignored) {
                    preferences().edit().remove(PREF_LAST_ADDRESS).apply();
                }
            }
            return statusJson();
        }

        @JavascriptInterface
        public String requestPrinterStatus() {
            BluetoothDevice current = connectedDevice;
            if (current != null) {
                Integer level = querySystemBattery(current);
                if (level != null) updateBattery(level, "Android");
            }
            dispatchStatus();
            return statusJson();
        }

        @JavascriptInterface
        public String getPrinterStatus() {
            return requestPrinterStatus();
        }

        @JavascriptInterface
        public String disconnectPrinter() {
            ioExecutor.execute(() -> {
                String name = connectedDevice == null ? "Printer" : safeDeviceName(connectedDevice);
                closeImmediately();
                dispatchStatus();
                publishAppEvent("Printer terputus", name + " diputuskan dari VanNota");
            });
            return statusJson();
        }

        @JavascriptInterface
        public void setPaperWidth(int width) {
            paperWidth = width >= 80 ? 80 : 58;
            preferences().edit().putInt(PREF_PAPER_WIDTH, paperWidth).apply();
        }

        @JavascriptInterface
        public void testPrint() {
            ioExecutor.execute(() -> {
                try {
                    JSONArray lines = new JSONArray();
                    lines.put(new JSONObject().put("text", "VANNOTA").put("align", 1).put("bold", 1).put("width", 2).put("height", 2));
                    lines.put(new JSONObject().put("text", "RPP02N siap digunakan").put("align", 1));
                    lines.put(new JSONObject().put("text", "Koneksi Bluetooth berhasil").put("align", 1));
                    sendBytes(buildReceiptBytes(lines));
                    publishAppEvent("Tes cetak berhasil", "Printer menerima halaman tes VanNota");
                } catch (Exception error) {
                    handlePrintError(error);
                }
            });
        }

        @JavascriptInterface
        public void cetakStrukDinamic(String jsonPayload, String ignoredLogoPayload) {
            printReceipt(jsonPayload);
        }

        @JavascriptInterface
        public void cetakStrukDynamic(String jsonPayload, String ignoredLogoPayload) {
            printReceipt(jsonPayload);
        }

        @JavascriptInterface
        public void printReceipt(String jsonPayload, String ignoredLogoPayload) {
            printReceipt(jsonPayload);
        }

        private void printReceipt(String jsonPayload) {
            ioExecutor.execute(() -> {
                try {
                    sendBytes(buildReceiptBytes(new JSONArray(jsonPayload)));
                    publishAppEvent("Struk berhasil dicetak", "Data struk berhasil dikirim ke printer");
                } catch (Exception error) {
                    handlePrintError(error);
                }
            });
        }

        @JavascriptInterface
        public void cetakWifi(String ssid, String qrPayload) {
            printWifiQr(ssid, qrPayload);
        }

        @JavascriptInterface
        public void printWifiQr(String ssid, String qrPayload) {
            ioExecutor.execute(() -> {
                try {
                    sendBytes(buildWifiBytes(ssid, qrPayload));
                    publishAppEvent("QR Wi-Fi berhasil dicetak", "QR jaringan " + safeEventText(ssid) + " dikirim ke printer");
                } catch (Exception error) {
                    handlePrintError(error);
                }
            });
        }

        @JavascriptInterface
        public String getCurrentWifiInfo() {
            return wifiJson();
        }

        @JavascriptInterface
        public String requestWifiInfo() {
            List<String> missing = new ArrayList<>();
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addIfMissing(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
            }
            if (!missing.isEmpty()) {
                runOnUiThread(() -> requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS));
            }
            String json = wifiJson();
            dispatchWifiInfo();
            return json;
        }

        @JavascriptInterface
        public void openWifiSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception ignored) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
        }

        @JavascriptInterface
        public String getTelegramStatus() {
            return telegramReporter.statusJson();
        }

        @JavascriptInterface
        public void configureTelegram() {
            runOnUiThread(() -> telegramReporter.showSetupDialog());
        }

        @JavascriptInterface
        public void testTelegram() {
            telegramReporter.sendTest();
        }

        @JavascriptInterface
        public void disableTelegram() {
            telegramReporter.setEnabled(false);
            telegramReporter.dispatchStatus();
        }

        @JavascriptInterface
        public void setTelegramEnabled(boolean enabled) {
            telegramReporter.setEnabled(enabled);
            telegramReporter.dispatchStatus();
        }

        @SuppressLint("MissingPermission")
        private void connectToDevice(BluetoothDevice device, boolean automatic) {
            if (device == null || connecting || isConnected()) return;
            closeImmediately();
            connecting = true;
            dispatchStatus();
            ioExecutor.execute(() -> {
                BluetoothSocket candidate = null;
                try {
                    BluetoothAdapter adapter = getBluetoothAdapter();
                    if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth belum aktif.");
                    try {
                        adapter.cancelDiscovery();
                    } catch (SecurityException ignored) {
                    }

                    IOException secureError = null;
                    try {
                        candidate = device.createRfcommSocketToServiceRecord(sppUuid);
                        candidate.connect();
                    } catch (IOException error) {
                        secureError = error;
                        closeSocket(candidate);
                        candidate = device.createInsecureRfcommSocketToServiceRecord(sppUuid);
                        try {
                            candidate.connect();
                        } catch (IOException insecureError) {
                            insecureError.addSuppressed(secureError);
                            throw insecureError;
                        }
                    }

                    synchronized (printerLock) {
                        socket = candidate;
                        output = candidate.getOutputStream();
                        connectedDevice = device;
                    }
                    paperWidth = preferences().getInt(PREF_PAPER_WIDTH, 58);
                    preferences().edit()
                            .putString(PREF_LAST_ADDRESS, device.getAddress())
                            .putString(PREF_LAST_NAME, safeDeviceName(device))
                            .apply();
                    connecting = false;
                    dispatchStatus();
                    startBatteryProbe(device);
                    publishAppEvent("Printer terhubung", safeDeviceName(device) + " terhubung melalui Bluetooth");
                    if (!automatic) dispatchToast("Printer " + safeDeviceName(device) + " terhubung.", "success");
                } catch (Exception error) {
                    closeSocket(candidate);
                    connecting = false;
                    closeImmediately();
                    dispatchStatus();
                    if (!automatic) {
                        dispatchToast("Gagal terhubung. Pastikan RPP02N sudah dipasangkan dan tidak dipakai aplikasi lain.", "warning");
                    }
                }
            });
        }

        private void sendBytes(byte[] bytes) throws IOException {
            synchronized (printerLock) {
                if (!isConnected()) throw new IOException("Printer belum terhubung.");
                try {
                    output.write(bytes);
                    output.flush();
                } catch (IOException error) {
                    closeImmediately();
                    dispatchStatus();
                    throw error;
                }
            }
        }

        private byte[] buildReceiptBytes(JSONArray lines) throws JSONException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            append(bytes, new byte[]{0x1B, 0x40});
            for (int index = 0; index < lines.length(); index++) {
                JSONObject line = lines.optJSONObject(index);
                if (line == null) continue;
                int align = clamp(line.optInt("align", 0), 0, 2);
                int bold = line.optInt("bold", 0) > 0 ? 1 : 0;
                int width = clamp(line.optInt("width", 1), 1, 2);
                int height = clamp(line.optInt("height", 1), 1, 2);
                int size = ((width - 1) << 4) | (height - 1);
                append(bytes, new byte[]{0x1B, 0x61, (byte) align});
                append(bytes, new byte[]{0x1B, 0x45, (byte) bold});
                append(bytes, new byte[]{0x1D, 0x21, (byte) size});
                append(bytes, encodeText(line.optString("text", "")));
                bytes.write(0x0A);
            }
            append(bytes, new byte[]{0x1B, 0x45, 0x00, 0x1D, 0x21, 0x00, 0x1B, 0x61, 0x00});
            append(bytes, new byte[]{0x0A, 0x0A, 0x0A});
            return bytes.toByteArray();
        }

        private byte[] buildWifiBytes(String ssid, String qrPayload) throws WriterException {
            if (qrPayload == null || qrPayload.trim().isEmpty()) throw new IllegalArgumentException("Data QR Wi-Fi kosong.");
            int size = paperWidth >= 80 ? 320 : 256;
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, size, size, hints);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            append(bytes, new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01, 0x1B, 0x45, 0x01});
            append(bytes, encodeText("WI-FI\n"));
            append(bytes, new byte[]{0x1B, 0x45, 0x00});
            append(bytes, encodeText((ssid == null ? "" : ssid.trim()) + "\n\n"));
            append(bytes, rasterize(matrix));
            append(bytes, encodeText("\nScan untuk terhubung\n\n\n"));
            append(bytes, new byte[]{0x1B, 0x61, 0x00});
            return bytes.toByteArray();
        }

        private byte[] rasterize(BitMatrix matrix) {
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int widthBytes = (width + 7) / 8;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + widthBytes * height);
            append(bytes, new byte[]{
                    0x1D, 0x76, 0x30, 0x00,
                    (byte) (widthBytes & 0xFF), (byte) ((widthBytes >> 8) & 0xFF),
                    (byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF)
            });
            for (int y = 0; y < height; y++) {
                for (int byteX = 0; byteX < widthBytes; byteX++) {
                    int value = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = byteX * 8 + bit;
                        if (x < width && matrix.get(x, y)) value |= 0x80 >> bit;
                    }
                    bytes.write(value);
                }
            }
            return bytes.toByteArray();
        }

        private byte[] encodeText(String text) {
            return String.valueOf(text).getBytes(Charset.forName("windows-1252"));
        }

        private void append(ByteArrayOutputStream stream, byte[] data) {
            stream.write(data, 0, data.length);
        }

        private int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private String statusJson() {
            try {
                String name = isConnected() && connectedDevice != null
                        ? safeDeviceName(connectedDevice)
                        : preferences().getString(PREF_LAST_NAME, "Belum terhubung");
                return new JSONObject()
                        .put("name", name)
                        .put("connected", isConnected())
                        .put("connecting", connecting)
                        .put("battery", batteryLevel == null ? JSONObject.NULL : batteryLevel)
                        .put("batterySupported", batteryLevel != null)
                        .put("batterySource", batterySource)
                        .put("transport", "Bluetooth Classic")
                        .toString();
            } catch (JSONException impossible) {
                return "{\"connected\":false}";
            }
        }

        private String wifiJson() {
            try {
                WifiSnapshot wifi = readCurrentWifi();
                return new JSONObject()
                        .put("ssid", wifi.ssid)
                        .put("security", wifi.security)
                        .put("connected", !wifi.ssid.isEmpty())
                        .put("passwordReadable", false)
                        .toString();
            } catch (JSONException impossible) {
                return "{\"ssid\":\"\"}";
            }
        }

        private void dispatchStatus() {
            String quoted = JSONObject.quote(statusJson());
            evaluateJavascript("window.updatePrinterStatus&&window.updatePrinterStatus(" + quoted + ");");
        }

        private void dispatchWifiInfo() {
            String quoted = JSONObject.quote(wifiJson());
            evaluateJavascript("window.updateWifiInfo&&window.updateWifiInfo(" + quoted + ");");
        }

        private void dispatchToast(String message, String type) {
            evaluateJavascript("window.showToast&&window.showToast(" + JSONObject.quote(message) + "," + JSONObject.quote(type) + ");");
        }

        private void handlePrintError(Exception error) {
            if (!isConnected()) dispatchStatus();
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) message = "Printer tidak merespons.";
            dispatchToast(message, "warning");
            publishAppEvent("Pencetakan gagal", safeEventText(message));
        }

        private String getConnectedAddress() {
            BluetoothDevice current = connectedDevice;
            return current == null ? "" : current.getAddress();
        }

        private void handleSystemDisconnect() {
            ioExecutor.execute(() -> {
                String name = connectedDevice == null ? "Printer" : safeDeviceName(connectedDevice);
                closeImmediately();
                dispatchStatus();
                dispatchToast("Koneksi printer terputus.", "warning");
                publishAppEvent("Koneksi printer terputus", name + " tidak lagi terhubung");
            });
        }

        private String safeEventText(String value) {
            String clean = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
            return clean.length() > 80 ? clean.substring(0, 80) : clean;
        }

        @SuppressLint("MissingPermission")
        private void startBatteryProbe(BluetoothDevice device) {
            batteryLevel = null;
            batterySource = "";
            closeBatteryGatt();

            Integer cached = querySystemBattery(device);
            if (cached != null) updateBattery(cached, "Android");

            try {
                BluetoothGatt gatt = device.connectGatt(
                        MainActivity.this,
                        false,
                        batteryGattCallback,
                        BluetoothDevice.TRANSPORT_LE
                );
                batteryGatt = gatt;
            } catch (Exception ignored) {
            }

            mainHandler.postDelayed(() -> {
                if (device.equals(connectedDevice)) {
                    Integer refreshed = querySystemBattery(device);
                    if (refreshed != null) updateBattery(refreshed, "Android");
                }
            }, 2500);
        }

        private Integer querySystemBattery(BluetoothDevice device) {
            if (device == null) return null;
            try {
                Object value = BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device);
                if (value instanceof Integer && validBattery((Integer) value)) return (Integer) value;
            } catch (Exception ignored) {
            }
            try {
                Object value = BluetoothDevice.class.getMethod("getMetadata", int.class).invoke(device, 18);
                if (value instanceof byte[]) {
                    byte[] bytes = (byte[]) value;
                    if (bytes.length == 1 && validBattery(bytes[0] & 0xff)) return bytes[0] & 0xff;
                    String text = new String(bytes, StandardCharsets.UTF_8).replaceAll("[^0-9]", "");
                    if (!text.isEmpty()) {
                        int parsed = Integer.parseInt(text);
                        if (validBattery(parsed)) return parsed;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private void acceptGattBattery(byte[] value) {
            if (value == null || value.length == 0) return;
            updateBattery(value[0] & 0xff, "Bluetooth Battery Service");
        }

        private boolean validBattery(int level) {
            return level >= 0 && level <= 100;
        }

        private void updateBattery(int level, String source) {
            if (!validBattery(level)) return;
            batteryLevel = level;
            batterySource = source == null ? "" : source;
            dispatchStatus();
        }

        private void closeBatteryGatt() {
            BluetoothGatt current = batteryGatt;
            batteryGatt = null;
            closeGatt(current);
        }

        @SuppressLint("MissingPermission")
        private void closeGatt(BluetoothGatt gatt) {
            if (gatt == null) return;
            try {
                gatt.disconnect();
            } catch (Exception ignored) {
            }
            try {
                gatt.close();
            } catch (Exception ignored) {
            }
        }

        private void closeImmediately() {
            synchronized (printerLock) {
                closeBatteryGatt();
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException ignored) {
                    }
                }
                closeSocket(socket);
                output = null;
                socket = null;
                connectedDevice = null;
                batteryLevel = null;
                batterySource = "";
                connecting = false;
            }
        }

        private void closeSocket(BluetoothSocket target) {
            if (target == null) return;
            try {
                target.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final class TelegramReporter {
        private static final String STORE = "vannota_secure";
        private static final String KEY_ALIAS = "vannota_telegram_key";
        private static final String KEY_TOKEN = "telegram_token";
        private static final String KEY_TOKEN_IV = "telegram_token_iv";
        private static final String KEY_CHAT_ID = "telegram_chat_id";
        private static final String KEY_ENABLED = "telegram_enabled";

        private SharedPreferences preferences() {
            return getSharedPreferences(STORE, MODE_PRIVATE);
        }

        private boolean isConfigured() {
            return !loadToken().isEmpty() && !preferences().getString(KEY_CHAT_ID, "").isEmpty();
        }

        private boolean isEnabled() {
            return isConfigured() && preferences().getBoolean(KEY_ENABLED, true);
        }

        private String statusJson() {
            try {
                return new JSONObject()
                        .put("configured", isConfigured())
                        .put("enabled", isEnabled())
                        .put("scope", "Aktivitas VanNota saja")
                        .toString();
            } catch (JSONException impossible) {
                return "{\"configured\":false,\"enabled\":false}";
            }
        }

        private void showSetupDialog() {
            int padding = Math.round(22 * getResources().getDisplayMetrics().density);
            LinearLayout fields = new LinearLayout(MainActivity.this);
            fields.setOrientation(LinearLayout.VERTICAL);
            fields.setPadding(padding, 0, padding, 0);

            EditText tokenInput = new EditText(MainActivity.this);
            tokenInput.setHint("Token bot baru dari BotFather");
            tokenInput.setSingleLine(true);
            tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            fields.addView(tokenInput);

            EditText chatInput = new EditText(MainActivity.this);
            chatInput.setHint("Chat ID Telegram");
            chatInput.setSingleLine(true);
            chatInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            chatInput.setText(preferences().getString(KEY_CHAT_ID, ""));
            fields.addView(chatInput);

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Hubungkan Telegram")
                    .setMessage("Masukkan token baru setelah token lama dicabut di BotFather. Token disimpan terenkripsi di perangkat dan tidak dimasukkan ke website, APK publik, atau GitHub. Hanya aktivitas VanNota yang dikirim.")
                    .setView(fields)
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Simpan & uji", (dialog, which) -> {
                        String token = tokenInput.getText().toString().trim();
                        String chatId = chatInput.getText().toString().trim();
                        if (!token.matches("[0-9]{6,12}:[A-Za-z0-9_-]{20,}") || !chatId.matches("-?[0-9]+")) {
                            showNativeMessage("Data Telegram belum valid", "Gunakan token bot baru dari BotFather dan Chat ID berupa angka.");
                            return;
                        }
                        try {
                            saveCredentials(token, chatId);
                            dispatchStatus();
                            sendTest();
                        } catch (Exception error) {
                            showNativeMessage("Gagal menyimpan", "Perangkat tidak dapat mengamankan token Telegram.");
                        }
                    })
                    .show();
        }

        private void setEnabled(boolean enabled) {
            preferences().edit().putBoolean(KEY_ENABLED, enabled).apply();
        }

        private void saveCredentials(String token, String chatId) throws Exception {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            preferences().edit()
                    .putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(KEY_CHAT_ID, chatId)
                    .putBoolean(KEY_ENABLED, true)
                    .apply();
        }

        private String loadToken() {
            String encrypted = preferences().getString(KEY_TOKEN, "");
            String iv = preferences().getString(KEY_TOKEN_IV, "");
            if (encrypted.isEmpty() || iv.isEmpty()) return "";
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
                if (key == null) return "";
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }

        private SecretKey getOrCreateKey() throws Exception {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (existing != null) return existing;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        }

        private void sendEvent(String title, String message) {
            if (!isEnabled()) return;
            sendMessage(title, message, false);
        }

        private void sendTest() {
            if (!isConfigured()) {
                mainHandler.post(() -> showNativeMessage("Telegram belum diatur", "Masukkan token bot baru dan Chat ID dari menu Pengaturan."));
                return;
            }
            setEnabled(true);
            dispatchStatus();
            sendMessage("Tes koneksi", "Telegram berhasil dihubungkan ke VanNota", true);
        }

        private void sendMessage(String title, String message, boolean showResult) {
            String token = loadToken();
            String chatId = preferences().getString(KEY_CHAT_ID, "");
            if (token.isEmpty() || chatId.isEmpty()) return;
            networkExecutor.execute(() -> {
                HttpURLConnection connection = null;
                boolean success = false;
                try {
                    URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    String text = "VanNota • " + title + "\n" + message;
                    String body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                            + "&text=" + URLEncoder.encode(text, "UTF-8");
                    try (OutputStream stream = connection.getOutputStream()) {
                        stream.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    int responseCode = connection.getResponseCode();
                    success = responseCode >= 200 && responseCode < 300;
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
                if (showResult) {
                    boolean sent = success;
                    mainHandler.post(() -> {
                        String messageText = sent
                                ? "Pesan uji berhasil dikirim ke Telegram."
                                : "Pesan uji gagal. Periksa token baru, Chat ID, dan koneksi internet.";
                        Toast.makeText(MainActivity.this, messageText, Toast.LENGTH_LONG).show();
                        printerBridge.dispatchToast(messageText, sent ? "success" : "warning");
                    });
                }
            });
        }

        private void dispatchStatus() {
            String quoted = JSONObject.quote(statusJson());
            evaluateJavascript("window.updateTelegramStatus&&window.updateTelegramStatus(" + quoted + ");");
        }
    }
}
