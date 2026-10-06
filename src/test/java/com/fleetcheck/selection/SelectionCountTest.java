package com.fleetcheck.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SelectionCountTest {

    @ParameterizedTest(name = "{1}% of {0} vehicles selects {2}")
    @CsvSource({
        // Worked examples from the README.
        "20,     25,       5",
        "3,      25,       1", // 0.75 rounds up: a positive percentage never selects nobody
        "5,      10,       1",
        "20,     0,        0",
        "20,     100,      20",
        "0,      25,       0", // empty fleet
        // Boundaries and precision.
        "1,      0.0001,   1",
        "0,      0,        0",
        "0,      100,      0",
        "3,      66.67,    3", // 2.0001 rounds up
        "3,      66.66,    2", // 1.9998 rounds up to 2
        "20,     25.00,    5", // scale does not matter
        "200000, 10,       20000",
        "200000, 0.0005,   1",
        // In double arithmetic 1000 * 16.1 / 100 = 161.00000000000003, whose ceiling is 162.
        "1000,   16.1,     161",
        "1000,   64.4,     644",
    })
    void followsCeilingOfExactProduct(int eligible, String percentage, int expected) {
        assertEquals(expected, SelectionCount.of(eligible, new BigDecimal(percentage)));
    }
}
