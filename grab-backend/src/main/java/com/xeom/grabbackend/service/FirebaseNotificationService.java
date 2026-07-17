package com.xeom.grabbackend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FirebaseNotificationService {
    private static final Logger log = LoggerFactory.getLogger(FirebaseNotificationService.class);

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseNotificationService(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public String sendMessage(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("FCM token is required");
        }

        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title == null || title.isBlank() ? "New message" : title)
                        .setBody(body == null ? "" : body)
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }

        try {
            return firebaseMessaging.send(builder.build());
        } catch (FirebaseMessagingException ex) {
            log.error("Unable to send Firebase notification to token {}", token, ex);
            throw new IllegalStateException("Unable to send Firebase notification", ex);
        }
    }
}
