from pathlib import Path

main = Path('android/app/src/main/java/com/cetakpro/print/MainActivity.java')
s = main.read_text(encoding='utf-8')

old_nav = '''                    Uri uri = request.getUrl();
                    String host = uri.getHost();
                    if (host != null && (host.equals("irvanmaulana.my.id") || host.endsWith(".jsdelivr.net"))) return false;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }'''
new_nav = '''                    Uri uri = request.getUrl();
                    String scheme = uri.getScheme();
                    if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                        view.loadUrl(uri.toString());
                        return true;
                    }
                    return false;'''
if old_nav in s:
    s = s.replace(old_nav, new_nav, 1)
elif 'view.loadUrl(uri.toString());' not in s:
    raise SystemExit('WebView navigation block not found')

# Disable cross-app notification forwarding / polling hooks, without touching printer or WebView bridges.
s = s.replace('            TelegramSender.init(this); // <-- INI YANG KAMU TANYAKAN\n', '')
s = s.replace('''            if (!isNotificationListenerEnabled()) {
                showNotificationAccessDialog();
            }
            
''', '', 1)
s = s.replace('                    mainHandler.postDelayed(() -> checkNotificationAccessStatus(), 1500);\n', '')
s = s.replace('''            if (!isNotificationListenerEnabled()) {
                showNotificationAccessDialog();
            }

''', '', 1)
s = s.replace('                    showNotificationAccessDialog();\n', '')
main.write_text(s, encoding='utf-8')

manifest = Path('android/app/src/main/AndroidManifest.xml')
x = manifest.read_text(encoding='utf-8')
x = x.replace('    <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />\n', '')
service = '''        <service
            android:name=".NotificationListenerService"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

'''
x = x.replace(service, '')
manifest.write_text(x, encoding='utf-8')

listener = Path('android/app/src/main/java/com/cetakpro/print/NotificationListenerService.java')
if listener.exists():
    listener.unlink()
