package com.fleetcheck.selection;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe in-memory store; stands in for a table with UNIQUE(company_id, quarter). */
public final class InMemorySelectionRepository implements SelectionRepository {

    private record Key(String companyId, Quarter quarter) {
    }

    private final ConcurrentMap<Key, SelectionResult> selections = new ConcurrentHashMap<>();

    @Override
    public Optional<SelectionResult> findByCompanyAndQuarter(String companyId, Quarter quarter) {
        return Optional.ofNullable(selections.get(new Key(companyId, quarter)));
    }

    @Override
    public SelectionResult saveIfAbsent(SelectionResult result) {
        // putIfAbsent is atomic, so concurrent first runs agree on a single winner.
        SelectionResult existing =
                selections.putIfAbsent(new Key(result.companyId(), result.quarter()), result);
        return existing != null ? existing : result;
    }

    public int size() {
        return selections.size();
    }
}
