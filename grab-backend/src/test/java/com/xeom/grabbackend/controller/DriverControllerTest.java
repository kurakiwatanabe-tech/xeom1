package com.xeom.grabbackend.controller;

import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.service.RideDataService;
import com.xeom.grabbackend.service.FirebaseNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverController.class)
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RideDataService rideDataService;

    @MockBean
    private FirebaseNotificationService firebaseNotificationService;

    @Test
    void shouldSaveDriverFcmToken() throws Exception {
        Driver driver = new Driver();
        driver.setId("drv_1");
        driver.setName("Alice");
        driver.setPhone("0900000000");
        driver.setVehicle("car");
        driver.setPlate("51A-12345");
        driver.setStatus("offline");
        driver.setRating(5.0);

        when(rideDataService.findDriver("drv_1")).thenReturn(Optional.of(driver));
        when(rideDataService.saveDriver(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/drivers/drv_1/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"test-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"));

        verify(rideDataService).saveDriver(argThat(saved -> "test-token".equals(saved.getFcmToken())));
    }

    @Test
    void shouldSendMessageToDriverUsingSavedToken() throws Exception {
        Driver driver = new Driver();
        driver.setId("drv_1");
        driver.setName("Alice");
        driver.setPhone("0900000000");
        driver.setVehicle("car");
        driver.setPlate("51A-12345");
        driver.setStatus("offline");
        driver.setRating(5.0);
        driver.setFcmToken("saved-token");

        when(rideDataService.findDriver("drv_1")).thenReturn(Optional.of(driver));
        when(firebaseNotificationService.sendMessage(eq("saved-token"), eq("Ride request"), eq("You have a new ride"), anyMap()))
                .thenReturn("message-id");

        mockMvc.perform(post("/api/drivers/drv_1/send-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ride request\",\"body\":\"You have a new ride\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.messageId").value("message-id"));
    }

    @Test
    void shouldHeartbeatDriverAndUpdateLocation() throws Exception {
        Driver driver = new Driver();
        driver.setId("drv_1");
        driver.setName("Alice");
        driver.setPhone("0900000000");
        driver.setVehicle("car");
        driver.setPlate("51A-12345");
        driver.setStatus("offline");
        driver.setRating(5.0);

        when(rideDataService.findDriver("drv_1")).thenReturn(Optional.of(driver));
        when(rideDataService.updateDriverLocation(eq("drv_1"), eq(21.0245), eq(105.8412))).thenAnswer(invocation -> {
            driver.setLat(21.0245);
            driver.setLng(105.8412);
            return driver;
        });
        when(rideDataService.updateDriverStatus(eq("drv_1"), eq("AVAILABLE"))).thenAnswer(invocation -> {
            driver.setStatus("AVAILABLE");
            return driver;
        });

        mockMvc.perform(post("/api/drivers/drv_1/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"available\",\"lat\":21.0245,\"lng\":105.8412}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        verify(rideDataService).updateDriverLocation("drv_1", 21.0245, 105.8412);
        verify(rideDataService).updateDriverStatus("drv_1", "AVAILABLE");
    }
}
