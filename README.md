# Cetak Pro

Editor struk SPBU dan generator QR Wi-Fi untuk printer thermal ESC/POS. Aplikasi berjalan sebagai website statis dan tetap kompatibel dengan pembungkus Android WebView.

## Fitur

- Editor struk 58 mm dan 80 mm dengan template, OCR, margin, perataan, ukuran teks, serta simpan template lokal.
- Pengaturan koneksi printer, status tersambung hijau, tes cetak, dan sambung ulang otomatis.
- Indikator baterai asli ketika printer BLE atau jembatan Android mendukung pembacaan baterai.
- Wi-Fi QR otomatis dari SSID yang dikirim aplikasi Android; profil sandi dapat disimpan sekali di perangkat.
- Cetak QR Wi-Fi dan struk melalui jembatan Android, Web Bluetooth BLE ESC/POS, atau menu cetak sistem.
- Tema otomatis, terang, gelap, pilihan warna, dan penggeser hue kustom.
- Dialog, konfirmasi, dan notifikasi khusus yang responsif untuk ponsel.

## Menjalankan website

Buka `index.html` melalui HTTPS atau GitHub Pages. HTTPS diperlukan untuk Web Bluetooth. Sebagian besar printer thermal murah memakai Bluetooth Classic/SPP; model tersebut perlu pembungkus Android karena browser hanya mendukung BLE.

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
