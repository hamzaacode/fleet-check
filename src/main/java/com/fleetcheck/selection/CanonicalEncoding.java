package com.fleetcheck.selection;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Unambiguous byte encoding for hash inputs.
 *
 * <p>Each field is written as a 4-byte big-endian length followed by its UTF-8 bytes. Without
 * the length prefix, {@code ("ab","c")} and {@code ("a","bc")} would hash identically, and two
 * different vehicles could share a score.
 */
final class CanonicalEncoding {

    private CanonicalEncoding() {
    }

    static byte[] encode(String... fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String field : fields) {
            out.writeBytes(encodeField(field));
        }
        return out.toByteArray();
    }

    static byte[] encodeField(String field) {
        byte[] utf8 = strictUtf8(field);
        return ByteBuffer.allocate(Integer.BYTES + utf8.length)
                .putInt(utf8.length)
                .put(utf8)
                .array();
    }

    /**
     * True when the string is valid Unicode (no unpaired surrogates). {@link String#getBytes}
     * silently replaces unpaired surrogates with '?', which would make distinct strings encode
     * identically.
     */
    static boolean isWellFormed(String text) {
        return UTF_8.newEncoder().canEncode(text);
    }

    private static byte[] strictUtf8(String field) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (!isWellFormed(field)) {
            throw new IllegalArgumentException("field is not well-formed Unicode");
        }
        return field.getBytes(UTF_8);
    }
}
