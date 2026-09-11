package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.AnalyticsDashboardResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Validated
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Get analytics dashboard for the currently authenticated user.
     * <p></p>
     * GET /api/analytics?days=30
     */
    @GetMapping
    public ResponseEntity<AnalyticsDashboardResponse>
    getDashboardAnalytics(
            Authentication authentication,
            @RequestParam(defaultValue = "30")
            @Min(1) @Max(90) int days) {

        User owner =
                getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                analyticsService.getDashboardAnalytics(owner, days)
        );
    }

    /**
     * Extract the authenticated User from Spring Security.
     * <
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
}
