package com.interview.poc.util;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CertUtil {

    public static PrivateKey decodeBase64PrivateKey(String base64Key, String algorithm) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid base64 private key: " + e.getMessage(), e);
        }
    }
}
