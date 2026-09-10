package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.AuthResponse;
import com.example.URLShortener.dto.GoogleLoginRequest;
import com.example.URLShortener.dto.ChangePasswordRequest;
import com.example.URLShortener.dto.LoginRequest;
import com.example.URLShortener.dto.RegisterRequest;
import com.example.URLShortener.dto.UserResponse;
import com.example.URLShortener.dto.ProfileUpdateRequest;
import com.example.URLShortener.dto.LinkDefaultsResponse;
import com.example.URLShortener.dto.LinkDefaultsUpdateRequest;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AuthService;
import com.example.URLShortener.services.GoogleAuthService;
import com.example.URLShortener.services.GoogleAuthService.GoogleAuthenticationException;
import com.example.URLShortener.services.AuthService.EmailAlreadyExistsException;
import com.example.URLShortener.services.AuthService.InvalidCredentialsException;
import com.example.URLShortener.services.AuthService.InvalidCurrentPasswordException;
import com.example.URLShortener.services.AuthService.SamePasswordException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        try {

            AuthResponse response =
                    authService.register(request);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (EmailAlreadyExistsException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        try {

            AuthResponse response =
                    authService.login(request);

            return ResponseEntity.ok(response);

        } catch (InvalidCredentialsException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(
            @Valid @RequestBody GoogleLoginRequest request) {

        try {
            return ResponseEntity.ok(
                    googleAuthService.authenticate(request.getCredential())
            );
        } catch (GoogleAuthenticationException e) {
            System.err.println("Google authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        authService.logout(user);

        return ResponseEntity
                .noContent()
                .build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        try {

            authService.changePassword(
                    user,
                    request
            );

            return ResponseEntity
                    .noContent()
                    .build();

        } catch (InvalidCurrentPasswordException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();

        } catch (SamePasswordException e) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            Authentication authentication) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        UserResponse response =
                UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .createdAt(user.getCreatedAt())
                        .defaultLinkExpiration(user.getDefaultLinkExpiration())
                        .authProvider(user.getAuthProvider())
                        .hasPassword(user.getPasswordHash() != null)
                        .build();

        return ResponseEntity.ok(response);
    }

    private User getAuthenticatedUser(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            throw new IllegalStateException(
                    "Authenticated principal is not a User"
            );
        }

        return user;
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        User updatedUser =
                authService.updateProfile(
                        user,
                        request
                );

        UserResponse response =
                UserResponse.builder()
                        .id(updatedUser.getId())
                        .email(updatedUser.getEmail())
                        .fullName(updatedUser.getFullName())
                        .profileImageUrl(updatedUser.getProfileImageUrl())
                        .createdAt(updatedUser.getCreatedAt())
                        .defaultLinkExpiration(updatedUser.getDefaultLinkExpiration())
                        .authProvider(updatedUser.getAuthProvider())
                        .hasPassword(updatedUser.getPasswordHash() != null)
                        .build();

        return ResponseEntity.ok(response);
    }


    // ================================================================
    // LINK DEFAULTS
    // ================================================================

    @GetMapping("/me/link-defaults")
    public ResponseEntity<LinkDefaultsResponse> getLinkDefaults(
            Authentication authentication) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        return ResponseEntity.ok(
                LinkDefaultsResponse.builder()
                        .defaultLinkExpiration(
                                authService.getDefaultLinkExpiration(user)
                        )
                        .build()
        );
    }

    @PatchMapping("/me/link-defaults")
    public ResponseEntity<LinkDefaultsResponse> updateLinkDefaults(
            Authentication authentication,
            @Valid @RequestBody LinkDefaultsUpdateRequest request) {

        User user =
                getAuthenticatedUser(
                        authentication
                );

        try {
            User updatedUser =
                    authService.updateDefaultLinkExpiration(
                            user,
                            request.getDefaultLinkExpiration()
                    );

            return ResponseEntity.ok(
                    LinkDefaultsResponse.builder()
                            .defaultLinkExpiration(
                                    updatedUser.getDefaultLinkExpiration()
                            )
                            .build()
            );

        } catch (AuthService.InvalidLinkDefaultException e) {
            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }

}
