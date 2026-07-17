package com.xeom.grabbackend.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.model.DriverStatus;
import com.xeom.grabbackend.service.FirebaseNotificationService;
import com.xeom.grabbackend.service.RideDataService;

@RestController
@RequestMapping("/api/drivers")
@CrossOrigin(origins = "*")
public class DriverController {
    private static final Set<String> VALID_STATUSES = Arrays.stream(DriverStatus.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
    private static final Set<String> VALID_VEHICLES = Set.of("bike", "car", "car7", "truck");

    private final RideDataService rideDataService;
    private final FirebaseNotificationService firebaseNotificationService;

    public DriverController(RideDataService rideDataService, FirebaseNotificationService firebaseNotificationService) {
        this.rideDataService = rideDataService;
        this.firebaseNotificationService = firebaseNotificationService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "status", required = false) String status, @RequestParam(name = "vehicle", required = false) String vehicle) {
        List<Driver> list = rideDataService.listDrivers(status, vehicle);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", list.size());
        response.put("data", list);
        return response;
    }

    @GetMapping("/nearby")
    public Map<String, Object> nearby(@RequestParam(name = "lat") double lat, @RequestParam(name = "lng") double lng,
                                      @RequestParam(name = "radius_km", defaultValue = "5") double radius_km,
                                      @RequestParam(name = "limit", defaultValue = "10") int limit,
                                      @RequestParam(name = "vehicle", required = false) String vehicle,
                                      @RequestParam(name = "status", defaultValue = "AVAILABLE") String status) {
        List<Map<String, Object>> candidates = rideDataService.findNearbyDrivers(lat, lng, radius_km, limit, vehicle, status);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("origin", Map.of("lat", lat, "lng", lng));
        response.put("radius_km", radius_km);
        response.put("count", candidates.size());
        response.put("data", candidates);
        return response;
    }

    @GetMapping("/{id}")
    public Driver getById(@PathVariable("id") @NonNull String id) {
        return findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
    }

    @GetMapping("/by-phone/{phone}")
    public Driver getByPhone(@PathVariable("phone") @NonNull String phone) {
        return rideDataService.findDriverByPhone(phone).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế với số điện thoại: " + phone));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Driver create(@RequestBody Driver payload) {
        if (payload.getName() == null || payload.getName().isBlank() || payload.getPhone() == null || payload.getPhone().isBlank() || payload.getVehicle() == null || payload.getPlate() == null || payload.getPlate().isBlank()) {
            throw new BadRequestException("Thiếu trường bắt buộc: name, phone, vehicle, plate");
        }
        if (!VALID_VEHICLES.contains(payload.getVehicle())) {
            throw new BadRequestException("vehicle phải là một trong: bike, car, car7, truck");
        }
        Driver driver = new Driver();
        driver.setId("drv_" + Instant.now().toEpochMilli());
        driver.setName(payload.getName());
        driver.setPhone(payload.getPhone());
        driver.setVehicle(payload.getVehicle());
        driver.setPlate(payload.getPlate());
        driver.setLat(payload.getLat());
        driver.setLng(payload.getLng());
        driver.setStatus(DriverStatus.OFFLINE.name());
        driver.setRating(5.0);
        driver.setCreatedAt(Instant.now().toString());
        return rideDataService.saveDriver(driver);
    }

    @PutMapping("/{id}")
    public Driver update(@PathVariable String id, @RequestBody Driver payload) {
        Driver existing = findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        if (payload.getVehicle() != null && !VALID_VEHICLES.contains(payload.getVehicle())) {
            throw new BadRequestException("vehicle phải là một trong: bike, car, car7, truck");
        }
        if (payload.getName() != null) existing.setName(payload.getName());
        if (payload.getPhone() != null) existing.setPhone(payload.getPhone());
        if (payload.getVehicle() != null) existing.setVehicle(payload.getVehicle());
        if (payload.getPlate() != null) existing.setPlate(payload.getPlate());
        existing.setUpdatedAt(Instant.now().toString());
        return rideDataService.saveDriver(existing);
    }

