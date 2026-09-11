package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.ChangePasswordRequest;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AuthService;
import com.example.URLShortener.services.AccountDeletionService;
import com.example.URLShortener.services.GoogleAuthService;
import com.example.URLShortener.services.AuthService.InvalidCurrentPasswordException;
import com.example.URLShortener.services.AuthService.SamePasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private Authentication unauthenticatedAuthentication;

    private AuthController authController;

    private User user;

    private Authentication authentication;

    @BeforeEach
    void setUp() {

        authController =
                new AuthController(
                        authService,
                        googleAuthService,
                        accountDeletionService
                );

        user =
                User.builder()
                        .id(1)
                        .email("test@example.com")
                        .passwordHash("password-hash")
                        .tokenVersion(0L)
                        .build();

        /*
         * This constructor creates an authenticated
         * UsernamePasswordAuthenticationToken because
         * authorities are supplied.
         */
        authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of()
                );
    }

    @Test
    void changePassword_returns204WhenSuccessful() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "old-password"
        );

        request.setNewPassword(
                "new-password"
        );

        ResponseEntity<Void> response =
                authController.changePassword(
                        authentication,
                        request
                );

        assertEquals(
                HttpStatus.NO_CONTENT,
                response.getStatusCode()
        );

        assertNull(
                response.getBody()
        );

        verify(authService)
                .changePassword(
                        user,
                        request
                );
    }

    @Test
    void changePassword_returns401WhenCurrentPasswordIsIncorrect() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "wrong-password"
        );

        request.setNewPassword(
                "new-password"
        );

        doThrow(
                new InvalidCurrentPasswordException(
                        "Current password is incorrect"
                )
        )
                .when(authService)
                .changePassword(
                        user,
                        request
                );

        ResponseEntity<Void> response =
                authController.changePassword(
                        authentication,
                        request
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                response.getStatusCode()
        );

        assertNull(
                response.getBody()
        );

        verify(authService)
                .changePassword(
                        user,
                        request
                );
    }

    @Test
    void changePassword_returns400WhenNewPasswordIsSame() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "old-password"
        );

        request.setNewPassword(
                "old-password"
        );

        doThrow(
                new SamePasswordException(
                        "New password must be different from the current password"
                )
        )
                .when(authService)
                .changePassword(
                        user,
                        request
                );

        ResponseEntity<Void> response =
                authController.changePassword(
                        authentication,
                        request
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNull(
                response.getBody()
        );

        verify(authService)
                .changePassword(
                        user,
                        request
                );
    }

    @Test
    void changePassword_passesAuthenticatedUserToService() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "old-password"
        );

        request.setNewPassword(
                "new-password"
        );

        authController.changePassword(
                authentication,
                request
        );

        verify(authService)
                .changePassword(
                        same(user),
                        same(request)
                );
    }

    @Test
    void changePassword_doesNotCallServiceWithUnauthenticatedAuthentication() {

        when(
                unauthenticatedAuthentication
                        .isAuthenticated()
        )
                .thenReturn(false);

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "old-password"
        );

        request.setNewPassword(
                "new-password"
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        authController.changePassword(
                                unauthenticatedAuthentication,
                                request
                        )
        );

        verifyNoInteractions(
                authService
        );
    }

    @Test
    void changePassword_rejectsNonUserPrincipal() {

        Authentication authenticationWithWrongPrincipal =
                new UsernamePasswordAuthenticationToken(
                        "not-a-user",
                        null,
                        List.of()
                );

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(
                "old-password"
        );

        request.setNewPassword(
                "new-password"
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        authController.changePassword(
                                authenticationWithWrongPrincipal,
                                request
                        )
        );

        verifyNoInteractions(
                authService
        );
    }
}
