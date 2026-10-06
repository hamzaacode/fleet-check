package com.fleetcheck.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuarterTest {

    @Test
    void canonicalFormIsYearDashQuarter() {
        assertEquals("2026-Q4", Quarter.of(2026, 4).canonical());
        assertEquals("2026-Q4", Quarter.of(2026, 4).toString());
    }

    @Test
    void parsesItsOwnCanonicalForm() {
        assertEquals(Quarter.of(2026, 4), Quarter.parse("2026-Q4"));
        assertEquals(Quarter.of(1999, 1), Quarter.parse(Quarter.of(1999, 1).canonical()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-Q5", "2026-Q0", "2026-q4", "2026Q4", "26-Q4", " 2026-Q4", "2026-Q4 ",
        "2026-Q04", "0999-Q1", "２０２６-Q4", ""})
    void rejectsAnythingButTheCanonicalForm(String text) {
        assertThrows(IllegalArgumentException.class, () -> Quarter.parse(text));
    }

    @Test
    void rejectsNullText() {
        assertThrows(IllegalArgumentException.class, () -> Quarter.parse(null));
    }

    @Test
    void rejectsOutOfRangeComponents() {
        assertThrows(IllegalArgumentException.class, () -> Quarter.of(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> Quarter.of(2026, 5));
        assertThrows(IllegalArgumentException.class, () -> Quarter.of(999, 1));
        assertThrows(IllegalArgumentException.class, () -> Quarter.of(10_000, 1));
    }

    @Test
    void canonicalFormIgnoresTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // Thai digits: String.format("%d") would emit non-ASCII digits under this locale.
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
            assertEquals("2026-Q4", Quarter.of(2026, 4).canonical());
        } finally {
            Locale.setDefault(original);
        }
    }
}
