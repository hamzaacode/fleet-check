package com.fleetcheck.selection;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 over every input that determines a selection, except the secret key material.
 *
 * <p>Two requests with the same fingerprint (and the same key material behind the keyId) produce
 * the same selection. An auditor recomputes it to prove they hold identical inputs; the service
 * compares it to detect a conflicting re-run.
 */
final class InputFingerprint {

    private InputFingerprint() {
    }

    static String of(String algorithmVersion, String keyId, SelectionRequest request) {
        MessageDigest sha256 = newSha256();
        sha256.update(CanonicalEncoding.encode(
                algorithmVersion,
                keyId,
                request.companyId(),
                request.quarter().canonical(),
                canonicalPercentage(request.percentage())));
        // Sorted so the fingerprint describes the set of vehicles, not the order the caller sent.
        // Each ID is length-prefixed, so the ID list cannot bleed into the header fields.
        request.vehicleIds().stream()
                .sorted()
                .forEach(id -> sha256.update(CanonicalEncoding.encodeField(id)));
        return HexFormat.of().formatHex(sha256.digest());
    }

    /** {@code 25}, {@code 25.0} and {@code 25.00} are the same percentage, so they hash the same. */
    static String canonicalPercentage(BigDecimal percentage) {
        return percentage.stripTrailingZeros().toPlainString();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }
}
