package com.novax.leadora.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    @Value("${SUPABASE_JWT_SECRET}")
    private String jwtSecret;

    // Default expiration: 24 hours (in seconds)
    private static final long EXPIRATION_SECONDS = 86400;

    public String generateToken(UserEntity user) {
        try {
            byte[] secretBytes;
            try {
                secretBytes = Base64.getDecoder().decode(jwtSecret.trim());
            } catch (IllegalArgumentException e) {
                secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            }

            JWSSigner signer = new MACSigner(secretBytes);

            Instant now = Instant.now();
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getUserId().toString())
                    .claim("email", user.getEmail())
                    .claim("role", user.getRole() != null ? user.getRole().getRoleName() : "STAFF")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(EXPIRATION_SECONDS)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }
}
