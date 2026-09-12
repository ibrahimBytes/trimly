package com.example.URLShortener.controllers;

import com.example.URLShortener.config.KafkaConfig;
import com.example.URLShortener.dto.AnalyticsResponse;
import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.services.AnalyticsService;
import com.example.URLShortener.services.UrlService;
import com.example.URLShortener.services.UrlService.AliasAlreadyExistsException;
import com.example.URLShortener.services.UrlService.UrlExpiredException;
import com.example.URLShortener.services.UrlService.UrlNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UrlControllerTest {

    private UrlService urlService;
    private AnalyticsService analyticsService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private HttpServletRequest request;
    private ObjectMapper objectMapper;
    private urlController controller;

    private User testUser;
    private Authentication authentication;


    @BeforeEach
    void setUp() {
        urlService = mock(UrlService.class);
        analyticsService = mock(AnalyticsService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        request = mock(HttpServletRequest.class);
        objectMapper = new ObjectMapper();

        testUser = User.builder()
                .id(1)
                .email("test@example.com")
                .build();

        authentication =
                new UsernamePasswordAuthenticationToken(
                        testUser,
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        controller = new urlController(
                urlService,
                analyticsService,
                kafkaTemplate,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // GET /api/urls/{shortUrl}
    // -------------------------------------------------------------------------

    @Test
    void getLongURLByShortURL_redirectsWhenFound() {
        when(urlService.resolveLongUrl("abc"))
                .thenReturn("https://example.com");

        when(request.getHeader("X-Forwarded-For"))
                .thenReturn(null);

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(request.getHeader("User-Agent"))
                .thenReturn("JUnit");

        ResponseEntity<Void> response =
                controller.getLongURLByShortURL(
                        "abc",
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FOUND);

        assertThat(response.getHeaders().getLocation())
                .hasToString("https://example.com");

        verify(kafkaTemplate)
                .send(
                        eq(KafkaConfig.CLICK_EVENTS_TOPIC),
                        eq("abc"),
                        anyString()
                );
    }

    @Test
    void getLongURLByShortURL_publishesClickEvent() {
        when(urlService.resolveLongUrl("abc"))
                .thenReturn("https://google.com");

        when(request.getHeader("X-Forwarded-For"))
                .thenReturn("192.168.1.100");

        when(request.getHeader("User-Agent"))
                .thenReturn("Mozilla/5.0");

        controller.getLongURLByShortURL(
                "abc",
                request
        );

        verify(kafkaTemplate)
                .send(
                        eq(KafkaConfig.CLICK_EVENTS_TOPIC),
                        eq("abc"),
                        contains("\"shortUrl\":\"abc\"")
                );
    }

    @Test
    void getLongURLByShortURL_returnsGoneWhenExpired() {
        doThrow(
                new UrlExpiredException("expired")
        )
        .when(urlService)
        .resolveLongUrl("expired");

        ResponseEntity<Void> response =
                controller.getLongURLByShortURL(
                        "expired",
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.GONE);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void getLongURLByShortURL_returnsNotFoundWhenMissing() {
        doThrow(
                new UrlNotFoundException("missing")
        )
        .when(urlService)
        .resolveLongUrl("missing");

        ResponseEntity<Void> response =
                controller.getLongURLByShortURL(
                        "missing",
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void getLongURLByShortURL_redirectsEvenWhenKafkaFails() {
        when(urlService.resolveLongUrl("abc"))
                .thenReturn("https://example.com");

        when(request.getHeader("X-Forwarded-For"))
                .thenReturn(null);

        when(request.getRemoteAddr())
                .thenReturn("10.0.0.1");

        when(request.getHeader("User-Agent"))
                .thenReturn("Chrome");

        when(
                kafkaTemplate.send(
                        anyString(),
                        anyString(),
                        anyString()
                )
        )
        .thenThrow(
                new RuntimeException("Kafka unavailable")
        );

        ResponseEntity<Void> response =
                controller.getLongURLByShortURL(
                        "abc",
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FOUND);

        assertThat(response.getHeaders().getLocation())
                .hasToString("https://example.com");
    }

    // -------------------------------------------------------------------------
    // GET /api/urls/{shortUrl}/analytics
    // -------------------------------------------------------------------------

    @Test
    void getAnalytics_returnsAnalyticsForCurrentOwner() {
        AnalyticsResponse analytics =
                AnalyticsResponse.builder()
                        .shortUrl("abc")
                        .totalClicks(10)
                        .recentClicks(List.of())
                        .build();

        when(analyticsService.getStats(
                "abc",
                testUser
        )).thenReturn(analytics);

        ResponseEntity<AnalyticsResponse> response =
                controller.getAnalytics("abc", authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(analytics);

        verify(analyticsService)
                .getStats(
                        "abc",
                        testUser
                );
    }

    @Test
    void getAnalytics_returnsNotFoundWhenUrlDoesNotBelongToCurrentOwner() {
        when(analyticsService.getStats(
                "private",
                testUser
        ))
        .thenThrow(
                new UrlNotFoundException("Short URL not found")
        );

        ResponseEntity<AnalyticsResponse> response =
                controller.getAnalytics("private", authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // GET /api/urls/{shortUrl}/details
    // -------------------------------------------------------------------------

    @Test
    void getUrlDetails_returnsDetailsForCurrentOwner() {
        URLResponse details =
                URLResponse.builder()
                        .shortCode("abc")
                        .shortUrl("http://localhost:8080/abc")
                        .longUrl("https://example.com")
                        .active(true)
                        .clicks(5)
                        .build();

        when(urlService.getUrlDetails(
                "abc",
                testUser
        )).thenReturn(details);

        ResponseEntity<URLResponse> response =
                controller.getUrlDetails("abc", authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(details);

        verify(urlService)
                .getUrlDetails(
                        "abc",
                        testUser
                );
    }

    @Test
    void getUrlDetails_returnsNotFoundWhenUrlDoesNotBelongToCurrentOwner() {
        when(urlService.getUrlDetails(
                "private",
                testUser
        ))
        .thenThrow(
                new UrlNotFoundException("Short URL not found")
        );

        ResponseEntity<URLResponse> response =
                controller.getUrlDetails("private", authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // GET /api/urls
    // -------------------------------------------------------------------------

    @Test
    void getRecentUrls_returnsOnlyCurrentUsersUrls() {
        List<URLResponse> urls =
                List.of(
                        URLResponse.builder()
                                .shortCode("abc")
                                .longUrl("https://example.com")
                                .active(true)
                                .build()
                );

        when(urlService.getRecentUrls(testUser))
                .thenReturn(urls);

        ResponseEntity<List<URLResponse>> response =
                controller.getRecentUrls(authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .containsExactlyElementsOf(urls);

        verify(urlService)
                .getRecentUrls(testUser);
    }

    // -------------------------------------------------------------------------
    // POST /api/urls
    // -------------------------------------------------------------------------

    @Test
    void createShortURL_createsUrlForCurrentOwner() {
        URLRequest request = new URLRequest();
        request.setLongUrl("https://example.com");

        URLResponse created =
                URLResponse.builder()
                        .shortCode("abc")
                        .shortUrl("http://localhost:8080/abc")
                        .longUrl("https://example.com")
                        .active(true)
                        .build();

        when(urlService.createShortUrl(
                request,
                testUser
        )).thenReturn(created);

        ResponseEntity<URLResponse> response =
                controller.createShortURL(request, authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(created);

        verify(urlService)
                .createShortUrl(
                        request,
                        testUser
                );
    }

    @Test
    void createShortURL_returnsConflictWhenAliasExists() {
        URLRequest request = new URLRequest();
        request.setLongUrl("https://example.com");
        request.setCustomAlias("taken");

        when(urlService.createShortUrl(
                request,
                testUser
        ))
        .thenThrow(
                new AliasAlreadyExistsException("taken")
        );

        ResponseEntity<URLResponse> response =
                controller.createShortURL(request, authentication);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().getShortUrl())
                .isEqualTo("error");
    }
}
