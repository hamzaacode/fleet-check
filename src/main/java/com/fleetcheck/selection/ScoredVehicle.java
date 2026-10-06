package com.fleetcheck.selection;

import java.util.Arrays;
import java.util.Comparator;

/** A vehicle and its 32-byte HMAC score. Internal to ranking; never exposed or stored. */
record ScoredVehicle(String vehicleId, byte[] score) {

    /**
     * Lowest score first, compared as unsigned bytes. Java bytes are signed, so a naive compare
     * would rank 0x80..0xFF before 0x00..0x7F, which is still deterministic but disagrees with
     * every other language's byte order and with the documented algorithm.
     *
     * <p>Ties on score are broken by vehicleId. IDs are unique, so this is a strict total order:
     * the sorted result depends only on the set of vehicles, never on input order.
     */
    static final Comparator<ScoredVehicle> RANK_ORDER = Comparator
            .comparing(ScoredVehicle::score, Arrays::compareUnsigned)
            .thenComparing(ScoredVehicle::vehicleId);
}
