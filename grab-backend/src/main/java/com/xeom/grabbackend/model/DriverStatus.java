package com.xeom.grabbackend.model;

import java.util.Locale;

public enum DriverStatus {
    OFFLINE,
    ONLINE,
    AVAILABLE,
    RECEIVER_REQUEST,
    ACCEPT,
    GOING_TO_PICKUP,
    WAITING_PASSENGER,
    ON_TRIP,
    COMLETING_PAYMENT;

    public static DriverStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Driver status must not be blank");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DriverStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported driver status: " + value);
    }
}
