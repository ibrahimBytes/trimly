package com.example.URLShortener.controllers;

import com.example.URLShortener.dto.AnalyticsResponse;
import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AnalyticsService;
import com.example.URLShortener.services.UrlService;
import com.example.URLShortener.services.UrlService.AliasAlreadyExistsException;
import com.example.URLShortener.services.UrlService.UrlNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebControllerTest {

    private UrlService urlService;
    private AnalyticsService analyticsService;
    private Model model;
    private BindingResult bindingResult;
    private WebController webController;
    private Authentication authentication;

    private User testUser;

    @BeforeEach
    void setUp() {
        urlService = mock(UrlService.class);
        analyticsService = mock(AnalyticsService.class);
        model = mock(Model.class);
        bindingResult = mock(BindingResult.class);

        testUser = User.builder()
                .id(1)
                .email("test@example.com")
                .build();

        authentication =
                new UsernamePasswordAuthenticationToken(
                        testUser,
                        null,
                        Collections.emptyList()
                );

        webController =
                new WebController(
                        urlService,
                        analyticsService
                );
    }

    @Test
    void index_addsEmptyRequestToModel() {
        String view =
                webController.index(model);

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        eq("urlRequest"),
                        any(URLRequest.class)
                );
    }

    @Test
    void shortenUrl_createsUrlForAuthenticatedUser() {
        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        URLResponse response =
                URLResponse.builder()
                        .shortCode("abc")
                        .shortUrl(
                                "http://localhost:8080/abc"
                        )
                        .longUrl(
                                "https://example.com"
                        )
                        .active(true)
                        .build();

        when(bindingResult.hasErrors())
                .thenReturn(false);

        when(urlService.createShortUrl(
                request,
                testUser
        )).thenReturn(response);

        String view =
                webController.shortenUrl(
                        request,
                        bindingResult,
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        "urlRequest",
                        request
                );

        verify(model)
                .addAttribute(
                        "result",
                        response
                );

        verify(model)
                .addAttribute(
                        "statsUrl",
                        "/stats/abc"
                );

        verify(urlService)
                .createShortUrl(
                        request,
                        testUser
                );
    }

    @Test
    void shortenUrl_returnsIndexWhenValidationFails() {
        URLRequest request =
                new URLRequest();

        when(bindingResult.hasErrors())
                .thenReturn(true);

        when(bindingResult.getAllErrors())
                .thenReturn(
                        Collections.singletonList(
                                new ObjectError(
                                        "urlRequest",
                                        "Validation failed"
                                )
                        )
                );

        String view =
                webController.shortenUrl(
                        request,
                        bindingResult,
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        "error",
                        "Validation failed"
                );

        verifyNoInteractions(urlService);
    }

    @Test
    void shortenUrl_handlesAliasConflict() {
        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        when(bindingResult.hasErrors())
                .thenReturn(false);

        when(urlService.createShortUrl(
                request,
                testUser
        ))
        .thenThrow(
                new AliasAlreadyExistsException(
                        "Alias already exists"
                )
        );

        String view =
                webController.shortenUrl(
                        request,
                        bindingResult,
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        eq("error"),
                        anyString()
                );
    }

    @Test
    void shortenUrl_handlesUnexpectedException() {
        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        when(bindingResult.hasErrors())
                .thenReturn(false);

        when(urlService.createShortUrl(
                request,
                testUser
        ))
        .thenThrow(
                new RuntimeException("Database failure")
        );

        String view =
                webController.shortenUrl(
                        request,
                        bindingResult,
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        eq("error"),
                        contains("Database failure")
                );
    }

    @Test
    void viewStats_returnsAnalyticsForAuthenticatedOwner() {
        AnalyticsResponse stats =
                AnalyticsResponse.builder()
                        .shortUrl("abc")
                        .totalClicks(10)
                        .recentClicks(Collections.emptyList())
                        .build();

        when(analyticsService.getStats(
                "abc",
                testUser
        )).thenReturn(stats);

        String view =
                webController.viewStats(
                        "abc",
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("analytics");

        verify(model)
                .addAttribute(
                        "stats",
                        stats
                );

        verify(analyticsService)
                .getStats(
                        "abc",
                        testUser
                );
    }

    @Test
    void viewStats_returnsIndexWhenUrlDoesNotBelongToOwner() {
        when(analyticsService.getStats(
                "private",
                testUser
        ))
        .thenThrow(
                new UrlNotFoundException(
                        "Short URL not found"
                )
        );

        String view =
                webController.viewStats(
                        "private",
                        model,
                        authentication
                );

        assertThat(view)
                .isEqualTo("index");

        verify(model)
                .addAttribute(
                        "error",
                        "Unable to load statistics for this link."
                );
    }
}
