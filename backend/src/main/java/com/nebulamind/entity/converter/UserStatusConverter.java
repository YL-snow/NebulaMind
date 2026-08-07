package com.nebulamind.entity.converter;

import com.nebulamind.entity.User;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<User.UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(User.UserStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name().toLowerCase();
    }

    @Override
    public User.UserStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return User.UserStatus.valueOf(dbData.toUpperCase());
    }
}