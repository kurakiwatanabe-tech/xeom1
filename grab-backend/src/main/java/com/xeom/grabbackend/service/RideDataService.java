package com.xeom.grabbackend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xeom.grabbackend.model.Customer;
import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.model.DriverStatus;
import com.xeom.grabbackend.model.Trip;
import com.xeom.grabbackend.repository.CustomerRepository;
import com.xeom.grabbackend.repository.DriverRepository;
import com.xeom.grabbackend.repository.TripRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class RideDataService {
    private static final Logger log = LoggerFactory.getLogger(RideDataService.class);

    private final DriverRepository driverRepository;
    private final CustomerRepository customerRepository;
    private final TripRepository tripRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RideDataService(DriverRepository driverRepository,
                           CustomerRepository customerRepository,
                           TripRepository tripRepository,
                           RedisTemplate<String, Object> redisTemplate) {
        this.driverRepository = driverRepository;
        this.customerRepository = customerRepository;
        this.tripRepository = tripRepository;
        this.redisTemplate = redisTemplate;
    }

    public List<Driver> listDrivers() {
        return driverRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public List<Driver> listDrivers(String status, String vehicle) {
        List<Driver> drivers = listDrivers();
        if (status != null && !status.isBlank()) {
            String normalizedStatus = DriverStatus.fromValue(status).name();
            drivers = drivers.stream().filter(d -> normalizedStatus.equals(d.getStatus())).toList();
        }
        if (vehicle != null && !vehicle.isBlank()) {
            drivers = drivers.stream().filter(d -> vehicle.equals(d.getVehicle())).toList();
        }
        return drivers;
    }

    public List<Customer> listCustomers() {
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public List<Trip> listTrips() {
        return tripRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Optional<Driver> findDriver(@NonNull String id) {
        return driverRepository.findById(id);
    }

    public Optional<Driver> findDriverByPhone(@NonNull String phone) {
        return driverRepository.findByPhone(phone);
    }

    public Optional<Customer> findCustomer(@NonNull String id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findCustomerByPhone(@NonNull String phone) {
        return customerRepository.findByPhone(phone);
    }

    public Optional<Trip> findTrip(@NonNull String id) {
        return tripRepository.findById(id);
    }

    public Driver createDriver(String name, String phone, String vehicle, String plate) {
        Driver driver = new Driver();
        driver.setId("drv_" + Instant.now().toEpochMilli());
        driver.setName(name);
        driver.setPhone(phone);
        driver.setVehicle(vehicle);
        driver.setPlate(plate);
        driver.setStatus(DriverStatus.OFFLINE.name());
        driver.setRating(5.0);
        driver.setCreatedAt(Instant.now().toString());
        return saveDriver(driver);
    }

    public Customer createCustomer(String name, String phone, String email, String address) {
        Customer customer = new Customer();
        customer.setId("cust_" + Instant.now().toEpochMilli());
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setAddress(address);
        customer.setCreatedAt(Instant.now().toString());
        return saveCustomer(customer);
    }

    public Trip createTrip(String customerId, String driverId, Double price, String status) {
        Trip trip = new Trip();
        trip.setId("trip_" + Instant.now().toEpochMilli());
        trip.setCustomerId(customerId);
        trip.setDriverId(driverId);
        trip.setPrice(price);
        trip.setStatus(status == null || status.isBlank() ? "RECEIVER_REQUEST" : status.toUpperCase());
        trip.setCreatedAt(Instant.now().toString());
        return saveTrip(trip);
    }

    public Driver saveDriver(@NonNull Driver driver) {
        Driver saved = driverRepository.save(driver);
        if (saved.getLat() != null && saved.getLng() != null) {
            syncDriverLocationToRedis(saved.getId(), saved.getLat(), saved.getLng());
        }
        return saved;
    }

    public Customer saveCustomer(@NonNull Customer customer) {
        return customerRepository.save(customer);
    }

    public Trip saveTrip(@NonNull Trip trip) {
        return tripRepository.save(trip);
    }

    public Driver updateDriverLocation(@NonNull String id, Double lat, Double lng) {
        Driver driver = driverRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        driver.setLat(lat);
        driver.setLng(lng);
        driver.setLocationUpdatedAt(Instant.now().toString());
        Driver saved = saveDriver(driver);
        syncDriverLocationToRedis(saved.getId(), saved.getLat(), saved.getLng());
        return saved;
    }

    public Driver updateDriverStatus(@NonNull String id, String status) {
        Driver driver = driverRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        driver.setStatus(status);
        driver.setStatusUpdatedAt(Instant.now().toString());
        return saveDriver(driver);
    }

    public int markStaleDriversOffline(@NonNull Duration staleAfter) {
        List<Driver> drivers = listDrivers();
        int removed = 0;
        Instant cutoff = Instant.now().minus(staleAfter);

        for (Driver driver : drivers) {
            if (driver.getStatusUpdatedAt() == null || driver.getStatusUpdatedAt().isBlank()) {
                continue;
            }

            try {
                Instant lastStatusUpdate = Instant.parse(driver.getStatusUpdatedAt());
                if (!lastStatusUpdate.isBefore(cutoff)) {
                    continue;
                }

                removeDriverFromRedis(driver.getId());
                updateDriverStatus(driver.getId(), DriverStatus.OFFLINE.name());
                removed++;
            } catch (Exception ex) {
                log.warn("Unable to remove stale driver {}", driver.getId(), ex);
            }
        }

        return removed;
    }

    public void deleteDriver(@NonNull String id) {
        Optional<Driver> driver = driverRepository.findById(id);
        if (driver.isEmpty()) {
            return;
        }

        List<Trip> trips = tripRepository.findByDriverId(id);
        for (Trip trip : trips) {
            trip.setDriverId(null);
            tripRepository.save(trip);
        }
        driverRepository.delete(driver.get());
        driverRepository.flush();
        entityManager.clear();
    }

    public void deleteCustomer(@NonNull String id) {
        customerRepository.deleteById(id);
    }

    public void deleteTrip(@NonNull String id) {
        tripRepository.deleteById(id);
    }

    public List<Map<String, Object>> findNearbyDrivers(double lat, double lng, double radiusKm, int limit, String vehicle, String status) {
        List<Map<String, Object>> response = new ArrayList<>();
        try {
            Circle circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
                var radiusResults = redisTemplate.opsForGeo().radius("drivers:location", circle);
                if (radiusResults == null || radiusResults.getContent() == null) {
                return fallbackNearbyDrivers(lat, lng, radiusKm, limit, vehicle, status);
                }
                List<RedisGeoCommands.GeoLocation<Object>> geoLocations = radiusResults.getContent().stream()
                    .map(geoResult -> geoResult.getContent())
                    .toList();
            if (geoLocations.isEmpty()) {
                return fallbackNearbyDrivers(lat, lng, radiusKm, limit, vehicle, status);
            }
            for (RedisGeoCommands.GeoLocation<Object> geoLocation : geoLocations) {
                Object nameObj = geoLocation.getName();
                if (nameObj == null) continue;
                String driverId = Objects.requireNonNull(nameObj.toString());
                Optional<Driver> driverOptional = driverRepository.findById(driverId);
                if (driverOptional.isPresent()) {
                    Driver driver = driverOptional.get();
                    if (vehicle != null && !vehicle.isBlank() && !vehicle.equals(driver.getVehicle())) {
                        continue;
                    }
                    if (status != null && !status.isBlank() && !status.equals(driver.getStatus())) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", driver.getId());
                    item.put("name", driver.getName());
                    item.put("phone", driver.getPhone());
                    item.put("vehicle", driver.getVehicle());
                    item.put("plate", driver.getPlate());
                    item.put("lat", driver.getLat());
                    item.put("lng", driver.getLng());
                    item.put("status", driver.getStatus());
                    item.put("rating", driver.getRating());
                    item.put("distance_km", Math.round(distanceKm(lat, lng, driver.getLat(), driver.getLng()) * 100.0) / 100.0);
                    response.add(item);
                    if (response.size() >= limit) {
                        break;
                    }
                }
            }
            if (!response.isEmpty()) {
                response.sort((a, b) -> Double.compare((Double) a.get("distance_km"), (Double) b.get("distance_km")));
            }
            return response;
        } catch (Exception ex) {
            log.warn("Redis geo lookup failed, falling back to database scan", ex);
            return fallbackNearbyDrivers(lat, lng, radiusKm, limit, vehicle, status);
        }
    }

    private List<Map<String, Object>> fallbackNearbyDrivers(double lat, double lng, double radiusKm, int limit, String vehicle, String status) {
        List<Map<String, Object>> response = new ArrayList<>();
        for (Driver driver : listDrivers()) {
            if (driver.getLat() == null || driver.getLng() == null) {
                continue;
            }
            if (vehicle != null && !vehicle.isBlank() && !vehicle.equals(driver.getVehicle())) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(driver.getStatus())) {
                continue;
            }
            double distanceKm = distanceKm(lat, lng, driver.getLat(), driver.getLng());
            if (distanceKm > radiusKm) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", driver.getId());
            item.put("name", driver.getName());
            item.put("phone", driver.getPhone());
            item.put("vehicle", driver.getVehicle());
            item.put("plate", driver.getPlate());
            item.put("lat", driver.getLat());
            item.put("lng", driver.getLng());
            item.put("status", driver.getStatus());
            item.put("rating", driver.getRating());
            item.put("distance_km", Math.round(distanceKm * 100.0) / 100.0);
            response.add(item);
            if (response.size() >= limit) {
                break;
            }
        }
        response.sort((a, b) -> Double.compare((Double) a.get("distance_km"), (Double) b.get("distance_km")));
        return response;
    }

    private void syncDriverLocationToRedis(String driverId, Double lat, Double lng) {
        if (driverId == null || lat == null || lng == null) {
            return;
        }
        try {
            redisTemplate.opsForGeo().add("drivers:location", new Point(lng, lat), driverId);
        } catch (Exception ex) {
            log.warn("Unable to persist driver location to Redis", ex);
        }
    }

    private void removeDriverFromRedis(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForGeo().remove("drivers:location", driverId);
        } catch (Exception ex) {
            log.warn("Unable to remove driver location from Redis for {}", driverId, ex);
        }
    }

    private double distanceKm(double lat1, double lng1, Double lat2, Double lng2) {
        if (lat2 == null || lng2 == null) {
            return Double.MAX_VALUE;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
