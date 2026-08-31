package com.cetakpro.print;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 4101;
    private static final int REQUEST_ENABLE_BLUETOOTH = 4102;
    private static final int REQUEST_FILE = 4103;
    private static final String OFFLINE_URL = "file:///android_asset/offline.html";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private PrinterBridge printerBridge;
    private ValueCallback<Uri[]> fileCallback;
    private boolean connectAfterPermission;
    private boolean receiverRegistered;
    private boolean showingOffline;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction()) || printerBridge == null) return;
            BluetoothDevice disconnected = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (disconnected != null && disconnected.getAddress().equals(printerBridge.getConnectedAddress())) {
                printerBridge.handleSystemDisconnect();
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(244, 247, 251));
        getWindow().setNavigationBarColor(Color.rgb(244, 247, 251));
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
        settings.setUserAgentString(settings.getUserAgentString() + " CetakProAndroid/1.0");

        printerBridge = new PrinterBridge();
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
        webView.loadUrl(BuildConfig.WEB_APP_URL);
        mainHandler.postDelayed(this::explainAndRequestPermissions, 700);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;
        printerBridge.dispatchWifiInfo();
        if (connectAfterPermission) {
            connectAfterPermission = false;
            choosePrinter();
        } else printerBridge.autoConnect();
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
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(bluetoothReceiver, filter);
            }
            receiverRegistered = true;
        } catch (SecurityException ignored) {
            receiverRegistered = false;
        }
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
                .setTitle("Izinkan fitur Cetak Pro")
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

    @SuppressLint("MissingPermission")
    private String readCurrentSsid() {
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
            if (wifiInfo == null) return "";
            String ssid = wifiInfo.getSSID();
            if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) return "";
            return ssid.replaceAll("^\"|\"$", "").trim();
        } catch (SecurityException ignored) {
            return "";
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
        private final Object printerLock = new Object();
        private volatile BluetoothSocket socket;
        private volatile OutputStream output;
        private volatile BluetoothDevice connectedDevice;
        private volatile boolean connecting;
        private volatile int paperWidth = 58;

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
                closeImmediately();
                dispatchStatus();
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
                    lines.put(new JSONObject().put("text", "CETAK PRO").put("align", 1).put("bold", 1).put("width", 2).put("height", 2));
                    lines.put(new JSONObject().put("text", "RPP02N siap digunakan").put("align", 1));
                    lines.put(new JSONObject().put("text", "Koneksi Bluetooth berhasil").put("align", 1));
                    sendBytes(buildReceiptBytes(lines));
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
                        .put("battery", JSONObject.NULL)
                        .put("batterySupported", false)
                        .put("transport", "Bluetooth Classic")
                        .toString();
            } catch (JSONException impossible) {
                return "{\"connected\":false}";
            }
        }

        private String wifiJson() {
            try {
                String ssid = readCurrentSsid();
                return new JSONObject()
                        .put("ssid", ssid)
                        .put("connected", !ssid.isEmpty())
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
        }

        private String getConnectedAddress() {
            BluetoothDevice current = connectedDevice;
            return current == null ? "" : current.getAddress();
        }

        private void handleSystemDisconnect() {
            ioExecutor.execute(() -> {
                closeImmediately();
                dispatchStatus();
                dispatchToast("Koneksi printer terputus.", "warning");
            });
        }

        private void closeImmediately() {
            synchronized (printerLock) {
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
}
