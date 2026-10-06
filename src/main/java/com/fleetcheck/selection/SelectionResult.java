package com.fleetcheck.selection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, auditable record of one quarterly selection.
 *
 * <p>Holds the {@code keyId}, never the key. {@code selectedAt} is metadata only: it is not part of
 * the fingerprint, so re-running the algorithm later still reproduces the same fingerprint.
 *
 * @param selectedVehicleIds sorted by vehicleId for stable output; use
 *     {@link QuarterlySelector#rank} for the rank order
 */
public record SelectionResult(
        String companyId,
        Quarter quarter,
        BigDecimal percentage,
        int eligibleCount,
        int selectedCount,
        List<String> selectedVehicleIds,
        String algorithmVersion,
        String keyId,
        String inputFingerprint,
        Instant selectedAt) {

    public SelectionResult {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(quarter, "quarter");
        Objects.requireNonNull(percentage, "percentage");
        Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(inputFingerprint, "inputFingerprint");
        Objects.requireNonNull(selectedAt, "selectedAt");
        selectedVehicleIds = List.copyOf(selectedVehicleIds);
        if (selectedCount != selectedVehicleIds.size() || selectedCount > eligibleCount) {
            throw new IllegalArgumentException("inconsistent counts: selected " + selectedCount
                    + " of " + eligibleCount + " with " + selectedVehicleIds.size() + " IDs");
        }
    }
}
