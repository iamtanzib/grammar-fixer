package com.grammarfixer.app;

import android.app.Application;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

// Saves the last crash so the app can show it on next launch (there is no logcat on users' phones).
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String info = "Grammar Fixer " + BuildInfo.version(this) + ", Android " + Build.VERSION.RELEASE
                    + " (" + Build.MANUFACTURER + " " + Build.MODEL + "), thread " + thread.getName() + "\n\n" + sw;
                MainActivity.prefs(this).edit().putString("crash", info).commit();   // commit: the process dies right after
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, e);
        });
    }
}

final class BuildInfo {
    static String version(Application a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }
}
