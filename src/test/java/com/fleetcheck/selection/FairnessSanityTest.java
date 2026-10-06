package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.KEY_A;
import static com.fleetcheck.selection.TestFixtures.Q1_2026;
import static com.fleetcheck.selection.TestFixtures.fleet;
import static com.fleetcheck.selection.TestFixtures.request;
import static com.fleetcheck.selection.TestFixtures.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A sanity check, not a proof. The fairness argument rests on HMAC-SHA256 being a PRF; this only
 * catches gross bias (for example a broken comparator that always favours low IDs). The inputs are
 * fixed, so the outcome is deterministic and the test cannot flake.
 */
class FairnessSanityTest {

    private static final int RUNS = 1_000;

    @Test
    @DisplayName("Sanity check, not a proof: each of 20 vehicles is picked ~25% of the time over 1,000 fixed companies")
    void selectionFrequenciesStayNearKOverN() {
        QuarterlySelector selector = selector(KEY_A);
        List<String> ids = fleet(20);
        Map<String, Integer> timesSelected = new HashMap<>();

        for (int run = 0; run < RUNS; run++) {
            String company = String.format(Locale.ROOT, "fairness-co-%04d", run);
            selector.select(request(company, Q1_2026, "25", ids))
                    .selectedVehicleIds()
                    .forEach(id -> timesSelected.merge(id, 1, Integer::sum));
        }

        assertEquals(20, timesSelected.size(), "every vehicle was selected at least once");
        // Expected 250 per vehicle, standard deviation ~13.7; the band is about ±3.6 sigma.
        timesSelected.forEach((id, count) -> {
            double frequency = (double) count / RUNS;
            assertTrue(frequency >= 0.20 && frequency <= 0.30, id + " selected " + count + " times");
        });
    }
}
