package com.fleetcheck.selection;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How many vehicles a percentage of the fleet means. */
final class SelectionCount {

    private SelectionCount() {
    }

    /**
     * {@code K = ceil(N × p / 100)}, in exact decimal arithmetic.
     *
     * <p>Ceiling, not rounding: any positive percentage of a non-empty fleet selects at least one
     * vehicle, so a surprise inspection is never silently empty (25% of 3 is 1, not 0). Exact
     * {@link BigDecimal} arithmetic matters because ceiling amplifies tiny errors: in doubles,
     * {@code 1000 * 16.1 / 100 = 161.00000000000003}, which would select 162 instead of 161.
     */
    static int of(int eligibleCount, BigDecimal percentage) {
        return BigDecimal.valueOf(eligibleCount)
                .multiply(percentage)
                .divide(SelectionRequest.HUNDRED, 0, RoundingMode.CEILING)
                .intValueExact();
    }
}
