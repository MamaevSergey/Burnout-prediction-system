package com.lamart.burnout.burnoutpredictionsystem.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
public class Anonymizer {

    private final String salt;

    public Anonymizer(@Value("${app.security.hash-salt}") String salt) {
        this.salt = salt;
    }

    public UUID hashToUuid(String input) {
        if (input == null) return null;

        try {
            String saltedInput = input.toLowerCase().trim() + salt;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));

            long mostSigBits = 0;
            long lostSigBits = 0;
            for (int i = 0; i < 8; i++) mostSigBits = (mostSigBits << 8) | (hash[i] & 0xff);
            for (int i = 8; i < 16; i++) lostSigBits = (lostSigBits << 8) | (hash[i] & 0xff);
            return new UUID(mostSigBits, lostSigBits);
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Ошибка алгоритма хеширования", exception);
        }
    }
}
