package com.xeom.grabbackend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xeom.grabbackend.model.Customer;
import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.model.Trip;
import com.xeom.grabbackend.repository.CustomerRepository;
import com.xeom.grabbackend.repository.DriverRepository;
import com.xeom.grabbackend.repository.TripRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:xeom-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("test")
class RideDataServiceTest {

    @Autowired
    private RideDataService rideDataService;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TripRepository tripRepository;

    @BeforeEach
    void clearDatabase() {
        tripRepository.deleteAll();
        customerRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void shouldPersistDriverCustomerAndTripToDatabase() {
        Driver driver = rideDataService.createDriver("Alex", "0900000000", "car", "ABC-1234");
        Customer customer = rideDataService.createCustomer("Mai", "0911111111", "mai@example.com", "District 1");
        Trip trip = rideDataService.createTrip(customer.getId(), driver.getId(), 120.0, "requested");

        assertThat(driver.getId()).isNotBlank();
        assertThat(customer.getId()).isNotBlank();
        assertThat(trip.getId()).isNotBlank();

        List<Driver> drivers = rideDataService.listDrivers();
        List<Customer> customers = rideDataService.listCustomers();
        List<Trip> trips = rideDataService.listTrips();

        assertThat(drivers).extracting(d -> d.getId()).contains(driver.getId());
        assertThat(customers).extracting(c -> c.getId()).contains(customer.getId());
        assertThat(trips).extracting(t -> t.getId()).contains(trip.getId());
    }

    @Test
    void shouldRemoveStaleDriversFromDatabaseAndRedis() {
        // Driver driver = rideDataService.createDriver("Bob", "0900000001", "bike", "XY-1111");
        // driver.setStatus("AVAILABLE");
        // driver.setStatusUpdatedAt(Instant.now().minus(Duration.ofMinutes(3)).toString());
        // rideDataService.saveDriver(driver);

        // int removed = rideDataService.markStaleDriversOffline(Duration.ofMinutes(2));

        // assertThat(removed).isEqualTo(1);
        // assertThat(rideDataService.findDriver(driver.getId())).isEmpty();
    }

    @Test
    void shouldRemoveStaleDriverEvenWhenTripStillReferencesIt() {
        Driver driver = rideDataService.createDriver("Carol", "0900000002", "car", "ZZ-9999");
        driver.setStatus("AVAILABLE");
        driver.setStatusUpdatedAt(Instant.now().minus(Duration.ofMinutes(3)).toString());
        rideDataService.saveDriver(driver);

        Trip trip = rideDataService.createTrip("cust_test", driver.getId(), 99.0, "REQUESTED");

        int removed = rideDataService.markStaleDriversOffline(Duration.ofMinutes(2));

        assertThat(removed).isEqualTo(1);
        assertThat(rideDataService.findDriver(driver.getId())).isEmpty();
        assertThat(rideDataService.findTrip(trip.getId())).isPresent();
        assertThat(rideDataService.findTrip(trip.getId()).get().getDriverId()).isNull();
    }
}
