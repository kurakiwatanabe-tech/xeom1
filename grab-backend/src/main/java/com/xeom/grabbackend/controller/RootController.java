package com.xeom.grabbackend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class RootController {

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Grab-style backend API");
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("customers", "/api/customers");
        endpoints.put("drivers", "/api/drivers");
        endpoints.put("nearbyDrivers", "/api/drivers/nearby?lat=..&lng=..&radius_km=5");
        endpoints.put("registerDriverFcmToken", "POST /api/drivers/{id}/fcm-token");
        endpoints.put("sendDriverMessage", "POST /api/drivers/{id}/send-message");
        endpoints.put("trips", "/api/trips");
        payload.put("endpoints", endpoints);
        return payload;
    }
}
