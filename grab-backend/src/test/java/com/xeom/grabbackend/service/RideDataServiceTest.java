package com.xeom.grabbackend.service;

import com.xeom.grabbackend.model.Customer;
import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.model.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
