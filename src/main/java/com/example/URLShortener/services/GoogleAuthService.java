package com.example.URLShortener.services;

import com.example.URLShortener.dto.AuthResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.twofactor.TwoFactorService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_LEGACY = "accounts.google.com";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;

    @Value("${google.client-id:}")
    private String googleClientId;

    private volatile GoogleIdTokenVerifier verifier;

    @Transactional
    public AuthResponse authenticate(String credential) {
        GoogleIdToken.Payload payload = verify(credential);

        String subject = payload.getSubject();
        String email = normalize(payload.getEmail());
        String name = claim(payload, "name");
        String picture = claim(payload, "picture");

        User user = userRepository.findByGoogleSubject(subject)
                .orElseGet(() -> findOrCreateByVerifiedEmail(email, subject, name, picture));

        if (name != null && !name.isBlank() &&
                (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(name);
        }

        if ((user.getProfileImageUrl() == null || user.getProfileImageUrl().isBlank())
                && picture != null && !picture.isBlank()) {
            user.setProfileImageUrl(picture);
        }

        // Keep LOCAL for a local account that has Google linked.
        user.setGoogleSubject(subject);

        User saved = userRepository.save(user);

        // Google is the first factor; Trimly 2FA remains the second factor.
        if (twoFactorService.isEnabled(saved)) {
            return AuthResponse.builder()
                    .requiresTwoFactor(true)
                    .challengeToken(twoFactorService.createLoginChallenge(saved))
                    .build();
        }

        return AuthResponse.builder()
                .token(jwtService.generateToken(saved.getEmail(), saved.getTokenVersion()))
                .tokenType("Bearer")
                .userId(saved.getId())
                .email(saved.getEmail())
                .requiresTwoFactor(false)
                .build();
    }

    private User findOrCreateByVerifiedEmail(
            String email,
            String subject,
            String name,
            String picture) {

        return userRepository.findByEmail(email)
                .map(existing -> {
                    String linkedSubject = existing.getGoogleSubject();
                    if (linkedSubject != null && !linkedSubject.equals(subject)) {
                        throw new GoogleAuthenticationException(
                                "This Trimly account is already linked to another Google identity"
                        );
                    }
                    existing.setGoogleSubject(subject);
                    return existing;
                })
                .orElseGet(() -> {
                    User user = User.builder()
                            .email(email)
                            .fullName(name)
                            .profileImageUrl(picture)
                            .passwordHash(null)
                            .authProvider("GOOGLE")
                            .googleSubject(subject)
                            .tokenVersion(0L)
                            .defaultLinkExpiration("never")
                            .build();

                    try {
                        return userRepository.saveAndFlush(user);
                    } catch (DataIntegrityViolationException e) {
                        return userRepository.findByGoogleSubject(subject)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private GoogleIdToken.Payload verify(String credential) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new GoogleAuthenticationException("Google sign-in is not configured");
        }

        try {
            GoogleIdToken token = getVerifier().verify(credential);

            if (token == null) {
                throw new GoogleAuthenticationException("Invalid Google credential");
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String issuer = payload.getIssuer();

            if (!GOOGLE_ISSUER.equals(issuer) && !GOOGLE_ISSUER_LEGACY.equals(issuer)) {
                throw new GoogleAuthenticationException("Invalid Google token issuer");
            }

            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new GoogleAuthenticationException("Google email is not verified");
            }

            if (payload.getSubject() == null || payload.getEmail() == null) {
                throw new GoogleAuthenticationException("Google credential is missing identity claims");
            }

            return payload;
        } catch (GeneralSecurityException | IOException e) {
            throw new GoogleAuthenticationException("Unable to verify Google credential", e);
        }
    }

    private GoogleIdTokenVerifier getVerifier() throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier current = verifier;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = verifier;
            if (current == null) {
                current = new GoogleIdTokenVerifier.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JacksonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build();
                verifier = current;
            }
            return current;
        }
    }

    private String claim(GoogleIdToken.Payload payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static class GoogleAuthenticationException extends RuntimeException {
        public GoogleAuthenticationException(String message) {
            super(message);
        }

        public GoogleAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
