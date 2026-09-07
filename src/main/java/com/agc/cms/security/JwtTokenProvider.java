package com.agc.cms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:d291bGQteW91LWxpa2UtdG8tYnVpbGQtYS1zdXBlci1zZWN1cmUtand0LXNlY3JldC1rZXktZm9yLWFnYy1jbXMtMjAyNiE=}") String secret,
            @Value("${jwt.expiration-hours:8}") long expirationHours) {
        
        byte[] keyBytes;
        try {
            // Attempt Base64 decode if formatted as Base64, otherwise use UTF-8 bytes with padding if needed
            keyBytes = java.util.Base64.getDecoder().decode(secret);
            if (keyBytes.length < 32) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        // If key is less than 32 bytes (256 bits), pad with standard hash
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationHours * 60 * 60 * 1000L;
    }

    public String generateToken(int officerID, String username, String email, String role, int userType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(officerID))
                .claims(Map.of(
                        "officerID", officerID,
                        "username", username,
                        "email", email != null ? email : "",
                        "role", role != null ? role : "Officer",
                        "userType", userType
                ))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser validateAndExtractUser(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            int officerID = ((Number) claims.get("officerID")).intValue();
            String username = claims.get("username", String.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            int userType = ((Number) claims.get("userType")).intValue();

            return new AuthenticatedUser(officerID, username, email, role, userType);
        } catch (Exception e) {
            return null;
        }
    }
}
