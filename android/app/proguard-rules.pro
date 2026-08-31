# Keep JavaScript bridge methods if release minification is enabled later.
-keepclassmembers class com.cetakpro.print.MainActivity$PrinterBridge {
    @android.webkit.JavascriptInterface <methods>;
}
