package com.lamart.burnout.burnoutpredictionsystem.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Anonymizer {
    public static UUID hashToUuid(String rawId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawId.getBytes(StandardCharsets.UTF_8));

            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (hash[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (hash[i] & 0xff);
            return new UUID(msb, lsb);
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Алгоритм SHA-256 не найден", exception);
        }
    }
}
