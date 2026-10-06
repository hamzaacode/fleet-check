package com.fleetcheck.selection;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inputs to one quarterly selection.
 *
 * <p>{@code vehicleIds} must already be the company's active vehicles; the selector does not
 * filter. IDs are opaque and matched exactly: {@code "V1"}, {@code "v1"} and {@code " V1"} are
 * three different vehicles. Trimming or case folding here would silently merge vehicles that the
 * source system treats as distinct.
 */
public record SelectionRequest(
        String companyId, Quarter quarter, BigDecimal percentage, List<String> vehicleIds) {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public SelectionRequest {
        requireText(companyId, "companyId");
        if (quarter == null) {
            throw new IllegalArgumentException("quarter must not be null");
        }
        requireValidPercentage(percentage);
        vehicleIds = validatedCopy(vehicleIds);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (!CanonicalEncoding.isWellFormed(value)) {
            throw new IllegalArgumentException(name + " is not well-formed Unicode");
        }
    }

    private static void requireValidPercentage(BigDecimal percentage) {
        if (percentage == null) {
            throw new IllegalArgumentException("percentage must not be null");
        }
        if (percentage.signum() < 0 || percentage.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "percentage must be between 0 and 100: " + percentage.toPlainString());
        }
    }

    private static List<String> validatedCopy(List<String> vehicleIds) {
        if (vehicleIds == null) {
            throw new IllegalArgumentException("vehicleIds must not be null");
        }
        Set<String> seen = new HashSet<>(vehicleIds.size() * 2);
        for (int i = 0; i < vehicleIds.size(); i++) {
            String id = vehicleIds.get(i);
            requireValidId(id, i);
            // Never de-duplicate: a repeated ID means the upstream "active vehicles" query is wrong,
            // and quietly fixing it would hide that from whoever owns the data.
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate vehicle ID '" + id + "'");
            }
        }
        return List.copyOf(vehicleIds);
    }

    private static void requireValidId(String id, int index) {
        if (id == null) {
            throw new IllegalArgumentException("vehicle ID at index " + index + " is null");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException(
                    "vehicle ID at index " + index + " is blank: '" + id + "'");
        }
        if (!CanonicalEncoding.isWellFormed(id)) {
            throw new IllegalArgumentException(
                    "vehicle ID at index " + index + " is not well-formed Unicode: '" + id + "'");
        }
    }
}
