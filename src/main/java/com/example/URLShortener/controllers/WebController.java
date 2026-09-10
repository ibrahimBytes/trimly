package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AnalyticsService;
import com.example.URLShortener.services.UrlService;
import com.example.URLShortener.services.UrlService.AliasAlreadyExistsException;
import com.example.URLShortener.services.UrlService.UrlNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute(
                "urlRequest",
                new URLRequest()
        );

        return "index";
    }

    @PostMapping("/shorten")
    public String shortenUrl(
            @Valid @ModelAttribute URLRequest urlRequest,
            BindingResult bindingResult,
            Model model,
            Authentication authentication) {

        model.addAttribute(
                "urlRequest",
                urlRequest
        );

        if (bindingResult.hasErrors()) {

            model.addAttribute(
                    "error",
                    bindingResult
                            .getAllErrors()
                            .get(0)
                            .getDefaultMessage()
            );

            return "index";
        }

        User owner =
                getAuthenticatedUser(authentication);

        try {

            URLResponse response =
                    urlService.createShortUrl(
                            urlRequest,
                            owner
                    );

            model.addAttribute(
                    "result",
                    response
            );

            model.addAttribute(
                    "statsUrl",
                    "/stats/" + response.getShortCode()
            );

        } catch (AliasAlreadyExistsException e) {

            model.addAttribute(
                    "error",
                    "Custom alias already taken. Please choose a different one."
            );

        } catch (Exception e) {

            model.addAttribute(
                    "error",
                    "Something went wrong: " + e.getMessage()
            );
        }

        return "index";
    }

    @GetMapping("/stats/{shortCode}")
    public String viewStats(
            @PathVariable("shortCode") String shortCode,
            Model model,
            Authentication authentication) {

        User owner =
                getAuthenticatedUser(authentication);

        try {

            model.addAttribute(
                    "stats",
                    analyticsService.getStats(
                            shortCode,
                            owner
                    )
            );

            return "analytics";

        } catch (UrlNotFoundException e) {

            model.addAttribute(
                    "error",
                    "Unable to load statistics for this link."
            );

            return "index";

        } catch (Exception e) {

            model.addAttribute(
                    "error",
                    "Unable to load statistics for this link."
            );

            return "index";
        }
    }

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