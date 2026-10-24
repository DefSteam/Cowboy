package com.cowboy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Thin Android shell for the shared web app in /web. */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 610;
    private static final int INSTALL_PERMISSION = 611;
    private static final String RELEASES_API = "https://api.github.com/repos/DefSteam/Cowboy/releases/latest";

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private File pendingUpdateApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new CowboyBridge(), "CowboyNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                openExternal(uri);
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    startActivityForResult(intent, PICK_FILE);
                } catch (ActivityNotFoundException error) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    toast("No file picker is available on this device");
                }
                return true;
            }
        });
        setContentView(webView);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileChooserCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                    ? new Uri[]{data.getData()} : null;
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUpdateApk != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().canRequestPackageInstalls()) {
            installPendingUpdate();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript("(()=>{const d=[...document.querySelectorAll('dialog[open]')].pop();"
                + "if(d){d.close();return}const p=document.querySelector('.page.active');"
                + "if(p&&p.id!=='todayPage'){document.querySelector('[data-page=\\\"todayPage\\\"]').click();return}"
                + "window.CowboyNative.exitRequested()})()", null);
    }

    private final class CowboyBridge {
        @JavascriptInterface public String versionName() { return BuildConfig.VERSION_NAME; }
        @JavascriptInterface public void checkForUpdate() { MainActivity.this.checkForUpdate(); }
        @JavascriptInterface public void shareCrashReport() {
            runOnUiThread(MainActivity.this::shareLatestCrashReport);
        }
        @JavascriptInterface public void exitRequested() { runOnUiThread(MainActivity.this::finish); }
    }

    private void checkForUpdate() {
        toast("Checking for Cowboy updates…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
                JSONObject release = new JSONObject(new String(readAll(connection.getInputStream()),
                        java.nio.charset.StandardCharsets.UTF_8));
                String tag = release.optString("tag_name", "");
                String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                String assetName = "Cowboy-" + latestVersion + ".apk";
                JSONArray assets = release.optJSONArray("assets");
                String downloadUrl = null;
                if (assets != null) {
                    for (int index = 0; index < assets.length(); index++) {
                        JSONObject asset = assets.getJSONObject(index);
                        if (assetName.equals(asset.optString("name"))) {
                            downloadUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (downloadUrl == null) throw new IllegalStateException("Release APK not found: " + assetName);
                if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) <= 0) {
                    runOnUiThread(() -> toast("Cowboy is up to date"));
                    return;
                }
                File apk = downloadApk(downloadUrl);
                runOnUiThread(() -> offerUpdate(latestVersion, apk));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not check for updates" : error.getMessage();
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update check failed").setMessage(message)
                        .setPositiveButton("OK", null).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "cowboy-update-check").start();
    }

    private File downloadApk(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        File directory = new File(getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create update folder");
        File apk = new File(directory, "Cowboy-update.apk");
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(apk)) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        } finally {
            connection.disconnect();
        }
        if (apk.length() < 1024) throw new IllegalStateException("Downloaded APK is incomplete");
        return apk;
    }

    private void offerUpdate(String version, File apk) {
        pendingUpdateApk = apk;
        new AlertDialog.Builder(this)
                .setTitle("Cowboy " + version + " is available")
                .setMessage("Download complete. Install the update now?")
                .setNegativeButton("Later", (dialog, which) -> pendingUpdateApk = null)
                .setPositiveButton("Install", (dialog, which) -> installPendingUpdate())
                .show();
    }

    private void installPendingUpdate() {
        if (pendingUpdateApk == null || !pendingUpdateApk.isFile()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(settings, INSTALL_PERMISSION);
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingUpdateApk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
        pendingUpdateApk = null;
    }

    private void shareLatestCrashReport() {
        File report = CrashReporter.latestReport(this);
        if (report == null) {
            toast("No saved crash report was found");
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", report);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Cowboy debug crash report");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share crash report"));
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            toast("No app can open that link");
        }
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private static byte[] readAll(InputStream input) throws Exception {
        try (InputStream in = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? parseVersionPart(a[i]) : 0;
            int bi = i < b.length ? parseVersionPart(b[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int parseVersionPart(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
