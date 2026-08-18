package com.novax.leadora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.novax.leadora.common.security.JwtAuthoritiesResolver;
import com.novax.leadora.common.security.TokenBlacklistService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import java.util.List;
import com.novax.leadora.api.filter.MdcUserFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.novax.leadora.infrastructure.security.audit.SecurityAuditLogger;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Value("${SUPABASE_JWT_SECRET}")
    private String jwtSecret;

    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Autowired
    @Lazy
    private JwtAuthoritiesResolver jwtAuthoritiesResolver;

    @Autowired
    @Lazy
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    @Lazy
    private SecurityAuditLogger securityAuditLogger;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/api/v1/auth/login", "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password", "/api/v1/health", "/api/v1/feedback/public/**",
                                "/api/v1/public/**")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/profile").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                        .bearerTokenResolver(request -> {
                            String path = request.getRequestURI();
                            if ("/".equals(path) || "/error".equals(path) || "/api/v1/health".equals(path)
                                    || path.startsWith("/api/v1/feedback/public/")
                                    || path.startsWith("/api/v1/public/")
                                    || path.startsWith("/api/v1/auth/login")
                                    || path.startsWith("/api/v1/auth/logout")
                                    || path.startsWith("/api/v1/auth/forgot-password")
                                    || path.startsWith("/api/v1/auth/reset-password")) {
                                return null;
                            }
                            // Extract Bearer token without throwing — returns null instead of throwing on
                            // invalid input
                            String header = request.getHeader("Authorization");
                            if (header != null && header.startsWith("Bearer ")) {
                                String token = header.substring(7).trim();
                                return token.isBlank() ? null : token;
                            }
                            return null;
                        }));

        http.addFilterAfter(new MdcUserFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            securityAuditLogger.logInvalidTokenAccess(request, authException);

            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank()) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, X-User-Id");
            }

            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\""
                    + authException.getMessage() + "\"}");
        };
    }

    private AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            securityAuditLogger.logAccessDenied(request, accessDeniedException);

            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank()) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, X-User-Id");
            }

            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"errorCode\":\"ACCESS_DENIED\",\"message\":\""
                    + accessDeniedException.getMessage() + "\"}");
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // 1. Build symmetric HS256 decoder
        String secret = jwtSecret.trim();
        if (secret.startsWith("\"") && secret.endsWith("\"") && secret.length() > 1) {
            secret = secret.substring(1, secret.length() - 1);
        }
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        JwtDecoder hs256Decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();

        // 2. Build asymmetric RS256/ES256/PS256 decoder using Supabase JWKS URI
        String url = supabaseUrl.trim();
        if (url.startsWith("\"") && url.endsWith("\"") && url.length() > 1) {
            url = url.substring(1, url.length() - 1);
        }
        String jwksUri = url + "/auth/v1/.well-known/jwks.json";
        JwtDecoder rs256Decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithms(algs -> {
                    algs.add(SignatureAlgorithm.RS256);
                    algs.add(SignatureAlgorithm.ES256);
                    algs.add(SignatureAlgorithm.RS384);
                    algs.add(SignatureAlgorithm.RS512);
                })
                .build();

        // 3. Return a delegating decoder based on the JWT header algorithm
        return token -> {
            if (tokenBlacklistService.isBlacklisted(token)) {
                throw new JwtException("Token is blacklisted");
            }
            try {
                var jwt = JWTParser.parse(token);
                if (jwt instanceof SignedJWT signedJwt) {
                    var algorithm = signedJwt.getHeader().getAlgorithm();
                    String algName = algorithm != null ? algorithm.getName() : "";
                    if (algName.startsWith("RS") || algName.startsWith("ES") || algName.startsWith("PS")) {
                        return rs256Decoder.decode(token);
                    }
                }
                return hs256Decoder.decode(token);
            } catch (JwtException ex) {
                throw ex; // Let validation/expiration exceptions pass through directly
            } catch (Exception ex) {
                throw new JwtException("Failed to decode JWT: " + ex.getMessage(), ex);
            }
        };
    }

    /**
     * Extracts the application role AND the role's effective permission codes from
     * our own tables
     * using the email claim in the Supabase JWT. Supabase's built-in "role" claim
     * always returns
     * "authenticated", not the app role.
     *
     * @see com.novax.leadora.common.security.JwtAuthoritiesResolver
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> jwtAuthoritiesResolver.resolve(jwt.getClaimAsString("email")));
        return converter;
    }

    /**
     * Password hashing for user-account management (UC-6.2 / UC-6.3).
     * BCrypt — matches the existing {@code $2a$10$...} hashes already seeded in the
     * {@code users} table.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.vercel.app",
                "https://www.leadora.id.vn",
                "https://leadora.id.vn"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-User-Id"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
