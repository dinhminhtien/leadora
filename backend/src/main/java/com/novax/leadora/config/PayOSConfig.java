package com.novax.leadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
public class PayOSConfig {

    @Value("${PAYOS_CLIENT_ID:dummy-client-id}")
    private String clientId;

    @Value("${PAYOS_API_KEY:dummy-api-key}")
    private String apiKey;

    @Value("${PAYOS_CHECKSUM_KEY:dummy-checksum-key}")
    private String checksumKey;

    @Bean
    public PayOS payOS() {
        return new PayOS(clientId.trim(), apiKey.trim(), checksumKey.trim());
    }
}
