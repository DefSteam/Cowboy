package com.cowboy.app;

import android.app.Application;

/** Captures uncaught debug-build crashes before Android terminates the process. */
public final class CowboyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) CrashReporter.install(this);
    }
}
