package com.example.URLShortener.config;

import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    private User user;

    @BeforeEach
    void setUp() {

        filter =
                new JwtAuthenticationFilter(
                        jwtService,
                        userRepository
                );

        user =
                User.builder()
                        .id(1)
                        .email("test@example.com")
                        .passwordHash("password-hash")
                        .tokenVersion(2L)
                        .build();

        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenWithCurrentVersion_authenticatesUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer valid-token");

        when(jwtService.extractEmail("valid-token"))
                .thenReturn("test@example.com");

        when(jwtService.extractTokenVersion("valid-token"))
                .thenReturn(2L);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                "valid-token",
                "test@example.com",
                2L
        ))
                .thenReturn(true);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertTrue(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .isAuthenticated()
        );

        assertSame(
                user,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal()
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void revokedToken_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer old-token");

        when(jwtService.extractEmail("old-token"))
                .thenReturn("test@example.com");

        when(jwtService.extractTokenVersion("old-token"))
                .thenReturn(1L);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                "old-token",
                "test@example.com",
                2L
        ))
                .thenReturn(false);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService)
                .isTokenValid(
                        "old-token",
                        "test@example.com",
                        2L
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void tokenVersionMismatch_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer revoked-token");

        when(jwtService.extractEmail("revoked-token"))
                .thenReturn("test@example.com");

        when(jwtService.extractTokenVersion("revoked-token"))
                .thenReturn(1L);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                "revoked-token",
                "test@example.com",
                2L
        ))
                .thenReturn(false);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void missingAuthorizationHeader_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn(null);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(
                jwtService,
                userRepository
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void malformedToken_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer invalid-token");

        when(jwtService.extractEmail("invalid-token"))
                .thenThrow(
                        new IllegalArgumentException(
                                "Invalid JWT"
                        )
                );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(
                userRepository
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void unknownUser_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer valid-token");

        when(jwtService.extractEmail("valid-token"))
                .thenReturn("unknown@example.com");

        when(jwtService.extractTokenVersion("valid-token"))
                .thenReturn(2L);

        when(userRepository.findByEmail(
                "unknown@example.com"
        ))
                .thenReturn(Optional.empty());

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService, never())
                .isTokenValid(
                        anyString(),
                        anyString(),
                        anyLong()
                );
    }
}
