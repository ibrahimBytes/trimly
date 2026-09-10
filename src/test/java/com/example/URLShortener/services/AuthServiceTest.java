package com.example.URLShortener.services;

import com.example.URLShortener.dto.ChangePasswordRequest;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {

        authService =
                new AuthService(
                        userRepository,
                        passwordEncoder,
                        jwtService
                );

        user =
                User.builder()
                        .id(1)
                        .email("test@example.com")
                        .passwordHash("old-hash")
                        .tokenVersion(5L)
                        .build();
    }

    @Test
    void changePassword_updatesPasswordAndInvalidatesTokens() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        when(passwordEncoder.matches(
                "old-password",
                "old-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "new-password",
                "old-hash"
        )).thenReturn(false);

        when(passwordEncoder.encode(
                "new-password"
        )).thenReturn("new-hash");

        authService.changePassword(
                user,
                request
        );

        assertEquals(
                "new-hash",
                user.getPasswordHash()
        );

        assertEquals(
                6L,
                user.getTokenVersion()
        );

        verify(passwordEncoder)
                .encode("new-password");

        verify(userRepository)
                .save(user);
    }

    @Test
    void changePassword_rejectsIncorrectCurrentPassword() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password");

        when(passwordEncoder.matches(
                "wrong-password",
                "old-hash"
        )).thenReturn(false);

        assertThrows(
                AuthService.InvalidCurrentPasswordException.class,
                () ->
                        authService.changePassword(
                                user,
                                request
                        )
        );

        assertEquals(
                "old-hash",
                user.getPasswordHash()
        );

        assertEquals(
                5L,
                user.getTokenVersion()
        );

        verify(passwordEncoder, never())
                .encode(any());

        verify(userRepository, never())
                .save(any());
    }

    @Test
    void changePassword_rejectsSamePassword() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword("old-password");
        request.setNewPassword("old-password");

        when(passwordEncoder.matches(
                "old-password",
                "old-hash"
        )).thenReturn(true);

        assertThrows(
                AuthService.SamePasswordException.class,
                () ->
                        authService.changePassword(
                                user,
                                request
                        )
        );

        assertEquals(
                "old-hash",
                user.getPasswordHash()
        );

        assertEquals(
                5L,
                user.getTokenVersion()
        );

        verify(passwordEncoder, never())
                .encode(any());

        verify(userRepository, never())
                .save(any());
    }

    @Test
    void changePassword_doesNotGenerateOrReturnNewToken() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        when(passwordEncoder.matches(
                "old-password",
                "old-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "new-password",
                "old-hash"
        )).thenReturn(false);

        when(passwordEncoder.encode(
                "new-password"
        )).thenReturn("new-hash");

        authService.changePassword(
                user,
                request
        );

        verifyNoInteractions(jwtService);
    }

    @Test
    void changePassword_savesOnlyAfterPasswordValidationSucceeds() {

        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        when(passwordEncoder.matches(
                "old-password",
                "old-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "new-password",
                "old-hash"
        )).thenReturn(false);

        when(passwordEncoder.encode(
                "new-password"
        )).thenReturn("new-hash");

        authService.changePassword(
                user,
                request
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(userCaptor.capture());

        User savedUser =
                userCaptor.getValue();

        assertEquals(
                "new-hash",
                savedUser.getPasswordHash()
        );

        assertEquals(
                6L,
                savedUser.getTokenVersion()
        );
    }
}
