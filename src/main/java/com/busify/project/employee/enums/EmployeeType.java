package com.busify.project.employee.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EmployeeType {
    DRIVER,
    STAFF;
    
    @JsonValue
    public String toValue() {
        return this.name();
    }
    
    @JsonCreator
    public static EmployeeType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            return EmployeeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid employee type: " + value + ". Valid values are: DRIVER, STAFF"
            );
        }
    }
}
