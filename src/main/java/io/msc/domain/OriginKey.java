package io.msc.domain;

/**
 * Registered public key for an origin (model_id:user_id). Revocable.
 */
public final class OriginKey {

    private final String origin;
    private final String publicKeyWire; // "ed25519:<base64url>"
    private final boolean revoked;

    public OriginKey(String origin, String publicKeyWire, boolean revoked) {
        this.origin = origin;
        this.publicKeyWire = publicKeyWire;
        this.revoked = revoked;
    }

    public String origin() { return origin; }
    public String publicKeyWire() { return publicKeyWire; }
    public boolean revoked() { return revoked; }
}