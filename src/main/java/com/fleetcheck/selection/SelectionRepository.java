package com.fleetcheck.selection;

import java.util.Optional;

/** Stores at most one selection per (companyId, quarter). */
public interface SelectionRepository {

    Optional<SelectionResult> findByCompanyAndQuarter(String companyId, Quarter quarter);

    /**
     * Atomically stores {@code result} unless a selection already exists for its company and
     * quarter, and returns whichever selection is stored afterwards. Never replaces an existing
     * one. In PostgreSQL: {@code INSERT ... ON CONFLICT (company_id, quarter) DO NOTHING} then
     * {@code SELECT}, in one transaction.
     */
    SelectionResult saveIfAbsent(SelectionResult result);
}
