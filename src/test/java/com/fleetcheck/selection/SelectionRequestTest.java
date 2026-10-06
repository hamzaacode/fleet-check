package com.fleetcheck.selection;

import static com.fleetcheck.selection.TestFixtures.COMPANY;
import static com.fleetcheck.selection.TestFixtures.Q1_2026;
import static com.fleetcheck.selection.TestFixtures.request;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SelectionRequestTest {

    private static final List<String> IDS = List.of("VH-1", "VH-2", "VH-3");

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "100.01", "101", "1E+3"})
    void rejectsOutOfRangePercentages(String percentage) {
        assertThrows(IllegalArgumentException.class, () -> request(Q1_2026, percentage, IDS));
    }

    @Test
    void rejectsNullPercentage() {
        assertThrows(IllegalArgumentException.class,
                () -> new SelectionRequest(COMPANY, Q1_2026, null, IDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "100", "100.000", "25"})
    void acceptsBoundaryPercentages(String percentage) {
        assertDoesNotThrow(() -> request(Q1_2026, percentage, IDS));
    }

    @Test
    void rejectsDuplicateIdNamingIt() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> request(Q1_2026, "25", List.of("VH-1", "VH-2", "VH-1")));

        assertTrue(error.getMessage().contains("'VH-1'"), error.getMessage());
    }

    @Test
    void rejectsNullIdNamingItsPosition() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> request(Q1_2026, "25", Arrays.asList("VH-1", null)));

        assertTrue(error.getMessage().contains("index 1"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n "})
    void rejectsBlankIds(String blank) {
        assertThrows(IllegalArgumentException.class,
                () -> request(Q1_2026, "25", List.of("VH-1", blank)));
    }

    @Test
    void rejectsMalformedUnicodeId() {
        assertThrows(IllegalArgumentException.class,
                () -> request(Q1_2026, "25", List.of("VH-\uD800")));
    }

    @Test
    void treatsIdsAsExactOpaqueStrings() {
        // No trimming or case folding, so these are three distinct vehicles, not duplicates.
        var request = request(Q1_2026, "25", List.of("V1", "v1", " V1"));

        assertEquals(List.of("V1", "v1", " V1"), request.vehicleIds());
    }

    @Test
    void rejectsMissingCompanyQuarterOrList() {
        assertThrows(IllegalArgumentException.class, () -> request(" ", Q1_2026, "25", IDS));
        assertThrows(IllegalArgumentException.class, () -> request(null, Q1_2026, "25", IDS));
        assertThrows(IllegalArgumentException.class, () -> request(COMPANY, null, "25", IDS));
        assertThrows(IllegalArgumentException.class,
                () -> new SelectionRequest(COMPANY, Q1_2026, BigDecimal.TEN, null));
    }

    @Test
    void copiesTheCallersListAndExposesAnImmutableOne() {
        List<String> callers = new ArrayList<>(IDS);
        var request = request(Q1_2026, "25", callers);

        callers.add("VH-4");

        assertEquals(IDS, request.vehicleIds());
        assertThrows(UnsupportedOperationException.class, () -> request.vehicleIds().add("VH-5"));
    }
}