    @PatchMapping("/{id}/location")
    public Driver updateLocation(@PathVariable("id") @NonNull String id, @RequestBody Map<String, Object> payload) {
        if (!payload.containsKey("lat") || !payload.containsKey("lng")) throw new BadRequestException("Thiếu trường bắt buộc: lat, lng");
        return rideDataService.updateDriverLocation(Objects.requireNonNull(id), Double.valueOf(payload.get("lat").toString()), Double.valueOf(payload.get("lng").toString()));
    }

    @PatchMapping("/{id}/status")
    public Driver updateStatus(@PathVariable("id") @NonNull String id, @RequestBody Map<String, String> payload) {
        String statusValue = payload.get("status");
        if (statusValue == null || statusValue.isBlank()) {
            throw new BadRequestException("Thiếu trường bắt buộc: status");
        }
        DriverStatus driverStatus = DriverStatus.fromValue(statusValue);
        if (!VALID_STATUSES.contains(driverStatus.name())) {
            throw new BadRequestException("status phải là một trong: " + String.join(", ", VALID_STATUSES));
        }
        return rideDataService.updateDriverStatus(Objects.requireNonNull(id), driverStatus.name());
    }

    @PostMapping("/{id}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable("id") @NonNull String id, @RequestBody(required = false) Map<String, Object> payload) {
        findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        String statusValue = payload != null && payload.get("status") != null ? payload.get("status").toString() : null;
        String nextStatus = statusValue != null && !statusValue.isBlank() ? statusValue.toUpperCase() : DriverStatus.AVAILABLE.name();
        DriverStatus driverStatus = DriverStatus.fromValue(nextStatus);
        if (payload != null && payload.containsKey("lat") && payload.containsKey("lng")) {
            double lat = Double.parseDouble(payload.get("lat").toString());
            double lng = Double.parseDouble(payload.get("lng").toString());
            rideDataService.updateDriverLocation(id, lat, lng);
        }
        Driver saved = rideDataService.updateDriverStatus(id, driverStatus.name());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("status", saved.getStatus());
        response.put("heartbeatAt", Instant.now().toString());
        return response;
    }

    @PostMapping("/{id}/fcm-token")
    public Map<String, Object> saveFcmToken(@PathVariable("id") @NonNull String id, @RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Thiếu trường bắt buộc: token");
        }
        Driver existing = findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        existing.setFcmToken(token);
        Driver saved = rideDataService.saveDriver(existing);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("token", saved.getFcmToken());
        return response;
    }

    @PostMapping("/{id}/send-message")
    public Map<String, Object> sendMessage(@PathVariable("id") @NonNull String id, @RequestBody Map<String, Object> payload) {
        Driver driver = findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        String token = payload.get("token") != null ? payload.get("token").toString() : null;
        String title = payload.get("title") != null ? payload.get("title").toString() : "New message";
        String body = payload.get("body") != null ? payload.get("body").toString() : "";
        String resolvedToken = (token != null && !token.isBlank()) ? token : driver.getFcmToken();
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new BadRequestException("Driver chưa đăng ký FCM token");
        }

        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() == null) continue;
            if (Set.of("token", "title", "body").contains(entry.getKey())) continue;
            data.put(entry.getKey(), entry.getValue().toString());
        }

        String messageId = firebaseNotificationService.sendMessage(resolvedToken, title, body, data);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sent", true);
        response.put("driverId", id);
        response.put("messageId", messageId);
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") @NonNull String id) {
        Driver match = findDriver(id).orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế"));
        rideDataService.deleteDriver(Objects.requireNonNull(id));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deleted", true);
        response.put("driver", match);
        return response;
    }

    private Optional<Driver> findDriver(String id) {
        return rideDataService.findDriver(Objects.requireNonNull(id));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public Map<String, String> handleNotFound(NotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public Map<String, String> handleBadRequest(BadRequestException ex) {
        return Map.of("error", ex.getMessage());
    }

    private static class NotFoundException extends RuntimeException { private NotFoundException(String message) { super(message); } }
    private static class BadRequestException extends RuntimeException { private BadRequestException(String message) { super(message); } }
}
