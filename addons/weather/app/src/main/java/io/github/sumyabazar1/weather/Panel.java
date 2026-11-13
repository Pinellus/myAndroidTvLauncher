package io.github.sumyabazar1.weather;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The rows PanelProvider gives the launcher's weather_panel (docs/modules.md): one "now" row, then one "day" row
 * per day after today. Whole degrees Celsius, words in the TV's language. Plain Java, so the JVM tests reach it.
 */
final class Panel {
    static final String[] COLUMNS = {"kind", "label", "temp", "code", "text", "max", "min", "day"};
    /** Read Open-Meteo again after this long. */
    static final long REFRESH_MS = Forecast.REFRESH_MS;
    /** Wait this long after a failed read before the next try. */
    static final long RETRY_MS = Forecast.RETRY_MS;
    /** Older than this (no network for hours) the panel shows nothing rather than stale weather. */
    static final long STALE_MS = 3 * 60 * 60_000L;
    /** Days after today in the panel. */
    static final int DAYS = 3;
    /** The place when none was chosen in the app: Nola, Campania. */
    static final String DEFAULT_NAME = "Nola";
    static final double DEFAULT_LAT = 40.9261, DEFAULT_LON = 14.5281;

    private Panel() { }

    static Object[] now(String place, double tempC, int code, String text, double maxC, double minC, boolean day) {
        return new Object[] {"now", place, round(tempC), code, text, round(maxC), round(minC), day ? 1 : 0};
    }

    static Object[] day(LocalDate date, int code, double maxC, double minC, Locale locale) {
        return new Object[] {"day", dayLabel(date, locale), null, code, null, round(maxC), round(minC), 1};
    }

    /** "Sab", "Dom": the short weekday, capitalised, without the trailing dot some languages add. */
    static String dayLabel(LocalDate date, Locale locale) {
        String s = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }

    static boolean fresh(long fetchedMs, long nowMs) {
        return fetchedMs > 0 && nowMs - fetchedMs >= 0 && nowMs - fetchedMs < STALE_MS;
    }

    static boolean due(long fetchedMs, long triedMs, long nowMs) {
        return nowMs - fetchedMs >= REFRESH_MS && nowMs - triedMs >= RETRY_MS;
    }

    static int round(double c) {
        return (int) Math.round(c);
    }
}
