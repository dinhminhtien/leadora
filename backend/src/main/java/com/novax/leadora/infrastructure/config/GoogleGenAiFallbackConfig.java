package com.novax.leadora.infrastructure.config;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.lang.reflect.Method;
@Configuration
public class GoogleGenAiFallbackConfig {
    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiFallbackConfig.class);
    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client googleGenAiClient(GoogleGenAiConnectionProperties connectionProperties) {
        String rawApiKey = null;
        String rawProjectId = null;
        String rawLocation = null;
        Boolean vertexAi = null;
        try {
            for (Method m : connectionProperties.getClass().getMethods()) {
                if (m.getName().equals("getApiKey") && m.getParameterCount() == 0) {
                    rawApiKey = (String) m.invoke(connectionProperties);
                } else if ((m.getName().equals("getProjectId") || m.getName().equals("getProject")) && m.getParameterCount() == 0) {
                    rawProjectId = (String) m.invoke(connectionProperties);
                } else if (m.getName().equals("getLocation") && m.getParameterCount() == 0) {
                    rawLocation = (String) m.invoke(connectionProperties);
                } else if ((m.getName().equals("getVertexAi") || m.getName().equals("isVertexAi") || m.getName().equals("vertexAi")) && m.getParameterCount() == 0) {
                    vertexAi = (Boolean) m.invoke(connectionProperties);
                }
            }
        } catch (Exception e) {
            log.error("Failed to inspect GoogleGenAiConnectionProperties via reflection", e);
        }
        // Sanitize parameters to handle Spring placeholder default values like "#{null}" or "null"
        String apiKey = sanitize(rawApiKey);
        String projectId = sanitize(rawProjectId);
        String location = sanitize(rawLocation);
        log.info("Configuring Google GenAI Client (Fallback-Aware). Vertex AI mode: {}, Project ID: {}, Location: {}, API Key configured: {}", 
                 vertexAi, projectId, location, (apiKey != null));
        // Check if Vertex AI is active and if API Key is not set
        if (Boolean.TRUE.equals(vertexAi) && apiKey == null) {
            try {
                // Verify if Application Default Credentials are available
                GoogleCredentials.getApplicationDefault();
                log.info("Google Application Default Credentials (ADC) found. Initializing Vertex AI client.");
                
                Client.Builder builder = Client.builder()
                        .vertexAI(true)
                        .location(location);
                if (projectId != null) {
                    builder.project(projectId);
                }
                return builder.build();
            } catch (Exception e) {
                log.warn("Google Application Default Credentials (ADC) were not found or failed to load: {}. " +
                         "Falling back to a placeholder Client with a dummy API Key to allow local development/testing startup.", 
                         e.getMessage());
                
                // Fall back to a dummy developer API key mode to bypass ADC checks
                return Client.builder()
                        .apiKey("DUMMY_KEY_FOR_LOCAL_STARTUP")
                        .build();
            }
        }
        // If not Vertex AI mode (or API Key is explicitly provided), build using properties
        // Enforce mutual exclusivity of Project/Location and API Key required by the SDK
        Client.Builder builder = Client.builder();
        if (apiKey != null) {
            builder.apiKey(apiKey);
        } else {
            if (Boolean.TRUE.equals(vertexAi)) {
                builder.vertexAI(true);
            }
            if (projectId != null) {
                builder.project(projectId);
            }
            if (location != null) {
                builder.location(location);
            }
        }
        
        return builder.build();
    }
    private String sanitize(String val) {
        if (val == null) {
            return null;
        }
        String trimmed = val.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") || trimmed.startsWith("#{")) {
            return null;
        }
        return trimmed;
    }
}