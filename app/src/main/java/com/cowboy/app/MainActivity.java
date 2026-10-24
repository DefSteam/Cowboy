package com.cowboy.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Thin Android shell for the shared web app in /web. */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 610;
    private static final int INSTALL_PERMISSION = 611;
    private static final int SAVE_JSON_FILE = 612;
    private static final String UPDATE_PREFS = "cowboy_update";
    private static final String RELEASES_API = "https://api.github.com/repos/DefSteam/Cowboy/releases/latest";

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private File pendingUpdateApk;
    private String pendingUpdateVersion;
    private volatile String availableUpdateUrl;
    private volatile String availableUpdateVersion;
    private volatile boolean updateCheckRunning;
    private volatile boolean updateDownloadRunning;
    private String pendingExportContents;

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
        restorePendingUpdate();
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileChooserCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                    ? new Uri[]{data.getData()} : null;
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        } else if (requestCode == SAVE_JSON_FILE) {
            String contents = pendingExportContents;
            pendingExportContents = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && contents != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new java.io.IOException("Could not open the selected file.");
                    output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    toast("Cowboy data exported");
                } catch (java.io.IOException error) {
                    toast("Could not save the Cowboy data file");
                }
            }
        } else if (requestCode == INSTALL_PERMISSION
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
        @JavascriptInterface public String updateState() { return MainActivity.this.updateStateJson(); }
        @JavascriptInterface public void downloadUpdate() { MainActivity.this.downloadUpdate(); }
        @JavascriptInterface public void installUpdate() {
            runOnUiThread(MainActivity.this::installPendingUpdate);
        }
        @JavascriptInterface public void shareCrashReport() {
            runOnUiThread(MainActivity.this::shareLatestCrashReport);
        }
        @JavascriptInterface public void exportJson(String filename, String contents) {
            runOnUiThread(() -> saveJsonFile(filename, contents));
        }
        @JavascriptInterface public void exitRequested() { runOnUiThread(MainActivity.this::finish); }
    }

    private void saveJsonFile(String filename, String contents) {
        pendingExportContents = contents;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(intent, SAVE_JSON_FILE);
        } catch (ActivityNotFoundException error) {
            pendingExportContents = null;
            toast("No file picker is available on this device");
        }
    }

    private void checkForUpdate() {
        if (updateCheckRunning || updateDownloadRunning) return;
        updateCheckRunning = true;
        availableUpdateUrl = null;
        availableUpdateVersion = null;
        reportUpdateState("checking", "Checking for the latest version…");
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
                    reportUpdateCallback("onCowboyUpdateCurrent", JSONObject.quote(BuildConfig.VERSION_NAME));
                    return;
                }
                availableUpdateUrl = downloadUrl;
                availableUpdateVersion = latestVersion;
                reportUpdateCallback("onCowboyUpdateAvailable", JSONObject.quote(latestVersion));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not check for updates" : error.getMessage();
                reportUpdateCallback("onCowboyUpdateError", JSONObject.quote(message));
            } finally {
                if (connection != null) connection.disconnect();
                updateCheckRunning = false;
            }
        }, "cowboy-update-check").start();
    }

    private void downloadUpdate() {
        if (updateDownloadRunning) return;
        final String url = availableUpdateUrl;
        final String version = availableUpdateVersion;
        if (url == null || version == null) {
            reportUpdateCallback("onCowboyUpdateError", JSONObject.quote("Check for updates again before downloading."));
            return;
        }
        updateDownloadRunning = true;
        reportUpdateState("downloading", "Downloading Cowboy " + version + "…");
        new Thread(() -> {
            try {
                File apk = downloadApk(url, version);
                pendingUpdateApk = apk;
                pendingUpdateVersion = version;
                getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).edit()
                        .putString("apk_path", apk.getAbsolutePath())
                        .putString("version", version).apply();
                availableUpdateUrl = null;
                availableUpdateVersion = null;
                reportUpdateCallback("onCowboyUpdateDownloaded", JSONObject.quote(version));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not download the update" : error.getMessage();
                reportUpdateCallback("onCowboyUpdateError", JSONObject.quote(message));
            } finally {
                updateDownloadRunning = false;
            }
        }, "cowboy-update-download").start();
    }

    private File downloadApk(String urlText, String version) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
        File directory = new File(getFilesDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create update folder");
        File apk = new File(directory, "Cowboy-update.apk");
        File partial = new File(directory, "Cowboy-update.apk.part");
        long total = connection.getContentLength(), received = 0;
        int lastProgress = -2;
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
                received += length;
                int progress = total > 0 ? (int) Math.min(100, received * 100 / total) : -1;
                if (progress != lastProgress) {
                    lastProgress = progress;
                    reportUpdateProgress(version, progress, received, total);
                }
            }
        } finally {
            connection.disconnect();
        }
        if (partial.length() < 1024) {
            partial.delete();
            throw new IllegalStateException("Downloaded APK is incomplete");
        }
        if (apk.exists() && !apk.delete()) throw new IllegalStateException("Could not replace the previous update file");
        if (!partial.renameTo(apk)) throw new IllegalStateException("Could not finish saving the update");
        return apk;
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
        try {
            startActivity(install);
        } catch (ActivityNotFoundException error) {
            reportUpdateCallback("onCowboyUpdateError", JSONObject.quote("No Android package installer is available."));
        }
    }

    private void restorePendingUpdate() {
        SharedPreferences preferences = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        String path = preferences.getString("apk_path", null);
        String version = preferences.getString("version", null);
        if (path == null || version == null) return;
        File apk = new File(path);
        if (compareVersions(BuildConfig.VERSION_NAME, version) >= 0) {
            preferences.edit().clear().apply();
            apk.delete();
        } else if (apk.isFile()) {
            pendingUpdateApk = apk;
            pendingUpdateVersion = version;
        } else {
            preferences.edit().clear().apply();
        }
    }

    private String updateStateJson() {
        JSONObject state = new JSONObject();
        try {
            boolean ready = pendingUpdateApk != null && pendingUpdateApk.isFile();
            state.put("ready", ready);
            if (ready) state.put("version", pendingUpdateVersion);
        } catch (Exception ignored) { }
        return state.toString();
    }

    private void reportUpdateState(String state, String message) {
        reportUpdateCallback("onCowboyUpdateState", JSONObject.quote(state) + "," + JSONObject.quote(message));
    }

    private void reportUpdateProgress(String version, int progress, long received, long total) {
        reportUpdateCallback("onCowboyUpdateProgress", JSONObject.quote(version) + "," + progress + "," + received + "," + total);
    }

    private void reportUpdateCallback(String method, String arguments) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.CowboyUpdate&&window.CowboyUpdate." + method + "(" + arguments + ")", null);
        });
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
