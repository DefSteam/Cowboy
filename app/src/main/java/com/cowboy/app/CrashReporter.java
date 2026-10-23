package com.cowboy.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Stores uncaught debug-build crash reports locally for later sharing by the user. */
final class CrashReporter {
    private static final String REPORTS_DIR = "crash-reports";

    private CrashReporter() { }

    static void install(Context context) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                save(context, thread, error);
            } catch (Throwable ignored) {
                // Preserve the original crash even if writing its report fails.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static File reportsDirectory(Context context) {
        return new File(context.getFilesDir(), REPORTS_DIR);
    }

    static File latestReport(Context context) {
        File[] reports = reportsDirectory(context).listFiles((dir, name) -> name.endsWith(".txt"));
        if (reports == null || reports.length == 0) return null;
        File latest = reports[0];
        for (File report : reports) {
            if (report.lastModified() > latest.lastModified()) latest = report;
        }
        return latest;
    }

    private static void save(Context context, Thread thread, Throwable error) throws Exception {
        File directory = reportsDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) return;

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File report = new File(directory, "crash-" + stamp + ".txt");
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));

        String versionName = "unknown";
        long versionCode = -1;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName == null ? "unknown" : info.versionName;
            versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) { }

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(report), StandardCharsets.UTF_8)) {
            writer.write("Cowboy debug crash report\n");
            writer.write("Time: " + new Date() + "\n");
            writer.write("App version: " + versionName + " (" + versionCode + ")\n");
            writer.write("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
            writer.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
            writer.write("Thread: " + thread.getName() + "\n\n");
            writer.write(trace.toString());
        }
    }
}
