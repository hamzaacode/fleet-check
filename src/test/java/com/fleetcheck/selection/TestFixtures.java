package com.fleetcheck.selection;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Fixed, non-secret test inputs. No real key is ever committed. */
final class TestFixtures {

    /** Bytes 0x00..0x1F. Obviously not a real key. */
    static final AuditKey KEY_A = new AuditKey("test-key-a", sequentialBytes(0x00));
    /** Bytes 0x20..0x3F. */
    static final AuditKey KEY_B = new AuditKey("test-key-b", sequentialBytes(0x20));

    static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC);

    static final String COMPANY = "acme-logistics";
    static final Quarter Q1_2026 = Quarter.of(2026, 1);
    static final Quarter Q2_2026 = Quarter.of(2026, 2);

    private TestFixtures() {
    }

    static byte[] sequentialBytes(int first) {
        byte[] bytes = new byte[AuditKey.MIN_MATERIAL_BYTES];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (first + i);
        }
        return bytes;
    }

    static QuarterlySelector selector(AuditKey key) {
        return new QuarterlySelector(key, FIXED_CLOCK);
    }

    /** "VH-000001" .. "VH-00000n". */
    static List<String> fleet(int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> String.format(Locale.ROOT, "VH-%06d", i))
                .toList();
    }

    static SelectionRequest request(Quarter quarter, String percentage, List<String> vehicleIds) {
        return request(COMPANY, quarter, percentage, vehicleIds);
    }

    static SelectionRequest request(
            String companyId, Quarter quarter, String percentage, List<String> vehicleIds) {
        return new SelectionRequest(companyId, quarter, new BigDecimal(percentage), vehicleIds);
    }

    /** A deterministic reordering, so order-independence tests need no randomness. */
    static List<String> reversedAndRotated(List<String> ids) {
        List<String> reordered = new ArrayList<>(ids);
        Collections.reverse(reordered);
        Collections.rotate(reordered, ids.size() / 3);
        return reordered;
    }
}
