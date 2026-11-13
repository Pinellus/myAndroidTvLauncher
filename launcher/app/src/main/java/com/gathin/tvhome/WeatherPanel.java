package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather_panel (config.xml or a theme): the weather now and for the next days under the clock, from an add-on such
 * as Weather. The launcher only reads what the add-on publishes and stays offline. Contract: docs/modules.md.
 */
final class WeatherPanel {
    static final String META = "com.gathin.tvhome.weather";
    /** Ask the add-on again after this long with Home open; it reads Open-Meteo only when its data is old. */
    static final long READ_MS = 30 * 60_000L;
    private static final String[] COLUMNS = {"kind", "label", "temp", "code", "text", "max", "min", "day"};

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private long lastRead;
    /** The three lines, or null when there is nothing to show. */
    private CharSequence[] lines;

    private WeatherPanel(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /** Null when the key is empty, the package is not installed, or it names no provider of its own in {@value #META}. */
    static WeatherPanel create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("forecast").build();
            return new WeatherPanel(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The provider went away; the panel keeps what it had.
            }
        }
        read();
    }

    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** From the minute tick: ask again once the last answer is half an hour old. */
    void readIfOld(long elapsedMs) {
        if (elapsedMs - lastRead >= READ_MS) read();
    }

    void read() {
        lastRead = android.os.SystemClock.elapsedRealtime();
        try {
            io.execute(() -> {
                List<Object[]> rows = new ArrayList<>();
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        rows.add(new Object[] {c.getString(0), c.getString(1), c.isNull(2) ? null : c.getInt(2),
                                c.getInt(3), c.getString(4), c.getInt(5), c.getInt(6), c.getInt(7)});
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                CharSequence[] parsed = lines(rows);
                main.post(() -> {
                    lines = parsed;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    CharSequence[] lines() {
        return lines;
    }

    /**
     * "Nola · 14°C ⛅", "Parzialmente nuvoloso · Max 17° Min 9°", "☀️ Sab 15°/8°   🌧️ Dom 16°/9°": null without a
     * usable "now" row (no reading, or one too old), which hides the panel.
     */
    static CharSequence[] lines(List<Object[]> rows) {
        Object[] now = null;
        StringBuilder days = new StringBuilder();
        for (Object[] r : rows) {
            if ("now".equals(r[0]) && now == null && r[2] instanceof Integer) {
                now = r;
            } else if ("day".equals(r[0]) && r[1] instanceof String) {
                if (days.length() > 0) days.append("    ");
                days.append(icon(num(r[3]), true)).append(' ').append(r[1]).append(' ')
                        .append(num(r[5])).append("°/").append(num(r[6])).append('°');
            }
        }
        if (now == null) return null;
        String place = r1(now[1]);
        String top = (place.isEmpty() ? "" : place + " · ") + now[2] + "°C  " + icon(num(now[3]), num(now[7]) != 0);
        String text = r1(now[4]);
        String today = (text.isEmpty() ? "" : text + " · ") + "Max " + num(now[5]) + "° Min " + num(now[6]) + "°";
        return new CharSequence[] {top, today, days.toString()};
    }

    /** An emoji for a WMO weather code; the TV's colour-emoji font draws it. */
    static String icon(int code, boolean day) {
        switch (code) {
            case 0: return day ? "☀️" : "🌙";               // sun / crescent moon
            case 1: return day ? "🌤️" : "🌙";         // sun behind small cloud
            case 2: return day ? "⛅" : "☁️";                      // sun behind cloud / cloud
            case 3: return "☁️";                                       // cloud
            case 45: case 48: return "🌫️";                       // fog
            case 71: case 73: case 75: case 77: case 85: case 86: return "❄️"; // snowflake
            default:
                if (code >= 95) return "⛈️";                           // thunder cloud and rain
                if (code >= 51 && code <= 82) return "🌧️";       // cloud with rain
                return "☁️";
        }
    }

    private static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static String r1(Object o) {
        return o instanceof String ? (String) o : "";
    }
}
