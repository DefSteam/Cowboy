package com.cowboy.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads, verifies, stages, and activates signed web runtime bundles. */
final class RuntimeUpdater {
    interface Listener {
        void onStatus(String version, String message);
        void onProgress(String version, int percent, long received, long total);
        void onCurrent(String version);
        void onReady(String version);
        void onError(String message);
    }

    private static final String PREFS = "cowboy_runtime";
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/DefSteam/Cowboy/master/runtime/latest.json";
    private static final String SIGNATURE_URL = "https://raw.githubusercontent.com/DefSteam/Cowboy/master/runtime/latest.sig";
    private static final int SHELL_API = 1;
    private static final long MAX_BUNDLE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 512;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final File runtimeRoot;
    private volatile boolean checking;

    RuntimeUpdater(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.runtimeRoot = new File(this.context.getFilesDir(), "cowboy-runtime");
    }

    String currentVersion() {
        String active = preferences.getString("active_version", null);
        return isVersion(active) && new File(versionDirectory(active), "index.html").isFile()
                ? active : BuildConfig.RUNTIME_VERSION;
    }

    File activeDirectory() {
        String active = preferences.getString("active_version", null);
        File directory = isVersion(active) ? versionDirectory(active) : null;
        return directory != null && new File(directory, "index.html").isFile() ? directory : null;
    }

    /** Rolls back a runtime that failed to finish booting on its previous launch. */
    void prepareForLaunch() {
        String pending = preferences.getString("pending_boot_version", null);
        String active = preferences.getString("active_version", null);
        if (pending == null || !pending.equals(active)) return;
        int attempts = preferences.getInt("boot_attempts", 0) + 1;
        if (attempts > 1) {
            String previous = preferences.getString("previous_version", null);
            SharedPreferences.Editor edit = preferences.edit().remove("pending_boot_version")
                    .remove("boot_attempts");
            if (isVersion(previous) && new File(versionDirectory(previous), "index.html").isFile()) {
                edit.putString("active_version", previous);
            } else {
                edit.remove("active_version");
            }
            edit.apply();
        } else {
            preferences.edit().putInt("boot_attempts", attempts).apply();
        }
    }

    void markRuntimeHealthy() {
        String pending = preferences.getString("pending_boot_version", null);
        if (pending == null || !pending.equals(preferences.getString("active_version", null))) return;
        if (activeDirectory() == null) return;
        preferences.edit().putString("healthy_version", pending)
                .remove("pending_boot_version").remove("boot_attempts").apply();
    }

    void checkAndUpdate() {
        if (checking) return;
        checking = true;
        status(currentVersion(), "Checking for runtime updates…");
        new Thread(() -> {
            try {
                byte[] manifestBytes = downloadSmallFile(MANIFEST_URL);
                byte[] signatureBytes = downloadSmallFile(SIGNATURE_URL);
                verifyManifestSignature(manifestBytes, signatureBytes);
                JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
                String version = validateManifest(manifest);
                String bundleUrl = manifest.getString("bundleUrl");
                String expectedHash = manifest.getString("sha256").toLowerCase(Locale.ROOT);
                String installed = currentVersion();
                if (compareVersions(version, installed) <= 0) {
                    main.post(() -> listener.onCurrent(installed));
                    return;
                }
                downloadAndActivate(bundleUrl, version, expectedHash);
                main.post(() -> listener.onReady(version));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not update the runtime." : error.getMessage();
                main.post(() -> listener.onError(message));
            } finally {
                checking = false;
            }
        }, "cowboy-runtime-update").start();
    }

