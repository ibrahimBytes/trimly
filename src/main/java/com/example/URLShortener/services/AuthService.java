package com.example.URLShortener.services;

import com.example.URLShortener.dto.AuthResponse;
import com.example.URLShortener.dto.ChangePasswordRequest;
import com.example.URLShortener.dto.LoginRequest;
import com.example.URLShortener.dto.RegisterRequest;
import com.example.URLShortener.dto.ProfileUpdateRequest;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public static class InvalidCurrentPasswordException
            extends RuntimeException {

        public InvalidCurrentPasswordException(
                String message) {

            super(message);
        }
    }

    public static class SamePasswordException
            extends RuntimeException {

        public SamePasswordException(
                String message) {

            super(message);
        }
    }

    @Transactional
    public AuthResponse register(
            RegisterRequest request) {

        String email =
                normalizeEmail(
                        request.getEmail()
                );

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(
                    "An account with this email already exists"
            );
        }

        User user =
                User.builder()
                        .email(email)
                        .passwordHash(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )
                        .tokenVersion(0L)
                        .build();

        final User savedUser;

        try {

            savedUser =
                    userRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException e) {

            throw new EmailAlreadyExistsException(
                    "An account with this email already exists"
            );
        }

        String token =
                jwtService.generateToken(
                        savedUser.getEmail(),
                        savedUser.getTokenVersion()
                );

        return buildAuthResponse(
                savedUser,
                token
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(
            LoginRequest request) {

        String email =
                normalizeEmail(
                        request.getEmail()
                );

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new InvalidCredentialsException(
                                        "Invalid email or password"
                                )
                        );

        if (user.getPasswordHash() == null) {
            throw new InvalidCredentialsException(
                    "This account uses Google sign-in"
            );
        }

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                );

        if (!passwordMatches) {

            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        String token =
                jwtService.generateToken(
                        user.getEmail(),
                        user.getTokenVersion()
                );

        return buildAuthResponse(
                user,
                token
        );
    }

    /**
     * Invalidates all currently issued JWTs for this user.
     *
     * The browser may delete its token immediately, but the
     * database remains the server-side source of truth.
     */
    @Transactional
    public void logout(
            User user) {

        user.setTokenVersion(
                user.getTokenVersion() + 1
        );

        userRepository.save(
                user
        );
    }

    @Transactional
    public void changePassword(
            User user,
            ChangePasswordRequest request) {

        if (user.getPasswordHash() == null) {
            throw new InvalidCurrentPasswordException(
                    "This account uses Google sign-in"
            );
        }

        boolean currentPasswordMatches =
                passwordEncoder.matches(
                        request.getCurrentPassword(),
                        user.getPasswordHash()
                );

        if (!currentPasswordMatches) {

            throw new InvalidCurrentPasswordException(
                    "Current password is incorrect"
            );
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPasswordHash()
        )) {

            throw new SamePasswordException(
                    "New password must be different from the current password"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        /*
         * Changing the password invalidates every JWT that
         * was issued before this point.
         */
        user.setTokenVersion(
                user.getTokenVersion() + 1
        );

        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(
            User user,
            String token) {

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

    private String normalizeEmail(
            String email) {

        return email
                .trim()
                .toLowerCase();
    }

    public static class EmailAlreadyExistsException
            extends RuntimeException {

        public EmailAlreadyExistsException(
                String message) {

            super(message);
        }
    }

    public static class InvalidCredentialsException
            extends RuntimeException {

        public InvalidCredentialsException(
                String message) {

            super(message);
        }
    }

    @Transactional
    public User updateProfile(
            User user,
            ProfileUpdateRequest request
    ) {

        String fullName =
                request.getFullName().trim();

        user.setFullName(fullName);

        return userRepository.save(user);
    }


    // ================================================================
    // LINK DEFAULTS
    // ================================================================

    private static final java.util.Set<String> VALID_DEFAULT_LINK_EXPIRATIONS =
            java.util.Set.of(
                    "never",
                    "7d",
                    "30d",
                    "90d",
                    "1y"
            );

    public String getDefaultLinkExpiration(User user) {
        String value = user.getDefaultLinkExpiration();

        if (value == null || value.isBlank()) {
            return "never";
        }

        return value;
    }

    @Transactional
    public User updateDefaultLinkExpiration(
            User user,
            String defaultLinkExpiration
    ) {
        String value =
                defaultLinkExpiration == null
                        ? ""
                        : defaultLinkExpiration.trim().toLowerCase(
                                java.util.Locale.ROOT
                        );

        if (!VALID_DEFAULT_LINK_EXPIRATIONS.contains(value)) {
            throw new InvalidLinkDefaultException(
                    "Invalid default link expiration"
            );
        }

        user.setDefaultLinkExpiration(value);

        return userRepository.save(user);
    }

    public static class InvalidLinkDefaultException
            extends RuntimeException {

        public InvalidLinkDefaultException(String message) {
            super(message);
        }
    }

}
