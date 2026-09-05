from pathlib import Path

index = Path('index.html')
text = index.read_text(encoding='utf-8')

anchor = '            <div id="custom-controls" class="hidden border p-4 rounded bg-yellow-50 border-yellow-200 space-y-3">'
logo_ui = '''            <div class="border p-4 rounded bg-gray-50 border-gray-200">
                <label class="block font-bold text-gray-700 mb-2">Logo Struk</label>
                <input type="file" id="in-logo-file" accept="image/png,image/jpeg,image/webp" class="w-full bg-white p-1 border rounded text-xs">
                <div id="logo-upload-preview" class="hidden mt-3 text-center">
                    <img id="logo-upload-preview-img" alt="Preview logo" style="display:inline-block;max-width:120px;max-height:70px;object-fit:contain;">
                    <div class="mt-2"><button type="button" class="template-action danger" onclick="clearReceiptLogo()">Hapus logo</button></div>
                </div>
                <p class="text-xs text-gray-500 mt-1">PNG/JPG/WebP. Logo akan tampil di pratinjau dan dicetak hitam-putih pada printer thermal.</p>
            </div>

'''
if anchor not in text:
    raise SystemExit('index custom-controls anchor not found')
if 'id="in-logo-file"' not in text:
    text = text.replace(anchor, logo_ui + anchor, 1)

anchor = '    function updateReceipt() {\n'
logo_js = '''    let receiptLogoDataUrl = '';

    function renderReceiptLogo() {
        document.querySelectorAll('.uploaded-logo-container').forEach(el => el.remove());
        const preview = document.getElementById('logo-upload-preview');
        const previewImg = document.getElementById('logo-upload-preview-img');
        if (preview && previewImg) {
            preview.classList.toggle('hidden', !receiptLogoDataUrl);
            previewImg.src = receiptLogoDataUrl || '';
        }
        if (!receiptLogoDataUrl) return;
        const activeModel = document.querySelector('.model-view:not(.hidden)');
        if (!activeModel) return;
        const box = document.createElement('div');
        box.className = 'center logo-display-container uploaded-logo-container';
        box.style.marginBottom = '6px';
        const img = document.createElement('img');
        img.src = receiptLogoDataUrl;
        img.alt = 'Logo struk';
        img.style.display = 'inline-block';
        img.style.maxWidth = '75%';
        img.style.maxHeight = '70px';
        img.style.objectFit = 'contain';
        box.appendChild(img);
        activeModel.insertBefore(box, activeModel.firstChild);
    }

    function clearReceiptLogo() {
        receiptLogoDataUrl = '';
        const input = document.getElementById('in-logo-file');
        if (input) input.value = '';
        renderReceiptLogo();
    }

    document.getElementById('in-logo-file')?.addEventListener('change', function(e) {
        const file = e.target.files && e.target.files[0];
        if (!file) return;
        if (!file.type.startsWith('image/')) {
            showToast('File logo harus berupa gambar.', 'warning');
            return;
        }
        const reader = new FileReader();
        reader.onload = () => {
            receiptLogoDataUrl = String(reader.result || '');
            renderReceiptLogo();
            showToast('Logo berhasil dimuat.', 'success');
        };
        reader.onerror = () => showToast('Logo gagal dibaca.', 'warning');
        reader.readAsDataURL(file);
    });

'''
if anchor not in text:
    raise SystemExit('updateReceipt anchor not found')
if 'let receiptLogoDataUrl' not in text:
    text = text.replace(anchor, logo_js + anchor, 1)

old = '''        updateReceipt();
    });

    // ==========================================
    // LOGIKA GLOBAL MARGIN
'''
new = '''        updateReceipt();
        renderReceiptLogo();
    });

    // ==========================================
    // LOGIKA GLOBAL MARGIN
'''
if old not in text:
    raise SystemExit('template change anchor not found')
text = text.replace(old, new, 1)

old = '''            if (child.classList.contains('hidden') || child.style.display === 'none') continue;

            let align = 0; 
'''
new = '''            if (child.classList.contains('hidden') || child.style.display === 'none') continue;
            if (child.classList.contains('uploaded-logo-container')) continue;

            let align = 0; 
'''
if old not in text:
    raise SystemExit('print loop anchor not found')
text = text.replace(old, new, 1)

old = '''                await syncNativePaperWidth();
                await Promise.resolve(nativeMethod.fn(jsonPayload, ''));
'''
new = '''                await syncNativePaperWidth();
                await Promise.resolve(nativeMethod.fn(jsonPayload, receiptLogoDataUrl || ''));
'''
if old not in text:
    raise SystemExit('native print call anchor not found')
text = text.replace(old, new, 1)
index.write_text(text, encoding='utf-8')

