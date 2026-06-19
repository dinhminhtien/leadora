package com.novax.leadora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            // All endpoints are open for now — JWT role enforcement will be added in a later phase.
            // NOTE: oauth2ResourceServer is intentionally removed.
            // Supabase signs access tokens with RS256 (asymmetric). Spring's default JWK decoder
            // fetches certs from /auth/v1/certs which requires authentication — causing it to fail
            // and reject all API requests despite the user being authenticated on the frontend.
            // Since all routes are public (permitAll) for the MVP phase, we skip server-side JWT
            // validation here. The frontend still sends Bearer tokens for future auth enforcement.
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.vercel.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // X-User-Id: temporary actor identity used by the AI chat assistant while
        // server-side JWT auth is not yet wired (login feature still in progress).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-User-Id"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
