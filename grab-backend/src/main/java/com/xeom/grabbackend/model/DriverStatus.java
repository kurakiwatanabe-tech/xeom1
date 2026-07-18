package com.xeom.grabbackend.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

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
        String raw = value.trim();
        String normalized = raw.toUpperCase(Locale.ROOT);

        // direct enum name match
        for (DriverStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }

        // friendly / localized aliases
        String lower = raw.toLowerCase(Locale.ROOT).strip();
        return switch (lower) {
            case "nhận chuyến", "nhan chuyen", "nhan-chuyen", "nhận-chuyến" -> ACCEPT;
            case "nhận", "nhan" -> ACCEPT;
            case "available", "có sẵn", "co san", "san" -> AVAILABLE;
            case "online", "onlin" -> ONLINE;
            case "offline", "off" -> OFFLINE;
            default -> throw new IllegalArgumentException("Unsupported driver status: " + value);
        };
    }
    public static boolean isValid(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(DriverStatus.values())
                .anyMatch(e -> e.name().equals(normalized))
                || Set.of("REQUESTED", "ONGOING", "COMPLETED", "CANCELLED").contains(normalized);
    }
}
