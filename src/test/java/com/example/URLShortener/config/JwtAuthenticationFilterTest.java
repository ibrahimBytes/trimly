package com.example.URLShortener.config;

import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.services.JwtService;
import com.example.URLShortener.services.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

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
        filter = new JwtAuthenticationFilter(
                jwtService,
                userRepository,
                sessionService
        );

        user = User.builder()
                .id(1)
                .email("test@example.com")
                .passwordHash("password-hash")
                .tokenVersion(2L)
                .build();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenWithCurrentVersion_authenticatesUser()
            throws Exception {

        String token = "valid-token";
        UUID sessionId = UUID.randomUUID();
        Date expiration =
                new Date(System.currentTimeMillis() + 60_000);

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                token,
                user.getEmail(),
                user.getTokenVersion()
        )).thenReturn(true);

        when(jwtService.extractSessionId(token))
                .thenReturn(sessionId);

        when(jwtService.extractExpiration(token))
                .thenReturn(expiration);

        when(sessionService.validateAndTouch(
                user,
                sessionId,
                expiration,
                request
        )).thenReturn(true);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(user, authentication.getPrincipal());

        verify(sessionService)
                .validateAndTouch(
                        user,
                        sessionId,
                        expiration,
                        request
                );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void revokedToken_doesNotAuthenticateUser()
            throws Exception {

        String token = "revoked-token";
        UUID sessionId = UUID.randomUUID();
        Date expiration =
                new Date(System.currentTimeMillis() + 60_000);

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                token,
                user.getEmail(),
                user.getTokenVersion()
        )).thenReturn(true);

        when(jwtService.extractSessionId(token))
                .thenReturn(sessionId);

        when(jwtService.extractExpiration(token))
                .thenReturn(expiration);

        when(sessionService.validateAndTouch(
                user,
                sessionId,
                expiration,
                request
        )).thenReturn(false);

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

        verify(sessionService)
                .validateAndTouch(
                        user,
                        sessionId,
                        expiration,
                        request
                );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void tokenVersionMismatch_doesNotAuthenticateUser()
            throws Exception {

        String token = "revoked-token";

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                token,
                user.getEmail(),
                user.getTokenVersion()
        )).thenReturn(false);

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
                        token,
                        user.getEmail(),
                        user.getTokenVersion()
                );

        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotAuthenticateUser()
            throws Exception {

        String token = "invalid-token";

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
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

        verifyNoInteractions(userRepository);
        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void expiredToken_doesNotAuthenticateUser()
            throws Exception {

        String token = "expired-token";

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                token,
                user.getEmail(),
                user.getTokenVersion()
        )).thenReturn(false);

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

        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void unknownUser_doesNotAuthenticateUser()
            throws Exception {

        String token = "unknown-user-token";

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn("unknown@example.com");

        when(userRepository.findByEmail("unknown@example.com"))
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

        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void legacyTokenWithoutSessionId_stillAuthenticates()
            throws Exception {

        String token = "legacy-token";

        givenBearerToken(token);

        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid(
                token,
                user.getEmail(),
                user.getTokenVersion()
        )).thenReturn(true);

        when(jwtService.extractSessionId(token))
                .thenThrow(
                        new IllegalArgumentException(
                                "Access token has no session id"
                        )
                );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(user, authentication.getPrincipal());

        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
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

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void nonBearerAuthorizationHeader_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Basic abc123");

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

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void emptyBearerToken_doesNotAuthenticateUser()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer ");

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

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(sessionService);

        verify(filterChain)
                .doFilter(request, response);
    }

    private void givenBearerToken(String token) {
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);
    }
}