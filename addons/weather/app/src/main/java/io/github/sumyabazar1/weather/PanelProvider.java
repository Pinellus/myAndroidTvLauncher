package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather for the Clean Your TV launcher's weather_panel, under the Home clock (docs/modules.md). Read-only and
 * exported without a permission: it holds only what Home shows anyway (the place's name and its weather).
 * No service: a query hands back the last reading kept on disk, and when that is over 30 minutes old one thread
 * reads Open-Meteo and then notifies the launcher. An empty cursor (no reading in the last 3 hours) hides the panel.
 */
public final class PanelProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.panel";
    static final Uri FORECAST = Uri.parse("content://" + AUTHORITY + "/forecast");
    private static final AtomicBoolean READING = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"forecast".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences place = ctx.getSharedPreferences("place", Context.MODE_PRIVATE);
        String name = place.getString("name", Panel.DEFAULT_NAME);
        double lat = place.contains("lat") ? Double.longBitsToDouble(place.getLong("lat", 0)) : Panel.DEFAULT_LAT;
        double lon = place.contains("lon") ? Double.longBitsToDouble(place.getLong("lon", 0)) : Panel.DEFAULT_LON;
        String key = String.format(Locale.ROOT, "%s|%.4f|%.4f", name, lat, lon);

        SharedPreferences saved = ctx.getSharedPreferences("panel", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        // A reading for another place (the owner picked a new city) counts as none.
        long fetched = key.equals(saved.getString("for", "")) ? saved.getLong("at", 0) : 0;
        MatrixCursor c = new MatrixCursor(Panel.COLUMNS);
        if (Panel.fresh(fetched, now)) {
            try {
                rows(ctx, c, name, new JSONObject(saved.getString("json", "")));
            } catch (JSONException | RuntimeException bad) {
                c = new MatrixCursor(Panel.COLUMNS);
                fetched = 0;
            }
        }
        c.setNotificationUri(ctx.getContentResolver(), FORECAST);
        if (Panel.due(fetched, saved.getLong("tried", 0), now)) refresh(ctx.getApplicationContext(), key, lat, lon);
        return c;
    }

    private static void rows(Context ctx, MatrixCursor c, String name, JSONObject reply) throws JSONException {
        JSONObject current = reply.getJSONObject("current");
        JSONObject daily = reply.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        double temp = current.getDouble("temperature_2m");
        int code = Forecast.snowIfCold(current.getInt("weather_code"), temp, current.optDouble("relative_humidity_2m", 100));
        boolean day = current.optInt("is_day", 1) == 1;
        c.addRow(Panel.now(name, temp, code, ctx.getString(Forecast.sky(code, day)), max.getDouble(0), min.getDouble(0), day));
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        // Day 0 is today; the panel shows the days after it, from today's date so a reading from last night is right.
        LocalDate today = LocalDate.now();
        for (int i = 1; i < dates.length(); i++) {
            LocalDate date = LocalDate.parse(dates.getString(i));
            if (!date.isAfter(today)) continue;
            if (c.getCount() > Panel.DAYS || codes.isNull(i) || max.isNull(i) || min.isNull(i)) break;
            c.addRow(Panel.day(date, Forecast.snowIfCold(codes.getInt(i), max.getDouble(i), 100),
                    max.getDouble(i), min.getDouble(i), locale));
        }
    }

    /** One read of Open-Meteo on its own thread, at most one at a time; the launcher hears of it through notifyChange. */
    private static void refresh(Context app, String key, double lat, double lon) {
        if (!READING.compareAndSet(false, true)) return;
        SharedPreferences saved = app.getSharedPreferences("panel", Context.MODE_PRIVATE);
        saved.edit().putLong("tried", System.currentTimeMillis()).apply();
        new Thread(() -> {
            try {
                JSONObject reply = Forecast.get(String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&models=icon_seamless"
                                + "&current=temperature_2m,relative_humidity_2m,weather_code,is_day"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=%d",
                        lat, lon, Panel.DAYS + 2));
                saved.edit().putString("json", reply.toString()).putString("for", key)
                        .putLong("at", System.currentTimeMillis()).apply();
                app.getContentResolver().notifyChange(FORECAST, null);
            } catch (Exception offline) {
                // No network or a bad reply: keep the last reading, try again after RETRY_MS.
            } finally {
                READING.set(false);
            }
        }, "weather-panel").start();
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".forecast";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
