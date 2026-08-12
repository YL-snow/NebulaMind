package com.nebulamind.entity.converter;

import com.nebulamind.entity.AuditLog;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AuditLogResourceTypeConverter implements AttributeConverter<AuditLog.ResourceType, String> {

    @Override
    public String convertToDatabaseColumn(AuditLog.ResourceType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public AuditLog.ResourceType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return AuditLog.ResourceType.valueOf(dbData.toUpperCase());
    }
}
