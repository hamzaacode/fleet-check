package com.fleetcheck.selection;

import java.security.MessageDigest;

/**
 * Secret HMAC key used to rank vehicles, identified by a non-secret {@code keyId}.
 *
 * <p>Only the {@code keyId} is ever stored or printed. An auditor retrieves the material from
 * escrow by that id to reproduce a selection.
 */
public record AuditKey(String keyId, byte[] material) {

    /** RFC 2104 recommends a key at least as long as the hash output (32 bytes for SHA-256). */
    static final int MIN_MATERIAL_BYTES = 32;

    public AuditKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (material == null || material.length < MIN_MATERIAL_BYTES) {
            throw new IllegalArgumentException(
                    "key material must be at least " + MIN_MATERIAL_BYTES + " bytes");
        }
        // Copy in so a caller zeroing or reusing its buffer cannot change past or future rankings.
        material = material.clone();
    }

    /** Returns a copy, so callers cannot mutate the key held by this instance. */
    @Override
    public byte[] material() {
        return material.clone();
    }

    @Override
    public boolean equals(Object other) {
        // Constant-time comparison so equality checks do not become a timing oracle on the secret.
        return other instanceof AuditKey that
                && keyId.equals(that.keyId)
                && MessageDigest.isEqual(material, that.material);
    }

    @Override
    public int hashCode() {
        // Deliberately excludes the material: a hash of the secret is still information about it.
        return keyId.hashCode();
    }

    @Override
    public String toString() {
        return "AuditKey[keyId=" + keyId + ", material=<redacted>]";
    }
}
