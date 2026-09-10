package com.example.URLShortener.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                /*
                 * CORS must be processed before Spring Security
                 * authentication/authorization.
                 */
                .cors(cors -> {})

                /*
                 * JWT-based stateless API.
                 *
                 * CSRF protection is disabled because the application
                 * does not use server-side sessions for authentication.
                 */
                .csrf(csrf ->
                        csrf.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        /*
                         * Spring MVC / Spring Boot internal ERROR dispatches.
                         *
                         * Spring Security authorizes ERROR dispatches
                         * separately from the original request.
                         *
                         * Without this rule, an endpoint that produces
                         * an error can be converted into HTTP 403 by the
                         * final anyRequest().denyAll() rule.
                         */
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        )
                        .permitAll()

                        /*
                         * Browser CORS preflight.
                         *
                         * OPTIONS requests do not contain the JWT
                         * that the actual request may contain.
                         */
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()

                        /*
                         * Public authentication endpoints.
                         */
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login"
                        )
                        .permitAll()

                        /*
                         * Authenticated authentication endpoints.
                         */
                        .requestMatchers(
                                "/api/auth/logout",
                                "/api/auth/change-password"
                        )
                        .authenticated()

                        /*
                         * Read authenticated profile.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        )
                        .authenticated()

                        /*
                         * Update authenticated profile.
                         */
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/auth/me"
                        )
                        .authenticated()

                        /*
                         * Public redirect endpoint.
                         *
                         * Example:
                         * GET /api/urls/abc123
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/urls/*"
                        )
                        .permitAll()

                        /*
                         * Private URL management.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/urls"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/urls"
                        )
                        .authenticated()

                        /*
                         * Private URL details.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/urls/*/details"
                        )
                        .authenticated()

                        /*
                         * Private per-link analytics.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/urls/*/analytics"
                        )
                        .authenticated()

                        /*
                         * Private aggregate analytics.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/analytics"
                        )
                        .authenticated()

                        /*
                         * Legacy server-side UI.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/shorten"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/stats/**"
                        )
                        .authenticated()

                        /*
                         * Health and framework error handling.
                         */
                        .requestMatchers(
                                "/actuator/health",
                                "/error"
                        )
                        .permitAll()

                        /*
                         * Everything else is denied.
                         */
                        .anyRequest()
                        .denyAll()
                )

                /*
                 * Process JWT authentication before Spring Security's
                 * UsernamePasswordAuthenticationFilter.
                 */
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}