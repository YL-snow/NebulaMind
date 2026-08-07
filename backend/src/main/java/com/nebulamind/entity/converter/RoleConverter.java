package com.nebulamind.entity.converter;

import com.nebulamind.entity.User;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<User.Role, String> {

    @Override
    public String convertToDatabaseColumn(User.Role attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public User.Role convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return User.Role.valueOf(dbData.toUpperCase());
    }
}