    /** Installs a single-file .cowboy-runtime package from a document provider. */
    void installPackage(InputStream packageStream) {
        if (checking) {
            try { packageStream.close(); } catch (Exception ignored) { }
            main.post(() -> listener.onError("A runtime update is already in progress."));
            return;
        }
        checking = true;
        status(currentVersion(), "Checking signed runtime package…");
        new Thread(() -> {
            File packageDirectory = new File(runtimeRoot, "imports");
            File bundle = new File(packageDirectory, "selected-runtime.zip");
            File manifestFile = new File(packageDirectory, "manifest.json");
            File signatureFile = new File(packageDirectory, "manifest.sig");
            try (InputStream source = packageStream; ZipInputStream zip = new ZipInputStream(source)) {
                if (!packageDirectory.isDirectory() && !packageDirectory.mkdirs()) {
                    throw new IllegalStateException("Could not prepare the runtime import folder.");
                }
                bundle.delete(); manifestFile.delete(); signatureFile.delete();
                long total = 0;
                int entries = 0;
                boolean foundBundle = false, foundManifest = false, foundSignature = false;
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 8) throw new IllegalStateException("The runtime package contains too many files.");
                    if (entry.isDirectory()) continue;
                    String name = new File(entry.getName()).getName();
                    File output;
                    long limit;
                    if ("bundle.zip".equals(name) && !foundBundle) { output = bundle; foundBundle = true; limit = MAX_BUNDLE_BYTES; }
                    else if ("manifest.json".equals(name) && !foundManifest) { output = manifestFile; foundManifest = true; limit = 128 * 1024; }
                    else if ("manifest.sig".equals(name) && !foundSignature) { output = signatureFile; foundSignature = true; limit = 16 * 1024; }
                    else throw new IllegalStateException("This is not a supported Cowboy runtime package.");
                    try (FileOutputStream out = new FileOutputStream(output)) {
                        int length;
                        while ((length = zip.read(buffer)) != -1) {
                            total += length;
                            if (length > limit || output.length() + length > limit || total > MAX_BUNDLE_BYTES + 160 * 1024L) {
                                throw new IllegalStateException("The runtime package is larger than allowed.");
                            }
                            out.write(buffer, 0, length);
                        }
                    }
                    zip.closeEntry();
                }
                if (!foundBundle || !foundManifest || !foundSignature) {
                    throw new IllegalStateException("The package needs bundle.zip, manifest.json, and manifest.sig.");
                }
                byte[] manifestBytes = readFile(manifestFile, 128 * 1024);
                verifyManifestSignature(manifestBytes, readFile(signatureFile, 16 * 1024));
                JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
                String version = validateManifest(manifest);
                if (version.equals(currentVersion())) {
                    throw new IllegalStateException("This runtime version is already active.");
                }
                String expectedHash = manifest.optString("sha256", "").toLowerCase(Locale.ROOT);
                if (!toHex(sha256(bundle)).equals(expectedHash)) {
                    throw new IllegalStateException("The runtime bundle checksum did not match the signed manifest.");
                }
                activateBundle(bundle, version);
                main.post(() -> listener.onReady(version));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not install this runtime package." : error.getMessage();
                main.post(() -> listener.onError(message));
            } finally {
                bundle.delete(); manifestFile.delete(); signatureFile.delete();
                checking = false;
            }
        }, "cowboy-runtime-import").start();
    }

    private void downloadAndActivate(String urlText, String version, String expectedHash) throws Exception {
        URL url = new URL(urlText);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
        connection.setInstanceFollowRedirects(true);
        File downloadDirectory = new File(runtimeRoot, "downloads");
        if (!downloadDirectory.isDirectory() && !downloadDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create the runtime download folder.");
        }
        File bundle = new File(downloadDirectory, "runtime-" + version + ".zip.part");
        long total = connection.getContentLength();
        if (total > MAX_BUNDLE_BYTES) throw new IllegalStateException("The runtime bundle is too large.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long received = 0;
        int lastProgress = -2;
        status(version, "Downloading runtime " + version + "…");
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(bundle)) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = input.read(buffer)) != -1) {
                received += length;
                if (received > MAX_BUNDLE_BYTES) throw new IllegalStateException("The runtime bundle is too large.");
                digest.update(buffer, 0, length);
                output.write(buffer, 0, length);
                int progress = total > 0 ? (int) Math.min(100, received * 100 / total) : -1;
                if (progress != lastProgress) {
                    lastProgress = progress;
                    int value = progress;
                    long receivedNow = received;
                    long totalNow = total;
                    main.post(() -> listener.onProgress(version, value, receivedNow, totalNow));
                }
            }
        } catch (Exception error) {
            bundle.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
        String actualHash = toHex(digest.digest());
        if (!actualHash.equals(expectedHash)) {
            bundle.delete();
            throw new IllegalStateException("The runtime bundle checksum did not match.");
        }

        try {
            activateBundle(bundle, version);
        } finally {
            bundle.delete();
        }
    }

    private void activateBundle(File bundle, String version) throws Exception {
        File staging = new File(runtimeRoot, "staging-" + version);
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IllegalStateException("Could not prepare the runtime update.");
        try {
            extractSafely(bundle, staging);
            for (String required : new String[]{"index.html", "styles.css", "app.js"}) {
                if (!new File(staging, required).isFile()) {
                    throw new IllegalStateException("The runtime bundle is incomplete (missing " + required + ").");
                }
            }
            File target = versionDirectory(version);
            File versionsDirectory = target.getParentFile();
            if (versionsDirectory == null || (!versionsDirectory.isDirectory() && !versionsDirectory.mkdirs())) {
                throw new IllegalStateException("Could not prepare the runtime version folder.");
            }
            File displaced = new File(versionsDirectory, version + ".replaced");
            deleteRecursively(displaced);
            if (target.exists() && !target.renameTo(displaced)) {
                throw new IllegalStateException("Could not safely replace the saved runtime version.");
            }
            if (!staging.renameTo(target)) {
                if (displaced.exists()) displaced.renameTo(target);
                throw new IllegalStateException("Could not stage the runtime update.");
            }
            deleteRecursively(displaced);
            String previous = preferences.getString("active_version", null);
            SharedPreferences.Editor edit = preferences.edit().putString("active_version", version)
                    .putString("pending_boot_version", version).putInt("boot_attempts", 0);
            if (isVersion(previous)) edit.putString("previous_version", previous);
            edit.apply();
            pruneOldVersions(version, previous);
        } catch (Exception error) {
            deleteRecursively(staging);
            throw error;
        }
    }

    private String validateManifest(JSONObject manifest) throws Exception {
        if (!"cowboy-runtime".equals(manifest.optString("format"))) {
            throw new IllegalStateException("The runtime manifest has an unknown format.");
        }
        String version = manifest.optString("version", "");
        if (!isVersion(version)) throw new IllegalStateException("The runtime version is invalid.");
        if (manifest.optInt("minimumShellApi", Integer.MAX_VALUE) > SHELL_API) {
            throw new IllegalStateException("This runtime needs a newer Cowboy app. Install the app update first.");
        }
        URL bundleUrl = new URL(manifest.optString("bundleUrl", ""));
        if (!"https".equals(bundleUrl.getProtocol()) || !"github.com".equalsIgnoreCase(bundleUrl.getHost())) {
            throw new IllegalStateException("The runtime bundle URL is not trusted.");
        }
        if (!manifest.optString("sha256", "").matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException("The runtime bundle checksum is invalid.");
        }
        return version;
    }

    private static byte[] readFile(File file, int maxBytes) throws Exception {
        if (file.length() > maxBytes) throw new IllegalStateException("The runtime package metadata is too large.");
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (output.size() + length > maxBytes) throw new IllegalStateException("The runtime package metadata is too large.");
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static byte[] sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        return digest.digest();
    }

    private void extractSafely(File zipFile, File destination) throws Exception {
        String destinationPath = destination.getCanonicalPath();
        String root = destinationPath + File.separator;
        long expanded = 0;
        int entries = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IllegalStateException("The runtime bundle has too many files.");
                File outputFile = new File(destination, entry.getName());
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.equals(destinationPath) && !outputPath.startsWith(root)) {
                    throw new IllegalStateException("The runtime bundle contains an unsafe path.");
                }
                if (entry.isDirectory()) {
                    if (!outputFile.isDirectory() && !outputFile.mkdirs()) throw new IllegalStateException("Could not unpack the runtime bundle.");
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Could not unpack the runtime bundle.");
                    try (FileOutputStream output = new FileOutputStream(outputFile)) {
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            expanded += length;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IllegalStateException("The runtime bundle expands beyond the allowed size.");
                            output.write(buffer, 0, length);
                        }
                    }
                }
                input.closeEntry();
            }
        }
    }

    private byte[] downloadSmallFile(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText + "?t=" + System.currentTimeMillis()).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (output.size() + length > 128 * 1024) throw new IllegalStateException("The runtime update information is too large.");
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private void verifyManifestSignature(byte[] manifest, byte[] signatureText) throws Exception {
        String encoded = new String(signatureText, StandardCharsets.US_ASCII).trim();
        byte[] signatureBytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
        try (InputStream input = context.getResources().openRawResource(R.raw.runtime_signing_public)) {
            String pem = new String(readAll(input), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            byte[] keyBytes = android.util.Base64.decode(pem, android.util.Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            java.security.PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(manifest);
            if (!verifier.verify(signatureBytes)) throw new IllegalStateException("The runtime signature is not valid.");
        }
    }

    private void pruneOldVersions(String active, String previous) {
        File versions = new File(runtimeRoot, "versions");
        File[] children = versions.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (!name.equals(active) && !name.equals(previous)) deleteRecursively(child);
        }
    }

    private File versionDirectory(String version) {
        return new File(new File(runtimeRoot, "versions"), version);
    }

    private void status(String version, String message) {
        main.post(() -> listener.onStatus(version, message));
    }

    private static boolean isVersion(String version) {
        return version != null && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+");
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toByteArray();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
