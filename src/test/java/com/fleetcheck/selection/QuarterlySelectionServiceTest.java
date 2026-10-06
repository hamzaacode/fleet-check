package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.COMPANY;
import static com.fleetcheck.selection.TestFixtures.KEY_A;
import static com.fleetcheck.selection.TestFixtures.KEY_B;
import static com.fleetcheck.selection.TestFixtures.Q1_2026;
import static com.fleetcheck.selection.TestFixtures.Q2_2026;
import static com.fleetcheck.selection.TestFixtures.fleet;
import static com.fleetcheck.selection.TestFixtures.request;
import static com.fleetcheck.selection.TestFixtures.reversedAndRotated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class QuarterlySelectionServiceTest {

    /**
     * Each reading is one second later than the last. A service that recomputed instead of
     * returning the stored result would produce a different selectedAt, so tests would notice.
     */
    private static final class TickingClock extends Clock {
        private final AtomicLong seconds = new AtomicLong(Instant.parse("2026-01-05T09:00:00Z").getEpochSecond());

        @Override
        public Instant instant() {
            return Instant.ofEpochSecond(seconds.getAndIncrement());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    private final InMemorySelectionRepository repository = new InMemorySelectionRepository();
    private final QuarterlySelectionService service = serviceWith(KEY_A);

    private QuarterlySelectionService serviceWith(AuditKey key) {
        return new QuarterlySelectionService(new QuarterlySelector(key, new TickingClock()), repository);
    }

    private SelectionResult stored(Quarter quarter) {
        return repository.findByCompanyAndQuarter(COMPANY, quarter).orElseThrow();
    }

    @Test
    void rerunReturnsTheStoredResultAndStoresOnlyOne() {
        var request = request(Q1_2026, "25", fleet(20));

        var first = service.select(request);
        var second = service.select(request);

        assertSame(first, second);
        assertSame(first, stored(Q1_2026));
        assertEquals(1, repository.size());
    }

    @Test
    void rerunWithReorderedVehiclesOrRescaledPercentageReturnsTheStoredResult() {
        var first = service.select(request(Q1_2026, "25", fleet(20)));

        assertSame(first, service.select(request(Q1_2026, "25", reversedAndRotated(fleet(20)))));
        assertSame(first, service.select(request(Q1_2026, "25.00", fleet(20))));
        assertEquals(1, repository.size());
    }

    @Test
    void changedPercentageIsAConflictAndLeavesTheStoredResultUnchanged() {
        var first = service.select(request(Q1_2026, "25", fleet(20)));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> service.select(request(Q1_2026, "30", fleet(20))));

        assertSame(first, conflict.existing());
        assertNotEquals(first.inputFingerprint(), conflict.requestedFingerprint());
        assertSame(first, stored(Q1_2026));
        assertEquals(1, repository.size());
    }

    @Test
    void addedVehicleIsAConflictAndLeavesTheStoredResultUnchanged() {
        var first = service.select(request(Q1_2026, "25", fleet(20)));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> service.select(request(Q1_2026, "25", fleet(21))));

        assertSame(first, conflict.existing());
        assertSame(first, stored(Q1_2026));
    }

    @Test
    void removedVehicleIsAConflictAndLeavesTheStoredResultUnchanged() {
        var first = service.select(request(Q1_2026, "25", fleet(20)));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> service.select(request(Q1_2026, "25", fleet(19))));

        assertSame(first, conflict.existing());
        assertSame(first, stored(Q1_2026));
    }

    @Test
    void changedKeyIsAConflictAndLeavesTheStoredResultUnchanged() {
        var first = service.select(request(Q1_2026, "25", fleet(20)));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> serviceWith(KEY_B).select(request(Q1_2026, "25", fleet(20))));

        assertSame(first, conflict.existing());
        assertSame(first, stored(Q1_2026));
    }

    @Test
    void conflictMessageNeverContainsKeyMaterial() {
        service.select(request(Q1_2026, "25", fleet(20)));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> service.select(request(Q1_2026, "30", fleet(20))));

        assertTrue(conflict.getMessage().contains(COMPANY));
        assertFalse(conflict.getMessage().contains("000102030405"), conflict.getMessage());
    }

    @Test
    void vehicleSelectedInQ1IsStillSelectableInQ2() {
        // Fixed inputs: VH-000018 is in the top 25% for both quarters. Nothing about Q1 feeds
        // into Q2's ranking, so being selected before neither excludes nor favours a vehicle.
        var q1 = service.select(request(Q1_2026, "25", fleet(20)));
        var q2 = service.select(request(Q2_2026, "25", fleet(20)));

        assertTrue(q1.selectedVehicleIds().contains("VH-000018"), q1.selectedVehicleIds().toString());
        assertTrue(q2.selectedVehicleIds().contains("VH-000018"), q2.selectedVehicleIds().toString());
        assertNotEquals(q1.selectedVehicleIds(), q2.selectedVehicleIds());
        assertEquals(2, repository.size());
    }

    @Test
    void concurrentFirstRunsAllReturnTheSingleStoredResult() throws Exception {
        int threads = 16;
        var request = request(Q1_2026, "10", fleet(20_000));
        var startTogether = new CountDownLatch(1);
        List<Future<SelectionResult>> futures = new ArrayList<>();
        List<SelectionResult> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    return service.select(request);
                }));
            }
            startTogether.countDown();
            for (Future<SelectionResult> future : futures) {
                results.add(future.get());
            }
        }

        var winner = stored(Q1_2026);
        results.forEach(result -> assertSame(winner, result));
        assertEquals(1, repository.size());
    }

    @Test
    void losingTheRaceReturnsTheWinnersResultNotOurOwn() {
        // Deterministically reproduce the race: the read sees nothing, but by the time we save,
        // another instance has already stored a selection for the same inputs.
        var winner = new QuarterlySelector(KEY_A, new TickingClock())
                .select(request(Q1_2026, "25", fleet(20)));
        var racing = new QuarterlySelectionService(
                new QuarterlySelector(KEY_A, new TickingClock()), alwaysMissesOnRead(winner));

        assertSame(winner, racing.select(request(Q1_2026, "25", fleet(20))));
    }

    @Test
    void losingTheRaceToDifferentInputsIsAConflict() {
        var winner = new QuarterlySelector(KEY_A, new TickingClock())
                .select(request(Q1_2026, "30", fleet(20)));
        var racing = new QuarterlySelectionService(
                new QuarterlySelector(KEY_A, new TickingClock()), alwaysMissesOnRead(winner));

        var conflict = assertThrows(SelectionConflictException.class,
                () -> racing.select(request(Q1_2026, "25", fleet(20))));
        assertSame(winner, conflict.existing());
    }

    /** A repository whose read happens "before" a competitor's write lands. */
    private static SelectionRepository alwaysMissesOnRead(SelectionResult competitorsResult) {
        var backing = new InMemorySelectionRepository();
        backing.saveIfAbsent(competitorsResult);
        return new SelectionRepository() {
            @Override
            public Optional<SelectionResult> findByCompanyAndQuarter(String companyId, Quarter quarter) {
                return Optional.empty();
            }

            @Override
            public SelectionResult saveIfAbsent(SelectionResult result) {
                return backing.saveIfAbsent(result);
            }
        };
    }
}
