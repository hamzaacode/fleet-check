package com.fleetcheck.selection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A calendar quarter with canonical form {@code "2026-Q4"}.
 *
 * <p>The canonical form is fed into the hash, so it must be byte-for-byte stable: it is built
 * from ASCII digits only and never passes through locale-sensitive formatting.
 */
public record Quarter(int year, int quarter) {

    static final int MIN_YEAR = 1000;
    static final int MAX_YEAR = 9999;

    // [0-9] rather than \d keeps the accepted digits ASCII regardless of regex flags.
    private static final Pattern CANONICAL = Pattern.compile("([0-9]{4})-Q([1-4])");

    public Quarter {
        // A four-digit year means the canonical form needs no padding and has exactly one spelling.
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException(
                    "year must be between " + MIN_YEAR + " and " + MAX_YEAR + ": " + year);
        }
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("quarter must be between 1 and 4: " + quarter);
        }
    }

    public static Quarter of(int year, int quarter) {
        return new Quarter(year, quarter);
    }

    /** Parses the canonical form only; anything else is rejected rather than guessed at. */
    public static Quarter parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("quarter text must not be null");
        }
        Matcher matcher = CANONICAL.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("quarter must look like 2026-Q4: '" + text + "'");
        }
        return new Quarter(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /** The exact string hashed into scores and fingerprints. */
    public String canonical() {
        return year + "-Q" + quarter;
    }

    @Override
    public String toString() {
        return canonical();
    }
}
