# Cetak Pro

Editor struk SPBU dan generator QR Wi-Fi untuk printer thermal ESC/POS. Repositori berisi website statis serta aplikasi Android native WebView dengan jembatan Bluetooth Classic untuk RPP02N.

## Fitur

- Editor struk 58 mm dan 80 mm dengan template, OCR, margin, perataan, ukuran teks, serta simpan template lokal.
- Pengaturan koneksi printer, status tersambung hijau, tes cetak, dan sambung ulang otomatis.
- Indikator baterai asli ketika model printer menyediakan data baterai; model yang tidak mendukung ditandai `N/A`.
- Wi-Fi QR otomatis dari SSID yang dikirim aplikasi Android; profil sandi dapat disimpan sekali di perangkat.
- Cetak QR Wi-Fi dan struk melalui jembatan Android, Web Bluetooth BLE ESC/POS, atau menu cetak sistem.
- Tema otomatis, terang, gelap, pilihan warna, dan penggeser hue kustom.
- Dialog, konfirmasi, dan notifikasi khusus yang responsif untuk ponsel.

## Menjalankan website

Buka `index.html` melalui HTTPS atau GitHub Pages. HTTPS diperlukan untuk Web Bluetooth. Sebagian besar printer thermal murah memakai Bluetooth Classic/SPP; model tersebut perlu pembungkus Android karena browser hanya mendukung BLE.

## Aplikasi Android

Source aplikasi berada di folder `android/` dengan package `com.cetakpro.print`, minimum Android 6, dan target Android 15. Aplikasi memuat GitHub Pages lalu menyediakan:

- pemilih printer Bluetooth yang sudah dipasangkan dan memprioritaskan `RPP02N`;
- koneksi ESC/POS Bluetooth Classic/SPP, sambung ulang otomatis, status socket nyata, serta deteksi koneksi terputus;
- cetak struk teks dan QR Wi-Fi raster 58/80 mm;
- pembacaan SSID Wi-Fi aktif setelah izin Android diberikan;
- pemilih gambar Android untuk fitur OCR.

Workflow **Build Android APK** berjalan otomatis saat folder Android berubah. APK hasil build tersedia sebagai artifact `Cetak-Pro-Android` di halaman Actions GitHub. Build manual:

```bash
cd android
gradle :app:assembleDebug
```

Android tetap menampilkan dialog izin sistem. Aplikasi tidak dapat memberi izin kepada dirinya sendiri dan tidak dapat membaca kata sandi Wi-Fi tersimpan. Sambungkan Wi-Fi di Pengaturan Android, izinkan perangkat sekitar/lokasi, lalu masukkan sandi sekali pada aplikasi agar profil QR tersimpan lokal.

RPP02N menggunakan baterai internal, tetapi command set ESC/POS publik model tersebut tidak menyediakan perintah persentase baterai. Karena itu aplikasi menampilkan status koneksi asli dan `N/A` untuk baterai RPP02N, bukan angka perkiraan.

## Kontrak Android WebView

Website mempertahankan metode cetak lama dan mengenali beberapa alias agar integrasi shell Android lebih mudah. API utama yang disarankan:

```text
AndroidPrinter.connectPrinter()
AndroidPrinter.disconnectPrinter()
AndroidPrinter.getPrinterStatus()
AndroidPrinter.testPrint()
AndroidPrinter.setPaperWidth(paperWidth) // opsional
AndroidPrinter.cetakStrukDinamic(jsonPayload, logoPayload)
AndroidPrinter.cetakWifi(ssid, wifiQrPayload)
AndroidPrinter.getCurrentWifiInfo()
```

Shell Android mengirim pembaruan ke halaman dengan callback berikut:

```javascript
window.updatePrinterStatus({
  name: "RPP02N",
  connected: true,
  battery: 82,
  transport: "Android Bluetooth"
});

window.updatePrinterBattery(81);

window.updateWifiInfo({
  ssid: "Nama WiFi",
  security: "WPA"
});
```

`battery` harus berasal dari respons/status printer, bukan baterai ponsel. Jika model printer tidak menyediakan status baterai, kirim `null` agar antarmuka menampilkan `N/A`.

Android tidak mengizinkan aplikasi biasa membaca kata sandi Wi-Fi yang sudah tersimpan. Karena itu shell cukup mengirim SSID; pengguna memasukkan sandi sekali dan dapat menyimpan profil pada penyimpanan lokal WebView. Jangan memasukkan kata sandi Wi-Fi ke source code atau repositori.