main = Path('android/app/src/main/java/com/cetakpro/print/MainActivity.java')
java = main.read_text(encoding='utf-8')

old = 'import android.graphics.Color;\n'
new = 'import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\nimport android.graphics.Color;\n'
if old not in java:
    raise SystemExit('Color import anchor not found')
if 'import android.graphics.Bitmap;' not in java:
    java = java.replace(old, new, 1)

old = '''        @JavascriptInterface
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
'''
new = '''        @JavascriptInterface
        public void cetakStrukDinamic(String jsonPayload, String logoPayload) {
            printReceiptInternal(jsonPayload, logoPayload);
        }

        @JavascriptInterface
        public void cetakStrukDynamic(String jsonPayload, String logoPayload) {
            printReceiptInternal(jsonPayload, logoPayload);
        }

        @JavascriptInterface
        public void printReceipt(String jsonPayload, String logoPayload) {
            printReceiptInternal(jsonPayload, logoPayload);
        }

        private void printReceiptInternal(String jsonPayload, String logoPayload) {
            ioExecutor.execute(() -> {
                try {
                    sendBytes(buildReceiptBytes(new JSONArray(jsonPayload), logoPayload));
                    publishAppEvent("Struk berhasil dicetak", "Data struk berhasil dikirim ke printer");
                } catch (Exception error) {
                    handlePrintError(error);
                }
            });
        }
'''
if old not in java:
    raise SystemExit('printer logo-ignore block not found')
java = java.replace(old, new, 1)

old = '''        private byte[] buildReceiptBytes(JSONArray lines) throws JSONException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            append(bytes, new byte[]{0x1B, 0x40});
'''
new = '''        private byte[] buildReceiptBytes(JSONArray lines) throws JSONException {
            return buildReceiptBytes(lines, "");
        }

        private byte[] buildReceiptBytes(JSONArray lines, String logoPayload) throws JSONException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            append(bytes, new byte[]{0x1B, 0x40});
            byte[] logoBytes = rasterizeLogo(logoPayload);
            if (logoBytes.length > 0) {
                append(bytes, new byte[]{0x1B, 0x61, 0x01});
                append(bytes, logoBytes);
                append(bytes, new byte[]{0x0A, 0x1B, 0x61, 0x00});
            }
'''
if old not in java:
    raise SystemExit('buildReceiptBytes anchor not found')
java = java.replace(old, new, 1)

anchor = '''        private byte[] buildWifiBytes(String ssid, String qrPayload) throws WriterException {
'''
helper = '''        private byte[] rasterizeLogo(String logoPayload) {
            if (logoPayload == null || logoPayload.trim().isEmpty()) return new byte[0];
            Bitmap original = null;
            Bitmap scaled = null;
            try {
                String base64 = logoPayload;
                int comma = base64.indexOf(',');
                if (comma >= 0) base64 = base64.substring(comma + 1);
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                original = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) return new byte[0];

                int maxWidth = paperWidth >= 80 ? 560 : 360;
                int width = Math.min(original.getWidth(), maxWidth);
                int height = Math.max(1, Math.round(original.getHeight() * (width / (float) original.getWidth())));
                scaled = Bitmap.createScaledBitmap(original, width, height, true);

                int widthBytes = (width + 7) / 8;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0x00);
                out.write(widthBytes & 0xFF); out.write((widthBytes >> 8) & 0xFF);
                out.write(height & 0xFF); out.write((height >> 8) & 0xFF);

                for (int y = 0; y < height; y++) {
                    for (int xb = 0; xb < widthBytes; xb++) {
                        int value = 0;
                        for (int bit = 0; bit < 8; bit++) {
                            int x = xb * 8 + bit;
                            if (x >= width) continue;
                            int pixel = scaled.getPixel(x, y);
                            int alpha = (pixel >>> 24) & 0xFF;
                            int r = (pixel >>> 16) & 0xFF;
                            int g = (pixel >>> 8) & 0xFF;
                            int b = pixel & 0xFF;
                            int luminance = (r * 299 + g * 587 + b * 114) / 1000;
                            if (alpha > 32 && luminance < 170) value |= (1 << (7 - bit));
                        }
                        out.write(value);
                    }
                }
                return out.toByteArray();
            } catch (Exception ignored) {
                return new byte[0];
            } finally {
                if (scaled != null && scaled != original && !scaled.isRecycled()) scaled.recycle();
                if (original != null && !original.isRecycled()) original.recycle();
            }
        }

'''
if anchor not in java:
    raise SystemExit('buildWifiBytes anchor not found')
if 'private byte[] rasterizeLogo(String logoPayload)' not in java:
    java = java.replace(anchor, helper + anchor, 1)

main.write_text(java, encoding='utf-8')
