package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.KEY_A;
import static com.fleetcheck.selection.TestFixtures.KEY_B;
import static com.fleetcheck.selection.TestFixtures.Q1_2026;
import static com.fleetcheck.selection.TestFixtures.Q2_2026;
import static com.fleetcheck.selection.TestFixtures.fleet;
import static com.fleetcheck.selection.TestFixtures.request;
import static com.fleetcheck.selection.TestFixtures.reversedAndRotated;
import static com.fleetcheck.selection.TestFixtures.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuarterlySelectorTest {

    private final QuarterlySelector selector = selector(KEY_A);

    @Test
    void sameInputsGiveAnIdenticalResult() {
        var request = request(Q1_2026, "25", fleet(50));

        assertEquals(selector.select(request), selector.select(request));
        assertEquals(selector.rank(request), selector.rank(request));
    }

    @Test
    void inputOrderDoesNotAffectSelectionRankingOrFingerprint() {
        List<String> ids = fleet(50);
        List<String> reordered = reversedAndRotated(ids);
        assertNotEquals(ids, reordered);

        var original = selector.select(request(Q1_2026, "25", ids));
        var shuffled = selector.select(request(Q1_2026, "25", reordered));

        assertEquals(original, shuffled);
        assertEquals(selector.rank(request(Q1_2026, "25", ids)),
                selector.rank(request(Q1_2026, "25", reordered)));
    }

    @Test
    void percentageScaleDoesNotAffectFingerprint() {
        assertEquals(selector.fingerprint(request(Q1_2026, "25", fleet(20))),
                selector.fingerprint(request(Q1_2026, "25.00", fleet(20))));
    }

    @Test
    void selectionIsTheFirstKOfTheRankingSortedById() {
        var request = request(Q1_2026, "25", fleet(20));

        var result = selector.select(request);

        List<String> topFive = selector.rank(request).subList(0, 5);
        assertEquals(topFive.stream().sorted().toList(), result.selectedVehicleIds());
    }

    @Test
    void differentQuartersRankTheSameFleetDifferently() {
        assertNotEquals(
                selector.rank(request(Q1_2026, "25", fleet(20))),
                selector.rank(request(Q2_2026, "25", fleet(20))));
    }

    @Test
    void differentCompaniesWithTheSameVehicleIdsRankDifferently() {
        assertNotEquals(
                selector.rank(request("company-a", Q1_2026, "25", fleet(20))),
                selector.rank(request("company-b", Q1_2026, "25", fleet(20))));
    }

    @Test
    void zeroPercentSelectsNoVehicles() {
        var result = selector.select(request(Q1_2026, "0", fleet(20)));

        assertEquals(0, result.selectedCount());
        assertEquals(List.of(), result.selectedVehicleIds());
        assertEquals(20, result.eligibleCount());
    }

    @Test
    void hundredPercentSelectsEveryVehicle() {
        var result = selector.select(request(Q1_2026, "100", reversedAndRotated(fleet(20))));

        assertEquals(fleet(20), result.selectedVehicleIds());
    }

    @Test
    void emptyFleetGivesAnEmptyResultNotAnError() {
        var result = selector.select(request(Q1_2026, "25", List.of()));

        assertEquals(0, result.eligibleCount());
        assertEquals(0, result.selectedCount());
        assertEquals(List.of(), result.selectedVehicleIds());
        assertEquals(64, result.inputFingerprint().length());
    }

    @Test
    void twentyVehicleFleetSelectsFiveDistinctInputVehiclesSortedById() {
        List<String> ids = fleet(20);

        var result = selector.select(request(Q1_2026, "25", ids));

        assertEquals(20, result.eligibleCount());
        assertEquals(5, result.selectedCount());
        assertEquals(5, Set.copyOf(result.selectedVehicleIds()).size());
        assertTrue(ids.containsAll(result.selectedVehicleIds()));
        assertEquals(result.selectedVehicleIds().stream().sorted().toList(),
                result.selectedVehicleIds());
    }

    @Test
    void twoHundredThousandVehicleFleetIsCorrectAndDeterministic() {
        List<String> ids = fleet(200_000);
        var request = request(Q1_2026, "10", ids);

        var first = selector.select(request);
        var second = selector.select(request);

        assertEquals(200_000, first.eligibleCount());
        assertEquals(20_000, first.selectedCount());
        Set<String> selected = new HashSet<>(first.selectedVehicleIds());
        assertEquals(20_000, selected.size(), "no duplicates");
        assertTrue(new HashSet<>(ids).containsAll(selected), "every selected ID came from the input");
        assertEquals(first, second);
    }

    @Test
    void resultRecordsAlgorithmVersionAndKeyIdButNotTheKey() {
        var result = selector.select(request(Q1_2026, "25", fleet(20)));

        assertEquals("HMAC-SHA256-RANK-v1", result.algorithmVersion());
        assertEquals("test-key-a", result.keyId());
        assertEquals(new BigDecimal("25"), result.percentage());
        assertEquals(TestFixtures.FIXED_CLOCK.instant(), result.selectedAt());
    }

    @Test
    void resultListIsImmutable() {
        var result = selector.select(request(Q1_2026, "25", fleet(20)));

        assertThrows(UnsupportedOperationException.class,
                () -> result.selectedVehicleIds().add("VH-999999"));
        assertThrows(UnsupportedOperationException.class,
                () -> selector.rank(request(Q1_2026, "25", fleet(20))).clear());
    }

    @Test
    void sameKeyGivesTheSameSelectionAndDifferentKeysGiveDifferentOnes() {
        var request = request(Q1_2026, "10", fleet(1_000));

        var withKeyA = selector(KEY_A).select(request);
        var withKeyAAgain = selector(new AuditKey("test-key-a", KEY_A.material())).select(request);
        var withKeyB = selector(KEY_B).select(request);

        assertEquals(withKeyA, withKeyAAgain);
        assertNotEquals(withKeyA.selectedVehicleIds(), withKeyB.selectedVehicleIds());
    }

    @Test
    void differentKeyIdsGiveDifferentFingerprints() {
        var request = request(Q1_2026, "25", fleet(20));

        assertNotEquals(selector(KEY_A).fingerprint(request), selector(KEY_B).fingerprint(request));
    }
}
