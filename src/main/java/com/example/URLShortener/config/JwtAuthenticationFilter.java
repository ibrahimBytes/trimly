package com.example.URLShortener.config;

import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.services.JwtService;
import com.example.URLShortener.services.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {

            filterChain.doFilter(request, response);
            return;
        }

        String token =
                authorizationHeader
                        .substring(BEARER_PREFIX.length())
                        .trim();

        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateRequest(token, request);
        } catch (Exception ignored) {
            /*
             * Invalid, malformed, expired, revoked, or otherwise
             * unusable JWTs must not establish authentication.
             *
             * Protected endpoints will subsequently be rejected
             * by Spring Security.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateRequest(
            String token,
            HttpServletRequest request
    ) {

        if (SecurityContextHolder
                .getContext()
                .getAuthentication() != null) {

            return;
        }

        String email =
                jwtService.extractEmail(token);

        if (email == null || email.isBlank()) {
            return;
        }

        Optional<User> userOptional =
                userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        boolean valid =
                jwtService.isTokenValid(
                        token,
                        email,
                        user.getTokenVersion()
                );

        if (!valid) {
            return;
        }

        /*
         * Access tokens created before Active Sessions was introduced
         * do not have a session/JTI that can be tracked server-side.
         *
         * Such legacy tokens remain valid according to the existing
         * JWT + tokenVersion rules until they expire.
         *
         * New access tokens contain a UUID JTI and therefore must have
         * a valid, non-revoked server-side session.
         */
        valid = validateSessionIfPresent(
                token,
                user,
                request
        );

        if (!valid) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    private boolean validateSessionIfPresent(
            String token,
            User user,
            HttpServletRequest request
    ) {

        try {
            UUID sessionId =
                    jwtService.extractSessionId(token);

            var expiration =
                    jwtService.extractExpiration(token);

            return sessionService.validateAndTouch(
                    user,
                    sessionId,
                    expiration,
                    request
            );

        } catch (IllegalArgumentException e) {
            /*
             * No valid session ID means this is a legacy access token.
             *
             * JwtService.isTokenValid(...) has already verified:
             * - signature
             * - subject/email
             * - tokenVersion
             * - expiration
             *
             * Therefore a legacy token can continue to authenticate.
             */
            return true;
        }
    }
}