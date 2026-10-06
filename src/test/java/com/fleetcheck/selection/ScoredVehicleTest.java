package com.fleetcheck.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoredVehicleTest {

    private static byte[] score(int firstByte) {
        byte[] score = new byte[32];
        score[0] = (byte) firstByte;
        return score;
    }

    @Test
    void equalScoresAreOrderedByVehicleId() {
        List<ScoredVehicle> vehicles = new ArrayList<>(List.of(
                new ScoredVehicle("VH-C", score(0x10)),
                new ScoredVehicle("VH-A", score(0x10)),
                new ScoredVehicle("VH-B", score(0x10))));

        vehicles.sort(ScoredVehicle.RANK_ORDER);

        assertEquals(List.of("VH-A", "VH-B", "VH-C"),
                vehicles.stream().map(ScoredVehicle::vehicleId).toList());
    }

    @Test
    void scoreDecidesBeforeVehicleId() {
        var lowScoreLateId = new ScoredVehicle("VH-Z", score(0x01));
        var highScoreEarlyId = new ScoredVehicle("VH-A", score(0x02));

        assertTrue(ScoredVehicle.RANK_ORDER.compare(lowScoreLateId, highScoreEarlyId) < 0);
    }

    @Test
    void scoresCompareAsUnsignedBytes() {
        // 0x80 is -128 as a signed Java byte; a signed compare would rank it before 0x7F.
        var x7f = new ScoredVehicle("VH-A", score(0x7F));
        var x80 = new ScoredVehicle("VH-B", score(0x80));
        var xff = new ScoredVehicle("VH-C", score(0xFF));
        List<ScoredVehicle> vehicles = new ArrayList<>(List.of(xff, x80, x7f));

        vehicles.sort(ScoredVehicle.RANK_ORDER);

        assertEquals(List.of(x7f, x80, xff), vehicles);
    }

    @Test
    void comparatorIsAntisymmetricAndConsistentForEqualElements() {
        var a = new ScoredVehicle("VH-A", score(0x10));
        var b = new ScoredVehicle("VH-B", score(0x10));
        var aCopy = new ScoredVehicle("VH-A", score(0x10));

        assertEquals(-Integer.signum(ScoredVehicle.RANK_ORDER.compare(b, a)),
                Integer.signum(ScoredVehicle.RANK_ORDER.compare(a, b)));
        assertEquals(0, ScoredVehicle.RANK_ORDER.compare(a, aCopy));
    }
}
