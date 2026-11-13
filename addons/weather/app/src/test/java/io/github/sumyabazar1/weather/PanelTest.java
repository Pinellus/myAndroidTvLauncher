package io.github.sumyabazar1.weather;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

/** The rows the launcher's weather_panel reads, and when the provider reads Open-Meteo again. */
public class PanelTest {
    @Test
    public void nowRowRoundsToWholeDegrees() {
        assertArrayEquals(new Object[] {"now", "Nola", 14, 2, "Parzialmente nuvoloso", 17, 9, 1},
                Panel.now("Nola", 13.6, 2, "Parzialmente nuvoloso", 16.5, 8.7, true));
    }

    @Test
    public void dayRowHasTheShortWeekday() {
        // 14 November 2026 is a Saturday.
        assertArrayEquals(new Object[] {"day", "Sab", null, 61, null, 15, 8, 1},
                Panel.day(LocalDate.of(2026, 11, 14), 61, 15.2, 7.8, Locale.ITALIAN));
        assertEquals("Dom", Panel.dayLabel(LocalDate.of(2026, 11, 15), Locale.ITALIAN));
        assertEquals("Sun", Panel.dayLabel(LocalDate.of(2026, 11, 15), Locale.ENGLISH));
    }

    @Test
    public void staleReadingsHideThePanel() {
        long now = 10 * Panel.STALE_MS;
        assertFalse(Panel.fresh(0, now));
        assertTrue(Panel.fresh(now - 60_000, now));
        assertFalse(Panel.fresh(now - Panel.STALE_MS, now));
    }

    @Test
    public void readsAgainAfterHalfAnHourButNotRightAfterAFailure() {
        long now = 10 * Panel.STALE_MS;
        assertFalse(Panel.due(now - 60_000, 0, now));
        assertTrue(Panel.due(now - Panel.REFRESH_MS, 0, now));
        assertFalse(Panel.due(now - Panel.REFRESH_MS, now - 60_000, now));
        assertTrue(Panel.due(0, 0, now));
    }
}
