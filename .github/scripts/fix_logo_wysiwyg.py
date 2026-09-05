from pathlib import Path

html = Path('index.html')
s = html.read_text(encoding='utf-8')

replacements = [
('''                <div id="logo-upload-preview" class="hidden mt-3 text-center">
                    <img id="logo-upload-preview-img" alt="Preview logo" style="display:inline-block;max-width:120px;max-height:70px;object-fit:contain;">
                    <div class="mt-2"><button type="button" class="template-action danger" onclick="clearReceiptLogo()">Hapus logo</button></div>
                </div>
                <p class="text-xs text-gray-500 mt-1">PNG/JPG/WebP. Logo akan tampil di pratinjau dan dicetak hitam-putih pada printer thermal.</p>''',
'''                <div id="logo-upload-preview" class="hidden mt-3 text-center">
                    <img id="logo-upload-preview-img" alt="Preview logo" style="display:inline-block;max-width:120px;max-height:70px;object-fit:contain;">
                    <div class="mt-3 text-left">
                        <div class="flex items-center justify-between gap-3 mb-1">
                            <label for="logo-size-slider" class="text-xs font-bold text-gray-700">Ukuran logo</label>
                            <span id="logo-size-label" class="text-xs font-bold text-blue-700">75%</span>
                        </div>
                        <input id="logo-size-slider" type="range" min="20" max="100" step="5" value="75" class="w-full" oninput="setReceiptLogoSize(this.value)">
                    </div>
                    <div class="mt-2"><button type="button" class="template-action danger" onclick="clearReceiptLogo()">Hapus logo</button></div>
                </div>
                <p class="text-xs text-gray-500 mt-1">PNG/JPG/WebP. Ukuran logo pada pratinjau akan dipakai sama saat dicetak.</p>'''),
("    let receiptLogoDataUrl = '';\n\n    function renderReceiptLogo() {",
"    let receiptLogoDataUrl = '';\n    let receiptLogoSizePercent = 75;\n\n    function setReceiptLogoSize(value) {\n        receiptLogoSizePercent = Math.max(20, Math.min(100, Number(value) || 75));\n        const label = document.getElementById('logo-size-label');\n        const slider = document.getElementById('logo-size-slider');\n        if (label) label.textContent = Math.round(receiptLogoSizePercent) + '%';\n        if (slider && Number(slider.value) !== receiptLogoSizePercent) slider.value = receiptLogoSizePercent;\n        renderReceiptLogo();\n    }\n\n    function renderReceiptLogo() {"),
("        if (preview && previewImg) {\n            preview.classList.toggle('hidden', !receiptLogoDataUrl);\n            previewImg.src = receiptLogoDataUrl || '';\n        }",
"        if (preview && previewImg) {\n            preview.classList.toggle('hidden', !receiptLogoDataUrl);\n            previewImg.src = receiptLogoDataUrl || '';\n            previewImg.style.maxWidth = receiptLogoSizePercent + '%';\n            const label = document.getElementById('logo-size-label');\n            if (label) label.textContent = Math.round(receiptLogoSizePercent) + '%';\n        }"),
("        img.style.maxWidth = '75%';", "        img.style.width = receiptLogoSizePercent + '%';\n        img.style.maxWidth = '100%';"),
('''                div.innerHTML = l.trim() === "" ? "&nbsp;" : l; 
                display.appendChild(div);''', '''                div.style.whiteSpace = "pre";
                div.textContent = l === "" ? " " : l;
                display.appendChild(div);'''),
("                        let printL = l.replace(/\\u00a0/g, ' ').trim();", "                        let printL = l.replace(/\\u00a0/g, ' ');"),
("                await Promise.resolve(nativeMethod.fn(jsonPayload, receiptLogoDataUrl || ''));", "                const logoPayload = receiptLogoDataUrl ? JSON.stringify({ dataUrl: receiptLogoDataUrl, sizePercent: receiptLogoSizePercent }) : '';\n                await Promise.resolve(nativeMethod.fn(jsonPayload, logoPayload));")
]
for old,new in replacements:
    if old not in s:
        raise SystemExit('HTML anchor not found: ' + old[:80])
    s=s.replace(old,new,1)
html.write_text(s,encoding='utf-8')

java=Path('android/app/src/main/java/com/cetakpro/print/MainActivity.java')
j=java.read_text(encoding='utf-8')
old='''                String base64 = logoPayload;
                int comma = base64.indexOf(',');
                if (comma >= 0) base64 = base64.substring(comma + 1);
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);'''
new='''                String base64 = logoPayload;
                int sizePercent = 75;
                String trimmedPayload = logoPayload.trim();
                if (trimmedPayload.startsWith("{")) {
                    JSONObject logo = new JSONObject(trimmedPayload);
                    base64 = logo.optString("dataUrl", "");
                    sizePercent = clamp(logo.optInt("sizePercent", 75), 20, 100);
                }
                int comma = base64.indexOf(',');
                if (comma >= 0) base64 = base64.substring(comma + 1);
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);'''
if old not in j: raise SystemExit('Java logo decode anchor not found')
j=j.replace(old,new,1)
old='''                int maxWidth = paperWidth >= 80 ? 560 : 360;
                int width = Math.min(original.getWidth(), maxWidth);'''
new='''                int paperMaxWidth = paperWidth >= 80 ? 560 : 360;
                int maxWidth = Math.max(1, Math.round(paperMaxWidth * (sizePercent / 100f)));
                int width = Math.min(original.getWidth(), maxWidth);'''
if old not in j: raise SystemExit('Java logo width anchor not found')
j=j.replace(old,new,1)
java.write_text(j,encoding='utf-8')
