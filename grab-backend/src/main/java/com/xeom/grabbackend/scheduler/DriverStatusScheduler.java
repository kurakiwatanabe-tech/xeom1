package com.xeom.grabbackend.scheduler;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xeom.grabbackend.service.RideDataService;

@Component
public class DriverStatusScheduler {
    private static final Logger log = LoggerFactory.getLogger(DriverStatusScheduler.class);

    private final RideDataService rideDataService;

    public DriverStatusScheduler(RideDataService rideDataService) {
        this.rideDataService = rideDataService;
    }

    @Scheduled(fixedRate = 60000)
    public void markStaleDriversOffline() {
        int removed = rideDataService.markStaleDriversOffline(Duration.ofMinutes(2));
        if (removed > 0) {
            log.info("Removed {} stale drivers from database and Redis", removed);
        }
    }
}
