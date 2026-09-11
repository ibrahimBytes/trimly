package com.example.URLShortener.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM =
            "tokenVersion";

    private static final String PURPOSE_CLAIM =
            "purpose";

    private static final String TWO_FACTOR_CHALLENGE_PURPOSE =
            "2fa-challenge";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMillis) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT secret must not be empty"
            );
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes long"
            );
        }

        if (expirationMillis <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be greater than zero"
            );
        }

        this.signingKey =
                Keys.hmacShaKeyFor(
                        secret.getBytes(StandardCharsets.UTF_8)
                );

        this.expiration =
                Duration.ofMillis(expirationMillis);
    }

    /**
     * Generates the normal authenticated access token.
     */
    public String generateToken(
            String email,
            long tokenVersion) {

        Instant now =
                Instant.now();

        Instant expiresAt =
                now.plus(expiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .claim(PURPOSE_CLAIM, "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a short-lived token proving that the user
     * has successfully completed the password step of login
     * but still needs to complete two-factor authentication.
     *
     * This is NOT an access token.
     */
    public String generateTwoFactorChallengeToken(
            String email,
            long tokenVersion) {

        Instant now =
                Instant.now();

        Instant expiresAt =
                now.plus(Duration.ofMinutes(5));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .claim(PURPOSE_CLAIM, TWO_FACTOR_CHALLENGE_PURPOSE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Determines whether the supplied JWT is a valid,
     * unexpired two-factor challenge token.
     */
    public boolean isTwoFactorChallengeToken(
            String token) {

        try {
            Object purpose =
                    extractAllClaims(token)
                            .get(PURPOSE_CLAIM);

            return TWO_FACTOR_CHALLENGE_PURPOSE.equals(purpose)
                    && !isTokenExpired(token);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses and validates a two-factor challenge token.
     */
    public TwoFactorChallenge parseTwoFactorChallenge(
            String token) {

        Claims claims =
                extractAllClaims(token);

        Object purpose =
                claims.get(PURPOSE_CLAIM);

        if (!TWO_FACTOR_CHALLENGE_PURPOSE.equals(purpose)
                || claims.getExpiration().before(new Date())) {

            throw new IllegalArgumentException(
                    "Invalid or expired two-factor challenge"
            );
        }

        Number version =
                claims.get(
                        TOKEN_VERSION_CLAIM,
                        Number.class
                );

        if (claims.getSubject() == null
                || version == null
                || claims.getId() == null) {

            throw new IllegalArgumentException(
                    "Invalid two-factor challenge"
            );
        }

        return new TwoFactorChallenge(
                claims.getSubject(),
                version.longValue(),
                claims.getId()
        );
    }

    /**
     * Data extracted from a two-factor challenge token.
     */
    public record TwoFactorChallenge(
            String email,
            long tokenVersion,
            String id) {
    }

    /**
     * Backward-compatible convenience method.
     */
    public String generateToken(
            String email) {

        return generateToken(
                email,
                0L
        );
    }

    public String extractEmail(
            String token) {

        return extractAllClaims(token)
                .getSubject();
    }

    public long extractTokenVersion(
            String token) {

        Number value =
                extractAllClaims(token)
                        .get(
                                TOKEN_VERSION_CLAIM,
                                Number.class
                        );

        /*
         * Tokens issued before tokenVersion existed are treated
         * as version 0.
         */
        return value == null
                ? 0L
                : value.longValue();
    }

    public UUID extractSessionId(String token) {
        String id = extractAllClaims(token).getId();
        if (id == null) throw new IllegalArgumentException("Access token has no session id");
        return UUID.fromString(id);
    }

    public Date extractExpiration(String token) { return extractAllClaims(token).getExpiration(); }

    public boolean isTokenValid(
            String token,
            String email,
            long expectedTokenVersion) {

        try {

            String tokenEmail =
                    extractEmail(token);

            long tokenVersion =
                    extractTokenVersion(token);

            return tokenEmail.equals(email)
                    && tokenVersion == expectedTokenVersion
                    && !isTokenExpired(token);

        } catch (Exception e) {

            return false;
        }
    }

    /**
     * Backward-compatible validation method.
     */
    public boolean isTokenValid(
            String token,
            String email) {

        try {

            String tokenEmail =
                    extractEmail(token);

            return tokenEmail.equals(email)
                    && !isTokenExpired(token);

        } catch (Exception e) {

            return false;
        }
    }

    public boolean isTokenExpired(
            String token) {

        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractAllClaims(
            String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
