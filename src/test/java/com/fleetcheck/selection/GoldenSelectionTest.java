package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.KEY_A;
import static com.fleetcheck.selection.TestFixtures.request;
import static com.fleetcheck.selection.TestFixtures.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Pins the exact output of HMAC-SHA256-RANK-v1 for fixed inputs.
 *
 * <p>If this test fails, the algorithm's output changed and every past selection is no longer
 * reproducible by this code. Do not update the expected values: introduce a new
 * {@code ALGORITHM_VERSION} and keep v1 runnable for audits.
 *
 * <p>The expected values were computed by an independent implementation of the README's
 * algorithm description (Python {@code hmac}/{@code hashlib}), not by this code.
 */
class GoldenSelectionTest {

    private static final List<String> GOLDEN_FLEET = List.of(
            "VH-000001", "VH-000002", "VH-000003", "VH-000004", "VH-000005",
            "VH-000006", "VH-000007", "VH-000008", "VH-000009", "VH-000010",
            "VH-000011", "VH-000012", "VH-000013", "VH-000014", "VH-000015",
            "VH-000016", "VH-000017", "VH-000018", "VH-000019", "VH-000020");

    private static final SelectionRequest GOLDEN_REQUEST =
            request("golden-co", Quarter.parse("2026-Q1"), "25", GOLDEN_FLEET);

    @Test
    void selectionIsPinned() {
        var result = selector(KEY_A).select(GOLDEN_REQUEST);

        assertEquals(
                List.of("VH-000007", "VH-000010", "VH-000012", "VH-000015", "VH-000020"),
                result.selectedVehicleIds());
        assertEquals(
                "fe4c40e9854dbe6d93fea01630e94ef46a1865376217f8a39e5a4140012c6fac",
                result.inputFingerprint());
    }

    @Test
    void rankingIsPinned() {
        assertEquals(
                List.of("VH-000012", "VH-000015", "VH-000020", "VH-000010", "VH-000007",
                        "VH-000009", "VH-000019", "VH-000004", "VH-000005", "VH-000008",
                        "VH-000013", "VH-000018", "VH-000002", "VH-000011", "VH-000017",
                        "VH-000006", "VH-000014", "VH-000003", "VH-000001", "VH-000016"),
                selector(KEY_A).rank(GOLDEN_REQUEST));
    }

    @Test
    void outputDoesNotDependOnTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
            assertEquals(
                    "fe4c40e9854dbe6d93fea01630e94ef46a1865376217f8a39e5a4140012c6fac",
                    selector(KEY_A).select(GOLDEN_REQUEST).inputFingerprint());
        } finally {
            Locale.setDefault(original);
        }
    }
}
