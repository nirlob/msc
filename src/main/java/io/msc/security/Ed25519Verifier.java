package io.msc.security;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

/**
 * Verifies Ed25519 signatures over raw request bodies, matching the format
 * declared in msc-rfc-protocol.md §5.3:
 *
 *   - The signature header is `ed25519:<base64url(raw sig)>`.
 *   - The public key (stored) is `ed25519:<base64url(raw pub)>`.
 *   - The signature is computed over the UTF-8 bytes of the request body.
 */
public final class Ed25519Verifier {

    private static final String SIG_PREFIX = "ed25519:";
    private static final int RAW_PUB_LEN = 32;

    private Ed25519Verifier() {}

    /**
     * Verify a signature against a body, given wire-format strings.
     *
     * @param publicKeyWire "ed25519:&lt;base64url raw pub&gt;"
     * @param body           UTF-8 bytes that were signed
     * @param signatureWire  "ed25519:&lt;base64url raw sig&gt;"
     * @return true if the signature is valid for the body
     */
    public static boolean verify(String publicKeyWire, byte[] body, String signatureWire) {
        if (publicKeyWire == null || signatureWire == null || body == null) return false;
        if (!publicKeyWire.startsWith(SIG_PREFIX) || !signatureWire.startsWith(SIG_PREFIX)) {
            return false;
        }
        try {
            PublicKey publicKey = parsePublicKey(
                publicKeyWire.substring(SIG_PREFIX.length()));
            byte[] signature = Base64Url.decode(
                signatureWire.substring(SIG_PREFIX.length()));
            return verify(publicKey, body, signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** Verify a signature against a body, with already-parsed key and signature bytes. */
    public static boolean verify(PublicKey publicKey, byte[] body, byte[] signature) {
        try {
            EdDSAEngine engine = new EdDSAEngine();
            engine.initVerify(publicKey);
            engine.update(body);
            return engine.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse an `ed25519:&lt;base64url raw&gt;` wire format string into a PublicKey.
     * Throws InvalidKeySpecException if the payload is not a valid 32-byte raw Ed25519 key.
     */
    public static PublicKey parsePublicKey(String b64UrlRaw) throws InvalidKeySpecException {
        byte[] raw = Base64Url.decode(b64UrlRaw);
        if (raw.length != RAW_PUB_LEN) {
            throw new InvalidKeySpecException(
                "Ed25519 public key must be " + RAW_PUB_LEN + " bytes, got " + raw.length);
        }
        EdDSAPublicKeySpec pubSpec =
            new EdDSAPublicKeySpec(raw, EdDSANamedCurveTable.ED_25519_CURVE_SPEC);
        return new EdDSAPublicKey(pubSpec);
    }
}