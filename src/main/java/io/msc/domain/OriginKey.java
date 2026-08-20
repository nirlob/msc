package io.msc.domain;

/**
 * Registered public key for an origin (`MSC-Origin` header value, typically
 * `model_id:user_id`). Revocable.
 *
 * Note: the cryptographic identity is the origin string itself; the specific
 * model+provider combination is recorded in {@code Contribution.context.model}
 * as a non-authenticated informational field. Rotating models or providers
 * therefore does not require re-issuing keys.
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