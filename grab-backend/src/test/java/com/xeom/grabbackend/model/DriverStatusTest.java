package com.xeom.grabbackend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverStatusTest {
    @Test
    void parsesKnownStatuses() {
        assertEquals(DriverStatus.OFFLINE, DriverStatus.fromValue("OFFLINE"));
        assertEquals(DriverStatus.ONLINE, DriverStatus.fromValue("ONLINE"));
        assertEquals(DriverStatus.AVAILABLE, DriverStatus.fromValue("AVAILABLE"));
        assertEquals(DriverStatus.RECEIVER_REQUEST, DriverStatus.fromValue("RECEIVER_REQUEST"));
        assertEquals(DriverStatus.ACCEPT, DriverStatus.fromValue("ACCEPT"));
    }

    @Test
    void rejectsUnknownStatuses() {
        assertThrows(IllegalArgumentException.class, () -> DriverStatus.fromValue("busy"));
    }
}
