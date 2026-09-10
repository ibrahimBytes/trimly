package com.example.URLShortener.controllers;

import com.example.URLShortener.config.KafkaConfig;
import com.example.URLShortener.dto.AnalyticsResponse;
import com.example.URLShortener.dto.ClickEventMessage;
import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AnalyticsService;
import com.example.URLShortener.services.UrlService;
import com.example.URLShortener.services.UrlService.AliasAlreadyExistsException;
import com.example.URLShortener.services.UrlService.UrlExpiredException;
import com.example.URLShortener.services.UrlService.UrlNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class urlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Get recent URLs
    // -------------------------------------------------------------------------

    /**
     * Get the most recently created short URLs owned by the
     * currently authenticated user.
     *
     * GET /api/urls
     *
     * Requires authentication.
     */
    @GetMapping
    public ResponseEntity<List<URLResponse>> getRecentUrls(
            Authentication authentication) {

        User owner =
                getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                urlService.getRecentUrls(owner)
        );
    }

    // -------------------------------------------------------------------------
    // Analytics for one short URL
    // -------------------------------------------------------------------------

    /**
     * Get analytics for a short URL owned by the
     * currently authenticated user.
     *
     * GET /api/urls/{shortUrl}/analytics
     *
     * Requires authentication.
     */
    @GetMapping("/{shortUrl}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable("shortUrl") String shortUrl,
            Authentication authentication) {

        User owner =
                getAuthenticatedUser(authentication);

        try {

            AnalyticsResponse response =
                    analyticsService.getStats(
                            shortUrl,
                            owner
                    );

            return ResponseEntity.ok(response);

        } catch (UrlNotFoundException e) {

            /*
             * Do not reveal whether the short URL belongs to
             * another user. Return 404.
             */
            log.debug(
                    "Short URL not found for current owner while loading analytics: {}",
                    shortUrl
            );

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // URL details
    // -------------------------------------------------------------------------

    /**
     * Get details for a short URL owned by the
     * currently authenticated user.
     * <p>
     * GET /api/urls/{shortUrl}/details
     * <p></p>
     * Requires authentication.
     */
    @GetMapping("/{shortUrl}/details")
    public ResponseEntity<URLResponse> getUrlDetails(
            @PathVariable("shortUrl") String shortUrl,
            Authentication authentication) {

        User owner =
                getAuthenticatedUser(authentication);

        try {

            URLResponse response =
                    urlService.getUrlDetails(
                            shortUrl,
                            owner
                    );

            return ResponseEntity.ok(response);

        } catch (UrlNotFoundException e) {

            /*
             * Return 404 both when the URL does not exist and when
             * it exists but belongs to another user.
             */
            log.debug(
                    "Short URL not found for current owner while loading details: {}",
                    shortUrl
            );

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Public redirect
    // -------------------------------------------------------------------------

    /**
     * Resolve a short URL and redirect to the original destination.
     * <p>
     * GET /api/urls/{shortUrl}
     * <p>
     * This endpoint is intentionally PUBLIC.
     * <p>
     * Ownership must NOT be checked here because anyone with a
     * valid short URL should be able to follow it.
     * <p>
     * A click event is published to Kafka asynchronously so that
     * analytics persistence does not block the redirect.
     */
    @GetMapping("/{shortUrl}")
    public ResponseEntity<Void> getLongURLByShortURL(
            @NotNull @PathVariable("shortUrl") String shortUrl,
            HttpServletRequest request) {

        try {

            String longUrl =
                    urlService.resolveLongUrl(shortUrl);

            /*
             * Publish analytics event asynchronously.
             *
             * Kafka failure must never prevent the user from
             * being redirected to the destination URL.
             */
            try {

                ClickEventMessage clickEvent =
                        ClickEventMessage.builder()
                                .shortUrl(shortUrl)
                                .ipAddress(
                                        resolveClientIp(request)
                                )
                                .userAgent(
                                        request.getHeader("User-Agent")
                                )
                                .clickedAt(
                                        LocalDateTime.now()
                                )
                                .build();

                String json =
                        objectMapper.writeValueAsString(
                                clickEvent
                        );

                kafkaTemplate.send(
                        KafkaConfig.CLICK_EVENTS_TOPIC,
                        shortUrl,
                        json
                );

            } catch (Exception e) {

                log.warn(
                        "Failed to publish click event to Kafka for shortUrl={}: {}",
                        shortUrl,
                        e.getMessage()
                );
            }

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(longUrl))
                    .build();

        } catch (UrlExpiredException e) {

            log.debug(
                    "Short URL expired: {}",
                    shortUrl
            );

            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .build();

        } catch (UrlNotFoundException e) {

            log.debug(
                    "Short URL not found: {}",
                    shortUrl
            );

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Create short URL
    // -------------------------------------------------------------------------

    /**
     * Create a new short URL owned by the currently authenticated user.
     *
     * POST /api/urls
     *
     * Requires authentication.
     */
    @PostMapping
    public ResponseEntity<URLResponse> createShortURL(
            @Valid @RequestBody URLRequest urlRequest,
            Authentication authentication) {

        User owner =
                getAuthenticatedUser(authentication);

        try {

            URLResponse response =
                    urlService.createShortUrl(
                            urlRequest,
                            owner
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (AliasAlreadyExistsException e) {

            log.debug(
                    "Custom alias already exists: {}",
                    urlRequest.getCustomAlias()
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                            URLResponse.builder()
                                    .shortUrl("error")
                                    .build()
                    );
        }
    }

    // -------------------------------------------------------------------------
    // Authentication helper
    // -------------------------------------------------------------------------

    /**
     * Extract the authenticated User from Spring Security.
     *
     * JwtAuthenticationFilter stores the User entity as the
     * Authentication principal.
     */
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

    // -------------------------------------------------------------------------
    // Client IP
    // -------------------------------------------------------------------------

    /**
     * Resolve the real client IP address.
     *
     * When the application is behind a reverse proxy or load balancer,
     * X-Forwarded-For may contain the original client address.
     */
    private String resolveClientIp(
            HttpServletRequest request) {

        String xForwardedFor =
                request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null
                && !xForwardedFor.isEmpty()
                && !"unknown".equalsIgnoreCase(
                xForwardedFor
        )) {

            return xForwardedFor
                    .split(",")[0]
                    .trim();
        }

        return request.getRemoteAddr();
    }
}