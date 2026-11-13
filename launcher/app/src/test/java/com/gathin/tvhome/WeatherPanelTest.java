package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WeatherPanelTest {
    private static Object[] now(String place, Integer temp, int code, String text, int max, int min, int day) {
        return new Object[] {"now", place, temp, code, text, max, min, day};
    }

    private static Object[] day(String label, int code, int max, int min) {
        return new Object[] {"day", label, null, code, null, max, min, 1};
    }

    @Test
    public void threeLinesFromTheAddOnsRows() {
        List<Object[]> rows = Arrays.asList(
                now("Nola", 14, 2, "Parzialmente nuvoloso", 17, 9, 1),
                day("Sab", 0, 15, 8),
                day("Dom", 3, 16, 9),
                day("Lun", 61, 14, 7));
        CharSequence[] lines = WeatherPanel.lines(rows);
        assertEquals("Nola · 14°C  ⛅", lines[0].toString());
        assertEquals("Parzialmente nuvoloso · Max 17° Min 9°", lines[1].toString());
        assertEquals("☀️ Sab 15°/8°    ☁️ Dom 16°/9°    🌧️ Lun 14°/7°",
                lines[2].toString());
    }

    @Test
    public void noNowRowHidesThePanel() {
        assertNull(WeatherPanel.lines(Collections.emptyList()));
        assertNull(WeatherPanel.lines(Collections.singletonList(day("Sab", 0, 15, 8))));
        assertNull(WeatherPanel.lines(Collections.singletonList(now("Nola", null, 0, "Sereno", 15, 8, 1))));
    }

    @Test
    public void withoutDaysTheThirdLineIsEmpty() {
        CharSequence[] lines = WeatherPanel.lines(Collections.singletonList(now("", -2, 0, "", 1, -4, 0)));
        assertEquals("-2°C  🌙", lines[0].toString());
        assertEquals("Max 1° Min -4°", lines[1].toString());
        assertEquals("", lines[2].toString());
    }

    @Test
    public void wmoCodesToIcons() {
        assertEquals("☀️", WeatherPanel.icon(0, true));
        assertEquals("🌙", WeatherPanel.icon(0, false));
        assertEquals("☁️", WeatherPanel.icon(3, true));
        assertEquals("🌫️", WeatherPanel.icon(45, true));
        assertEquals("🌧️", WeatherPanel.icon(53, true));
        assertEquals("🌧️", WeatherPanel.icon(81, true));
        assertEquals("❄️", WeatherPanel.icon(73, true));
        assertEquals("❄️", WeatherPanel.icon(86, true));
        assertEquals("⛈️", WeatherPanel.icon(95, true));
        assertEquals("☁️", WeatherPanel.icon(42, true));
    }
}
