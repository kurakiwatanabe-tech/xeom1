package com.xeom.grabbackend.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.RequiredArgsConstructor;
@Configuration
@RequiredArgsConstructor
public class Firebasemessaging {
    private final FirebaseProperties firebaseProperties;

    @Bean
    FirebaseMessaging firebaseMessaging() throws IOException {
        String credentialsPath = firebaseProperties.getGoogleCredentials();
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IOException("Firebase credentials path is not configured");
        }

        GoogleCredentials googleCredentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath));
        FirebaseOptions firebaseOptions = FirebaseOptions
                .builder()
                .setCredentials(googleCredentials)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(firebaseOptions, "my-app");
        }
        FirebaseApp app = FirebaseApp.getInstance("my-app");
        return FirebaseMessaging.getInstance(app);
    }
}
