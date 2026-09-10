package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.AuthResponse;
import com.example.URLShortener.dto.TwoFactorDisableRequest;
import com.example.URLShortener.dto.TwoFactorLoginVerifyRequest;
import com.example.URLShortener.dto.TwoFactorSetupResponse;
import com.example.URLShortener.dto.TwoFactorSetupVerifyResponse;
import com.example.URLShortener.dto.TwoFactorStatusResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.twofactor.TwoFactorService;
import com.example.URLShortener.twofactor.TwoFactorService.InvalidTwoFactorCodeException;
import com.example.URLShortener.twofactor.TwoFactorService.InvalidTwoFactorDisableCredentialsException;
import com.example.URLShortener.twofactor.TwoFactorService.TwoFactorAlreadyEnabledException;
import com.example.URLShortener.twofactor.TwoFactorService.TwoFactorNotConfiguredException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    /**
     * Returns the current 2FA status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponse> getStatus(
            Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                twoFactorService.getStatus(user)
        );
    }

    /**
     * Starts 2FA setup and returns the temporary secret and
     * otpauth URI used to generate the QR code.
     */
    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(
            Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        try {
            return ResponseEntity.ok(
                    twoFactorService.beginSetup(user)
            );

        } catch (TwoFactorAlreadyEnabledException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }
    }

    /**
     * Verifies the TOTP code entered during setup and enables 2FA.
     * Recovery codes are returned exactly once in this response.
     */
    @PostMapping("/setup/verify")
    public ResponseEntity<TwoFactorSetupVerifyResponse> verifySetup(
            Authentication authentication,
            @Valid @RequestBody TwoFactorSetupVerifyRequest request) {

        User user = getAuthenticatedUser(authentication);

        try {
            return ResponseEntity.ok(
                    twoFactorService.verifySetup(
                            user,
                            request.getCode()
                    )
            );

        } catch (TwoFactorAlreadyEnabledException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();

        } catch (InvalidTwoFactorCodeException e) {
            return ResponseEntity
                    .badRequest()
                    .build();

        } catch (TwoFactorNotConfiguredException e) {
            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }

    /**
     * Disables 2FA after validating both the current password
     * and the current TOTP code.
     */
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(
            Authentication authentication,
            @Valid @RequestBody TwoFactorDisableRequest request) {

        User user = getAuthenticatedUser(authentication);

        try {
            twoFactorService.disable(
                    user,
                    request.getPassword(),
                    request.getCode()
            );

            return ResponseEntity.noContent().build();

        } catch (InvalidTwoFactorDisableCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();

        } catch (TwoFactorNotConfiguredException e) {
            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }

    /**
     * Completes a login that requires 2FA.
     *
     * This endpoint is intentionally public at the HTTP security layer
     * because the client does not yet possess a normal access JWT.
     * The challenge token itself authenticates the pending login.
     */
    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verifyLogin(
            @Valid @RequestBody TwoFactorLoginVerifyRequest request) {

        try {
            return ResponseEntity.ok(
                    twoFactorService.verifyLoginChallenge(
                            request.getChallengeToken(),
                            request.getCode()
                    )
            );

        } catch (InvalidTwoFactorCodeException |
                 TwoFactorNotConfiguredException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    /**
     * The JwtAuthenticationFilter stores the User entity itself
     * as the Authentication principal.
     */
    private User getAuthenticatedUser(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            throw new IllegalStateException(
                    "Authenticated principal is not a User"
            );
        }

        return user;
    }
}
