package io.msc.security;

import java.util.Base64;

/** Minimal base64url decoder. Throws IllegalArgumentException on bad input. */
public final class Base64Url {

    private Base64Url() {}

    public static byte[] decode(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null input");
        }
        // Pad to multiple of 4
        int padding = (4 - s.length() % 4) % 4;
        StringBuilder padded = new StringBuilder(s.length() + padding);
        padded.append(s);
        for (int i = 0; i < padding; i++) {
            padded.append('=');
        }
        return Base64.getUrlDecoder().decode(padded.toString());
    }

    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}