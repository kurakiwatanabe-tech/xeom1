package com.xeom.grabbackend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GrabBackendApplication {
    private static final Logger log = LoggerFactory.getLogger(GrabBackendApplication.class);

    public static void main(String[] args) {
        log.info("Starting Grab Backend application");
        SpringApplication.run(GrabBackendApplication.class, args);
    }
}
