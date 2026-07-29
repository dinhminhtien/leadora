package com.novax.leadora.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }

            GoogleCredentials credentials = null;

            // 1. Try environment variable GOOGLE_APPLICATION_CREDENTIALS
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (envPath != null && !envPath.trim().isEmpty()) {
                Path path = Paths.get(envPath);
                if (Files.exists(path)) {
                    try (InputStream is = Files.newInputStream(path)) {
                        credentials = GoogleCredentials.fromStream(is);
                        log.info("Firebase credentials loaded from GOOGLE_APPLICATION_CREDENTIALS path: {}", envPath);
                    }
                }
            }

            // 2. Try root project .env configurations or local classpath resource
            if (credentials == null) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json")) {
                    if (is != null) {
                        credentials = GoogleCredentials.fromStream(is);
                        log.info("Firebase credentials loaded from classpath: firebase-service-account.json");
                    }
                }
            }

            // 3. Fallback to Application Default Credentials (ADC)
            if (credentials == null) {
                try {
                    credentials = GoogleCredentials.getApplicationDefault();
                    log.info("Firebase loaded via Google Application Default Credentials (ADC).");
                } catch (IOException e) {
                    log.warn("Could not load Google Application Default Credentials: {}", e.getMessage());
                }
            }

            if (credentials == null) {
                log.error("Firebase App initialization aborted: No valid credentials found.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase App initialized successfully.");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase App due to IO exception", e);
        }
    }
}
