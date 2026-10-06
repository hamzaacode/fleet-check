package com.fleetcheck.selection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class CanonicalEncodingTest {

    @Test
    void splittingAFieldDifferentlyGivesADifferentEncoding() {
        byte[] abThenC = CanonicalEncoding.encode("ab", "c");
        byte[] aThenBc = CanonicalEncoding.encode("a", "bc");

        assertFalse(Arrays.equals(abThenC, aThenBc));
    }

    @Test
    void eachFieldIsBigEndianLengthThenUtf8Bytes() {
        assertArrayEquals(
                HexFormat.of().parseHex("00000002" + "6162" + "00000001" + "63"),
                CanonicalEncoding.encode("ab", "c"));
    }

    @Test
    void lengthCountsUtf8BytesNotChars() {
        // "é" is one char but two UTF-8 bytes (C3 A9).
        assertArrayEquals(HexFormat.of().parseHex("00000002c3a9"), CanonicalEncoding.encode("é"));
    }

    @Test
    void emptyFieldIsStillDelimited() {
        assertArrayEquals(
                HexFormat.of().parseHex("00000000" + "00000001" + "61"),
                CanonicalEncoding.encode("", "a"));
    }

    @Test
    void rejectsUnpairedSurrogatesThatUtf8WouldSilentlyReplace() {
        // getBytes(UTF_8) would turn both of these into "?", making two distinct IDs collide.
        assertFalse(CanonicalEncoding.isWellFormed("\uD800"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalEncoding.encode("\uD800"));
        assertTrue(CanonicalEncoding.isWellFormed("🚚")); // a valid surrogate pair
    }

    @Test
    void rejectsNullField() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalEncoding.encode("a", null));
    }
}
