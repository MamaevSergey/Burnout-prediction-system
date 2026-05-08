package com.lamart.burnout.burnoutpredictionsystem.config;

import com.lamart.burnout.burnoutpredictionsystem.util.SecurityUtils;
import jakarta.persistence.AttributeConverter;
import org.springframework.beans.factory.annotation.Value;

public class EncryptionConverter implements AttributeConverter<String, String> {
    @Value("${app.security.master-key}")
    private String secretKey;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return SecurityUtils.encrypt(attribute, secretKey);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return SecurityUtils.decrypt(dbData, secretKey);
    }
}
