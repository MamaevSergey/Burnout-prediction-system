package com.lamart.burnout.burnoutpredictionsystem.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Anonymizer {
    public static UUID hashToUuid(String rawId) {
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8));
    }
}
