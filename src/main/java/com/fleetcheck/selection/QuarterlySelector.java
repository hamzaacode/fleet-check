package com.fleetcheck.selection;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The pure selection algorithm: same inputs and key, same output, every time.
 *
 * <p>Each vehicle gets {@code score = HMAC-SHA256(key, encode(version, companyId, quarter, vehicleId))}.
 * Vehicles are sorted by score and the first K are selected. Assuming HMAC-SHA256 behaves as a
 * pseudo-random function, the ranking is a uniformly random permutation of the eligible set.
 * Every K-subset is equally likely, so each vehicle's chance of selection is K/N. Input order and
 * selection history are not inputs to the score.
 *
 * <p>This class knows nothing about storage. Determinism lives here; idempotency (one selection
 * per company and quarter) lives in {@link QuarterlySelectionService}.
 */
public final class QuarterlySelector {

    /** Bump when anything that changes output changes, and keep old versions runnable for audits. */
    public static final String ALGORITHM_VERSION = "HMAC-SHA256-RANK-v1";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final AuditKey key;
    private final Clock clock;

    public QuarterlySelector(AuditKey key, Clock clock) {
        this.key = Objects.requireNonNull(key, "key");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SelectionResult select(SelectionRequest request) {
        List<String> ranking = rank(request);
        int count = SelectionCount.of(ranking.size(), request.percentage());
        List<String> selected = ranking.subList(0, count).stream().sorted().toList();
        return new SelectionResult(
                request.companyId(),
                request.quarter(),
                request.percentage(),
                ranking.size(),
                count,
                selected,
                ALGORITHM_VERSION,
                key.keyId(),
                fingerprint(request),
                clock.instant());
    }

    /**
     * Every eligible vehicle in rank order; the selection is the first K. Auditors can inspect
     * it, and it is the natural source of replacements when a selected vehicle is unavailable.
     */
    public List<String> rank(SelectionRequest request) {
        return scoreAll(request).stream().map(ScoredVehicle::vehicleId).toList();
    }

    public String fingerprint(SelectionRequest request) {
        return InputFingerprint.of(ALGORITHM_VERSION, key.keyId(), request);
    }

    public String keyId() {
        return key.keyId();
    }

    private List<ScoredVehicle> scoreAll(SelectionRequest request) {
        Mac mac = newMac();
        List<ScoredVehicle> scored = new ArrayList<>(request.vehicleIds().size());
        for (String vehicleId : request.vehicleIds()) {
            scored.add(new ScoredVehicle(vehicleId, mac.doFinal(message(request, vehicleId))));
        }
        // A full sort rather than a top-K heap: O(N log N) is cheap at 200k and trivial to audit.
        scored.sort(ScoredVehicle.RANK_ORDER);
        return scored;
    }

    /**
     * Company and quarter are hashed alongside the vehicle so each (company, quarter) gets an
     * independent ranking: being picked last quarter neither helps nor hurts this quarter.
     */
    private static byte[] message(SelectionRequest request, String vehicleId) {
        return CanonicalEncoding.encode(
                ALGORITHM_VERSION, request.companyId(), request.quarter().canonical(), vehicleId);
    }

    private Mac newMac() {
        // A fresh Mac per call because Mac is not thread-safe; doFinal resets it between vehicles.
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key.material(), HMAC_SHA256));
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Message names the key by id only; the cause never contains material.
            throw new IllegalStateException("cannot initialise HMAC for key " + key.keyId(), e);
        }
    }
}
