package com.example.URLShortener.services;

import com.example.URLShortener.dto.AnalyticsDashboardResponse;
import com.example.URLShortener.dto.AnalyticsResponse;
import com.example.URLShortener.models.ClickEvent;
import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private ClickEventRepository clickEventRepository;
    private UrlRepository urlRepository;
    private AnalyticsService analyticsService;

    private User owner;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        clickEventRepository =
                mock(ClickEventRepository.class);

        urlRepository =
                mock(UrlRepository.class);

        analyticsService =
                new AnalyticsService(
                        clickEventRepository,
                        urlRepository
                );

        ReflectionTestUtils.setField(
                analyticsService,
                "publicBaseUrl",
                "http://localhost:8080"
        );

        owner =
                User.builder()
                        .id(1)
                        .email("owner@example.com")
                        .build();

        anotherUser =
                User.builder()
                        .id(2)
                        .email("another@example.com")
                        .build();
    }

    // -------------------------------------------------------------------------
    // Record click
    // -------------------------------------------------------------------------

    @Test
    void recordClick_savesClickEvent() {
        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("X-Forwarded-For"))
                .thenReturn(null);

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(request.getHeader("User-Agent"))
                .thenReturn("JUnit");

        analyticsService.recordClick(
                "abc",
                request
        );

        verify(clickEventRepository)
                .save(
                        argThat(event ->
                                event.getShortUrl()
                                        .equals("abc")
                                        && event.getIpAddress()
                                        .equals("127.0.0.1")
                                        && event.getUserAgent()
                                        .equals("JUnit")
                                        && event.getClickedAt() != null
                        )
                );
    }

    @Test
    void recordClick_prefersForwardedIp() {
        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("X-Forwarded-For"))
                .thenReturn("192.168.1.10, 10.0.0.1");

        when(request.getRemoteAddr())
                .thenReturn("127.0.0.1");

        when(request.getHeader("User-Agent"))
                .thenReturn("Browser");

        analyticsService.recordClick(
                "abc",
                request
        );

        verify(clickEventRepository)
                .save(
                        argThat(event ->
                                event.getIpAddress()
                                        .equals("192.168.1.10")
                        )
                );
    }

    // -------------------------------------------------------------------------
    // Per-link analytics
    // -------------------------------------------------------------------------

    @Test
    void getStats_returnsAnalyticsForOwner() {
        URL url =
                URL.builder()
                        .id(1)
                        .shortUrl("abc")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .active(true)
                        .build();

        ClickEvent first =
                ClickEvent.builder()
                        .shortUrl("abc")
                        .ipAddress("127.0.0.1")
                        .userAgent("Chrome")
                        .clickedAt(
                                LocalDateTime.now()
                        )
                        .build();

        when(urlRepository.findByShortUrlAndOwner(
                "abc",
                owner
        )).thenReturn(Optional.of(url));

        when(clickEventRepository.countByShortUrl("abc"))
                .thenReturn(5L);

        when(clickEventRepository
                .findByShortUrlOrderByClickedAtDesc("abc"))
                .thenReturn(List.of(first));

        AnalyticsResponse response =
                analyticsService.getStats(
                        "abc",
                        owner
                );

        assertThat(response.getShortUrl())
                .isEqualTo("abc");

        assertThat(response.getTotalClicks())
                .isEqualTo(5L);

        assertThat(response.getRecentClicks())
                .hasSize(1);

        assertThat(
                response
                        .getRecentClicks()
                        .get(0)
                        .getIpAddress()
        )
        .isEqualTo("127.0.0.1");

        verify(urlRepository)
                .findByShortUrlAndOwner(
                        "abc",
                        owner
                );

        verify(clickEventRepository)
                .countByShortUrl("abc");
    }

    @Test
    void getStats_rejectsUrlOwnedByAnotherUser() {
        when(urlRepository.findByShortUrlAndOwner(
                "private",
                owner
        )).thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        analyticsService.getStats(
                                "private",
                                owner
                        )
        )
        .isInstanceOf(
                UrlService.UrlNotFoundException.class
        );

        verify(clickEventRepository, never())
                .countByShortUrl(anyString());

        verify(clickEventRepository, never())
                .findByShortUrlOrderByClickedAtDesc(
                        anyString()
                );
    }

    @Test
    void getStats_doesNotExposeAnotherUsersAnalytics() {
        URL anotherUsersUrl =
                URL.builder()
                        .id(2)
                        .shortUrl("secret")
                        .longUrl("https://secret.example.com")
                        .owner(anotherUser)
                        .active(true)
                        .build();

        when(urlRepository.findByShortUrlAndOwner(
                "secret",
                owner
        )).thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        analyticsService.getStats(
                                "secret",
                                owner
                        )
        )
        .isInstanceOf(
                UrlService.UrlNotFoundException.class
        );

        verify(clickEventRepository, never())
                .countByShortUrl("secret");
    }

    @Test
    void getStats_limitsRecentClicksTo50() {
        URL url =
                URL.builder()
                        .id(1)
                        .shortUrl("popular")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build();

        List<ClickEvent> events =
                java.util.stream.IntStream
                        .range(0, 100)
                        .mapToObj(i ->
                                ClickEvent.builder()
                                        .shortUrl("popular")
                                        .ipAddress(
                                                "127.0.0." + i
                                        )
                                        .userAgent("JUnit")
                                        .clickedAt(
                                                LocalDateTime.now()
                                                        .minusMinutes(i)
                                        )
                                        .build()
                        )
                        .toList();

        when(urlRepository.findByShortUrlAndOwner(
                "popular",
                owner
        )).thenReturn(Optional.of(url));

        when(clickEventRepository.countByShortUrl("popular"))
                .thenReturn(100L);

        when(clickEventRepository
                .findByShortUrlOrderByClickedAtDesc("popular"))
                .thenReturn(events);

        AnalyticsResponse response =
                analyticsService.getStats(
                        "popular",
                        owner
                );

        assertThat(response.getTotalClicks())
                .isEqualTo(100L);

        assertThat(response.getRecentClicks())
                .hasSize(50);
    }

    // -------------------------------------------------------------------------
    // Dashboard analytics
    // -------------------------------------------------------------------------

    @Test
    void getDashboardAnalytics_scopesUrlsToOwner() {
        URL first =
                URL.builder()
                        .id(1)
                        .shortUrl("abc")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build();

        URL second =
                URL.builder()
                        .id(2)
                        .shortUrl("xyz")
                        .longUrl("https://example.org")
                        .owner(owner)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(urlRepository.findByOwner(owner))
                .thenReturn(List.of(first, second));

        when(clickEventRepository.countClicksByShortUrlsBetween(
                eq(List.of("abc", "xyz")),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        ))
        .thenReturn(
                List.of(
                        new Object[]{"abc", 10L},
                        new Object[]{"xyz", 5L}
                )
        );

        when(clickEventRepository.countClicksBetweenForShortUrls(
                eq(List.of("abc", "xyz")),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        ))
        .thenReturn(3L);

        when(clickEventRepository
                .countClicksByDayBetweenForShortUrls(
                        eq(List.of("abc", "xyz")),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                ))
        .thenReturn(List.of());

        AnalyticsDashboardResponse response =
                analyticsService.getDashboardAnalytics(
                        owner
                );

        assertThat(response.getTotalClicks())
                .isEqualTo(15L);

        assertThat(response.getClicksToday())
                .isEqualTo(3L);

        assertThat(response.getLinksCreated())
                .isEqualTo(2L);

        assertThat(response.getTopLinks())
                .hasSize(2);

        assertThat(
                response
                        .getTopLinks()
                        .get(0)
                        .getShortCode()
        )
        .isEqualTo("abc");

        verify(urlRepository)
                .findByOwner(owner);

        verify(clickEventRepository)
                .countClicksByShortUrlsBetween(
                        eq(List.of("abc", "xyz")),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                );
    }

    @Test
    void getDashboardAnalytics_returnsZeroesWhenOwnerHasNoUrls() {
        when(urlRepository.findByOwner(owner))
                .thenReturn(List.of());

        AnalyticsDashboardResponse response =
                analyticsService.getDashboardAnalytics(
                        owner
                );

        assertThat(response.getTotalClicks())
                .isZero();

        assertThat(response.getClicksToday())
                .isZero();

        assertThat(response.getLinksCreated())
                .isZero();

        assertThat(response.getTopLinks())
                .isEmpty();

        assertThat(response.getDailyClicks())
                .hasSize(30);

        verify(clickEventRepository, never())
                .countClicksByShortUrlsBetween(
                        anyList(),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                );

        verify(clickEventRepository, never())
                .countClicksBetweenForShortUrls(
                        anyList(),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                );
    }

    @Test
    void getDashboardAnalytics_doesNotQueryAnotherUsersUrls() {
        when(urlRepository.findByOwner(owner))
                .thenReturn(List.of());

        analyticsService.getDashboardAnalytics(owner);

        verify(urlRepository)
                .findByOwner(owner);

        verify(urlRepository, never())
                .findByOwner(anotherUser);
    }

    @Test
    void getDashboardAnalytics_usesRequestedRangeForTotalsAndChart() {
        LocalDateTime now = LocalDateTime.now();
        URL recent = URL.builder()
                .id(1)
                .shortUrl("recent")
                .longUrl("https://example.com")
                .owner(owner)
                .createdAt(now.minusDays(2))
                .build();
        URL older = URL.builder()
                .id(2)
                .shortUrl("older")
                .longUrl("https://example.org")
                .owner(owner)
                .createdAt(now.minusDays(10))
                .build();

        when(urlRepository.findByOwner(owner))
                .thenReturn(List.of(recent, older));
        when(clickEventRepository.countClicksByShortUrlsBetween(
                eq(List.of("recent", "older")),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.<Object[]>of(new Object[]{"recent", 4L}));
        when(clickEventRepository.countClicksBetweenForShortUrls(
                eq(List.of("recent", "older")),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1L);
        when(clickEventRepository.countClicksByDayBetweenForShortUrls(
                eq(List.of("recent", "older")),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        AnalyticsDashboardResponse response =
                analyticsService.getDashboardAnalytics(owner, 7);

        assertThat(response.getTotalClicks()).isEqualTo(4L);
        assertThat(response.getLinksCreated()).isEqualTo(1L);
        assertThat(response.getDailyClicks()).hasSize(7);
        assertThat(response.getTopLinks().get(0).getShortCode())
                .isEqualTo("recent");
        assertThat(response.getTopLinks().get(0).getClicks()).isEqualTo(4L);

        verify(clickEventRepository).countClicksByShortUrlsBetween(
                eq(List.of("recent", "older")),
                eq(LocalDate.now().minusDays(6).atStartOfDay()),
                eq(LocalDate.now().plusDays(1).atStartOfDay())
        );
    }
}
