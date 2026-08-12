package com.nebulamind.entity.converter;

import com.nebulamind.entity.AuditLog;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AuditLogActionConverter implements AttributeConverter<AuditLog.Action, String> {

    @Override
    public String convertToDatabaseColumn(AuditLog.Action attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public AuditLog.Action convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return AuditLog.Action.valueOf(dbData.toUpperCase());
    }
}
