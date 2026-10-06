package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.sequentialBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AuditKeyTest {

    private static final byte[] MATERIAL = sequentialBytes(0x40);

    @Test
    void toStringShowsKeyIdButNeverMaterial() {
        String printed = new AuditKey("key-2026", MATERIAL).toString();

        assertTrue(printed.contains("key-2026"));
        assertFalse(printed.contains(HexFormat.of().formatHex(MATERIAL)));
        assertFalse(printed.toLowerCase().contains(HexFormat.of().formatHex(MATERIAL, 0, 4)));
        assertFalse(printed.contains(Base64.getEncoder().encodeToString(MATERIAL)));
        assertFalse(printed.contains(Arrays.toString(MATERIAL)));
        assertFalse(printed.contains("64, 65")); // first bytes as decimals
    }

    @Test
    void copiesMaterialOnTheWayIn() {
        byte[] buffer = MATERIAL.clone();
        AuditKey key = new AuditKey("k", buffer);

        Arrays.fill(buffer, (byte) 0);

        assertArrayEquals(MATERIAL, key.material());
    }

    @Test
    void copiesMaterialOnTheWayOut() {
        AuditKey key = new AuditKey("k", MATERIAL);

        key.material()[0] ^= 1;

        assertArrayEquals(MATERIAL, key.material());
    }

    @Test
    void equalityComparesMaterialContentNotArrayIdentity() {
        assertEquals(new AuditKey("k", MATERIAL.clone()), new AuditKey("k", MATERIAL.clone()));
        assertNotEquals(new AuditKey("k", MATERIAL), new AuditKey("k", sequentialBytes(0x41)));
    }

    @Test
    void rejectsShortOrMissingMaterialAndBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new AuditKey("k", new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> new AuditKey("k", null));
        assertThrows(IllegalArgumentException.class, () -> new AuditKey(" ", MATERIAL));
        assertThrows(IllegalArgumentException.class, () -> new AuditKey(null, MATERIAL));
    }
}
