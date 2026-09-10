package com.example.URLShortener.config;

import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.services.JwtService;
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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(
                        AUTHORIZATION_HEADER
                );

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(
                        BEARER_PREFIX
                )) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        String token =
                authorizationHeader
                        .substring(
                                BEARER_PREFIX.length()
                        )
                        .trim();

        if (token.isEmpty()) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        try {

            String email =
                    jwtService.extractEmail(
                            token
                    );

            long tokenVersion =
                    jwtService.extractTokenVersion(
                            token
                    );

            if (email != null
                    && SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                Optional<User> userOptional =
                        userRepository.findByEmail(
                                email
                        );

                if (userOptional.isPresent()) {

                    User user =
                            userOptional.get();

                    boolean valid =
                            jwtService.isTokenValid(
                                    token,
                                    email,
                                    user.getTokenVersion()
                            );

                    if (valid) {

                        UsernamePasswordAuthenticationToken
                                authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user,
                                        null,
                                        List.of(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_USER"
                                                )
                                        )
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request)
                        );

                        SecurityContextHolder
                                .getContext()
                                .setAuthentication(
                                        authentication
                                );
                    }
                }
            }

        } catch (Exception ignored) {

            /*
             * Invalid, malformed, expired, or revoked JWTs
             * simply do not establish authentication.
             *
             * Protected endpoints will subsequently produce
             * HTTP 401 through Spring Security.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}
