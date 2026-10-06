package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.COMPANY;
import static com.fleetcheck.selection.TestFixtures.KEY_A;
import static com.fleetcheck.selection.TestFixtures.Q1_2026;
import static com.fleetcheck.selection.TestFixtures.Q2_2026;
import static com.fleetcheck.selection.TestFixtures.fleet;
import static com.fleetcheck.selection.TestFixtures.request;
import static com.fleetcheck.selection.TestFixtures.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemorySelectionRepositoryTest {

    private final InMemorySelectionRepository repository = new InMemorySelectionRepository();

    @Test
    void saveIfAbsentKeepsTheFirstResultForACompanyAndQuarter() {
        var first = selector(KEY_A).select(request(Q1_2026, "25", fleet(20)));
        var second = selector(KEY_A).select(request(Q1_2026, "50", fleet(20)));

        assertSame(first, repository.saveIfAbsent(first));
        assertSame(first, repository.saveIfAbsent(second));
        assertSame(first, repository.findByCompanyAndQuarter(COMPANY, Q1_2026).orElseThrow());
        assertEquals(1, repository.size());
    }

    @Test
    void companiesAndQuartersAreSeparateKeys() {
        repository.saveIfAbsent(selector(KEY_A).select(request(Q1_2026, "25", fleet(20))));
        repository.saveIfAbsent(selector(KEY_A).select(request(Q2_2026, "25", fleet(20))));
        repository.saveIfAbsent(selector(KEY_A).select(request("other-co", Q1_2026, "25", fleet(20))));

        assertEquals(3, repository.size());
        assertTrue(repository.findByCompanyAndQuarter("unknown-co", Q1_2026).isEmpty());
    }
}
