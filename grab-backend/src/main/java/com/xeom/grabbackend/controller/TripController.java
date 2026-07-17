package com.xeom.grabbackend.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.xeom.grabbackend.model.Trip;
import com.xeom.grabbackend.service.FirebaseNotificationService;
import com.xeom.grabbackend.service.RideDataService;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class TripController {
    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    private final RideDataService rideDataService;
    private final FirebaseNotificationService firebaseNotificationService;

    public TripController(RideDataService rideDataService, FirebaseNotificationService firebaseNotificationService) {
        this.rideDataService = rideDataService;
        this.firebaseNotificationService = firebaseNotificationService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "customerId", required = false) String customerId,
                                    @RequestParam(name = "driverId", required = false) String driverId,
                                    @RequestParam(name = "status", required = false) String status) {
        log.debug("Listing trips: customerId={}, driverId={}, status={}", customerId, driverId, status);
        List<Trip> list = rideDataService.listTrips();
        if (customerId != null && !customerId.isBlank()) list = list.stream().filter(t -> customerId.equals(t.getCustomerId())).toList();
        if (driverId != null && !driverId.isBlank()) list = list.stream().filter(t -> driverId.equals(t.getDriverId())).toList();
        if (status != null && !status.isBlank()) list = list.stream().filter(t -> status.equals(t.getStatus())).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", list.size());
        response.put("data", list);
        return response;
    }

    @GetMapping("/{id}")
    public Trip getById(@PathVariable("id") @NonNull String id) {
        log.debug("Fetching trip {}", id);
        return findTrip(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip create(@RequestBody Trip payload) {
        log.info("Creating new trip for customerId={}", payload.getCustomerId());
        if (payload.getCustomerId() == null || payload.getCustomerId().isBlank()) {
            log.warn("Trip creation failed: missing customerId");
            throw new BadRequestException("Thiếu trường bắt buộc: customerId");
        }
        // if (payload.getStatus() != null && !VALID_STATUSES.contains(payload.getStatus())) {
        //     throw new BadRequestException("status phải là một trong: requested, ongoing, completed, cancelled");
        // }
        if (rideDataService.findCustomer(Objects.requireNonNull(payload.getCustomerId())).isEmpty()) {
            log.warn("Trip creation failed: invalid customerId={}", payload.getCustomerId());
            throw new BadRequestException("customerId không hợp lệ");
        }
        // if (payload.getDriverId() != null && !payload.getDriverId().isBlank() && rideDataService.findDriver(Objects.requireNonNull(payload.getDriverId())).isEmpty()) {
        //     throw new BadRequestException("driverId không hợp lệ");
        // }
        Trip trip = new Trip();
        trip.setId("trip_" + Instant.now().toEpochMilli());
        trip.setCustomerId(payload.getCustomerId());
        trip.setDriverId(payload.getDriverId());
        trip.setPrice(payload.getPrice());
        trip.setTimeStart(payload.getTimeStart());
        trip.setTimeEnd(payload.getTimeEnd());
        trip.setAddressStart(payload.getAddressStart());
        trip.setAddressEnd(payload.getAddressEnd());
        trip.setLatitudeStart(payload.getLatitudeStart());
        trip.setLongitudeStart(payload.getLongitudeStart());
        trip.setLatitudeEnd(payload.getLatitudeEnd());
        trip.setLongitudeEnd(payload.getLongitudeEnd());
        trip.setDistance(payload.getDistance());
        trip.setStatus(payload.getStatus() == null ? "requested" : payload.getStatus());
        trip.setCreatedAt(Instant.now().toString());

        // Try to find nearby drivers around the trip start point (5km radius)
        try {
            Double latObj = trip.getLatitudeStart();
            Double lngObj = trip.getLongitudeStart();
            double lat = latObj == null ? 0.0 : latObj.doubleValue();
            double lng = lngObj == null ? 0.0 : lngObj.doubleValue();
            log.debug("Searching for nearby drivers at lat={}, lng={}, radius=10km", lat, lng);
            List<Map<String, Object>> candidates = rideDataService.findNearbyDrivers(lat, lng, 10, 10, null, "AVAILABLE");
            log.debug("Found {} nearby drivers", candidates.size());
            if (!candidates.isEmpty()) {
                Object idObj = candidates.get(0).get("id");
                if (idObj != null) {
                    String assignedDriverId = idObj.toString();
                    trip.setDriverId(assignedDriverId);
                    log.info("Assigned driver {} to trip, distance_km={}", assignedDriverId, candidates.get(0).get("distance_km"));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to find nearby drivers for trip assignment", ex);
            // ignore lookup failures and proceed to save trip without assigned driver
        }

        Trip saved = rideDataService.saveTrip(trip);
        log.info("Trip {} created successfully, customerId={}, status={}", saved.getId(), saved.getCustomerId(), saved.getStatus());

        // Notify assigned driver (if any)
        try {
            if (saved.getDriverId() != null && !saved.getDriverId().isBlank()) {
                Optional<Driver> drvOpt = rideDataService.findDriver(saved.getDriverId());
                if (drvOpt.isPresent()) {
                    Driver drv = drvOpt.get();
                    String token = drv.getFcmToken();
                    if (token != null && !token.isBlank()) {
                        Map<String, String> data = new LinkedHashMap<>();
                        data.put("tripId", saved.getId());
                        data.put("customerId", saved.getCustomerId());
                        data.put("lat", String.valueOf(saved.getLatitudeStart()));
                        data.put("lng", String.valueOf(saved.getLongitudeStart()));
                       // data.put("lat", "37.42132786690192");
                        //data.put("lng", "-122.1361437329108");
                        // 37.42132786690192, -122.1361437329108
                        // 3375 El Camino Real, Palo Alto, CA 94306, United States
                        log.info("Sending FCM notification to driver {} about trip {}", drv.getId(), saved.getId());
                        firebaseNotificationService.sendMessage(token, "Ride request", "You have a new ride request", data);
                        log.debug("FCM notification sent successfully to driver {}", drv.getId());
                    } else {
                        log.warn("Driver {} has no FCM token registered", drv.getId());
                    }
                } else {
                    log.warn("Assigned driver {} not found", saved.getDriverId());
                }
            } else {
                log.info("No driver assigned to trip {}", saved.getId());
            }
        } catch (Exception ex) {
            log.error("Failed to notify driver about trip {}", saved.getId(), ex);
            // don't fail trip creation if notification fails
        }

        return saved;
    }

    @PutMapping("/{id}")
    public Trip update(@PathVariable("id") @NonNull String id, @RequestBody Trip payload) {
        log.info("Updating trip {}", id );
        Trip existing = findTrip(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi"));
        if (payload.getCustomerId() != null && !payload.getCustomerId().isBlank() && rideDataService.findCustomer(Objects.requireNonNull(payload.getCustomerId())).isEmpty()) {
            throw new BadRequestException("customerId không hợp lệ");
        }
        if (payload.getDriverId() != null && !payload.getDriverId().isBlank() && rideDataService.findDriver(Objects.requireNonNull(payload.getDriverId())).isEmpty()) {
            throw new BadRequestException("driverId không hợp lệ");
        }
        if (payload.getStatus() != null && !DriverStatus.isValid(payload.getStatus())) {
            throw new BadRequestException("status phải là một trong: requested, ongoing, completed, cancelled");
        }
        if (payload.getCustomerId() != null) existing.setCustomerId(payload.getCustomerId());
        if (payload.getDriverId() != null) existing.setDriverId(payload.getDriverId());
        if (payload.getPrice() != null) existing.setPrice(payload.getPrice());
        if (payload.getTimeStart() != null) existing.setTimeStart(payload.getTimeStart());
        if (payload.getTimeEnd() != null) existing.setTimeEnd(payload.getTimeEnd());
        if (payload.getLatitudeStart() != null) existing.setLatitudeStart(payload.getLatitudeStart());
        if (payload.getLongitudeStart() != null) existing.setLongitudeStart(payload.getLongitudeStart());
        if (payload.getLatitudeEnd() != null) existing.setLatitudeEnd(payload.getLatitudeEnd());
        if (payload.getLongitudeEnd() != null) existing.setLongitudeEnd(payload.getLongitudeEnd());
        if (payload.getStatus() != null) existing.setStatus(payload.getStatus());
        existing.setUpdatedAt(Instant.now().toString());
        Trip updated = rideDataService.saveTrip(existing);
        log.info("Trip {} updated successfully", id);
        return updated;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") @NonNull String id) {
        log.info("Deleting trip {}", id);
        Trip match = findTrip(id).orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi"));
        rideDataService.deleteTrip(Objects.requireNonNull(id));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deleted", true);
        response.put("trip", match);
        return response;
    }

    private Optional<Trip> findTrip(String id) {
        return rideDataService.findTrip(Objects.requireNonNull(id));
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
