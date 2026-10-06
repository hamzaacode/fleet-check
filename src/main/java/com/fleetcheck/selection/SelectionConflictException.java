package com.fleetcheck.selection;

/**
 * A selection already exists for this company and quarter, but from different inputs (percentage,
 * vehicles, key or algorithm version). The existing selection stands; it is never recomputed or
 * replaced, because inspections may already be scheduled against it.
 */
public final class SelectionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SelectionResult existing;
    private final String requestedFingerprint;

    public SelectionConflictException(SelectionResult existing, String requestedFingerprint) {
        super("selection for company '" + existing.companyId() + "' in " + existing.quarter()
                + " already exists with input fingerprint " + existing.inputFingerprint()
                + "; this request has fingerprint " + requestedFingerprint);
        this.existing = existing;
        this.requestedFingerprint = requestedFingerprint;
    }

    public SelectionResult existing() {
        return existing;
    }

    public String requestedFingerprint() {
        return requestedFingerprint;
    }
}
