package com.fleetcheck.selection;

import java.util.Objects;
import java.util.Optional;

/**
 * Guarantees one selection per (companyId, quarter).
 *
 * <p>Determinism alone is not idempotency: the selector would return a different selection if the
 * vehicle list or percentage changed mid-quarter. This service makes the first stored selection
 * final. A repeat with identical inputs returns it; a repeat with different inputs is a conflict.
 */
public final class QuarterlySelectionService {

    private final QuarterlySelector selector;
    private final SelectionRepository repository;

    public QuarterlySelectionService(QuarterlySelector selector, SelectionRepository repository) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Returns the stored selection for the request's company and quarter, computing and storing
     * it first if none exists.
     *
     * @throws SelectionConflictException if a selection exists from different inputs
     */
    public SelectionResult select(SelectionRequest request) {
        String fingerprint = selector.fingerprint(request);
        Optional<SelectionResult> existing =
                repository.findByCompanyAndQuarter(request.companyId(), request.quarter());
        if (existing.isPresent()) {
            return requireSameInputs(existing.get(), fingerprint);
        }
        // If another caller stored first, saveIfAbsent hands back their result, not ours.
        SelectionResult stored = repository.saveIfAbsent(selector.select(request));
        return requireSameInputs(stored, fingerprint);
    }

    private static SelectionResult requireSameInputs(SelectionResult stored, String fingerprint) {
        if (!stored.inputFingerprint().equals(fingerprint)) {
            throw new SelectionConflictException(stored, fingerprint);
        }
        return stored;
    }
}
