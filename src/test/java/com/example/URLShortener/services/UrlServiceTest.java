package com.example.URLShortener.services;

import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UrlServiceTest {

    private UrlRepository urlRepository;
    private ClickEventRepository clickEventRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    private UrlService urlService;

    private User owner;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        urlRepository =
                mock(UrlRepository.class);

        clickEventRepository =
                mock(ClickEventRepository.class);

        redisTemplate =
                mock(StringRedisTemplate.class);

        valueOperations =
                mock(ValueOperations.class);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

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

        urlService =
                new UrlService(
                        urlRepository,
                        clickEventRepository,
                        redisTemplate
                );

        ReflectionTestUtils.setField(
                urlService,
                "baseUrl",
                "http://localhost:8080/api/urls"
        );
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Test
    void createShortUrl_generatesBase62CodeAndAssignsOwner() {
        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        when(valueOperations.get(
                "long:user:1:https://example.com"
        ))
        .thenReturn(null);

        when(urlRepository.findByLongUrlAndActiveTrueAndOwner(
                "https://example.com",
                owner
        ))
        .thenReturn(Optional.empty());

        when(urlRepository.save(any(URL.class)))
                .thenAnswer(invocation -> {
                    URL url =
                            invocation.getArgument(0);

                    if (url.getId() == null) {
                        url.setId(1);
                    }

                    return url;
                });

        URLResponse response =
                urlService.createShortUrl(
                        request,
                        owner
                );

        assertThat(response.getShortCode())
                .isNotBlank();

        assertThat(response.getLongUrl())
                .isEqualTo("https://example.com");

        assertThat(response.getShortUrl())
                .isEqualTo(
                        "http://localhost:8080/api/urls/"
                                + response.getShortCode()
                );

        verify(urlRepository, times(2))
                .save(
                        argThat(url ->
                                url.getOwner() == owner
                        )
                );

        verify(valueOperations)
                .set(
                        eq("short:" + response.getShortCode()),
                        eq("https://example.com"),
                        any()
                );

        verify(valueOperations)
                .set(
                        eq(
                                "long:user:1:https://example.com"
                        ),
                        eq(response.getShortCode()),
                        any()
                );
    }

    @Test
    void createShortUrl_throwsConflictWhenCustomAliasExists() {
        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        request.setCustomAlias("alias");

        when(valueOperations.get(anyString()))
                .thenReturn(null);

        when(urlRepository.findByLongUrlAndActiveTrueAndOwner(
                "https://example.com",
                owner
        ))
        .thenReturn(Optional.empty());

        when(urlRepository.existsByShortUrl("alias"))
                .thenReturn(true);

        assertThatThrownBy(
                () ->
                        urlService.createShortUrl(
                                request,
                                owner
                        )
        )
        .isInstanceOf(
                UrlService.AliasAlreadyExistsException.class
        );

        verify(urlRepository)
                .existsByShortUrl("alias");
    }

    @Test
    void createShortUrl_reusesExistingOwnerScopedDatabaseUrl() {
        URL existing =
                URL.builder()
                        .id(10)
                        .shortUrl("abc")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .active(true)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusHours(1)
                        )
                        .build();

        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        when(valueOperations.get(
                "long:user:1:https://example.com"
        ))
        .thenReturn(null);

        when(urlRepository.findByLongUrlAndActiveTrueAndOwner(
                "https://example.com",
                owner
        ))
        .thenReturn(Optional.of(existing));

        URLResponse response =
                urlService.createShortUrl(
                        request,
                        owner
                );

        assertThat(response.getShortCode())
                .isEqualTo("abc");

        verify(urlRepository, never())
                .existsByShortUrl(anyString());

        verify(valueOperations)
                .set(
                        eq("short:abc"),
                        eq("https://example.com"),
                        any()
                );

        verify(valueOperations)
                .set(
                        eq(
                                "long:user:1:https://example.com"
                        ),
                        eq("abc"),
                        any()
                );
    }

    @Test
    void createShortUrl_doesNotReuseAnotherUsersUrl() {
        URL anotherUsersUrl =
                URL.builder()
                        .id(20)
                        .shortUrl("secret")
                        .longUrl("https://example.com")
                        .owner(anotherUser)
                        .active(true)
                        .build();

        URLRequest request =
                new URLRequest();

        request.setLongUrl(
                "https://example.com"
        );

        when(valueOperations.get(
                "long:user:1:https://example.com"
        ))
        .thenReturn(null);

        when(urlRepository.findByLongUrlAndActiveTrueAndOwner(
                "https://example.com",
                owner
        ))
        .thenReturn(Optional.empty());

        when(urlRepository.save(any(URL.class)))
                .thenAnswer(invocation -> {
                    URL url =
                            invocation.getArgument(0);

                    if (url.getId() == null) {
                        url.setId(21);
                    }

                    return url;
                });

        URLResponse response =
                urlService.createShortUrl(
                        request,
                        owner
                );

        assertThat(response.getShortCode())
                .isNotEqualTo("secret");

        verify(urlRepository)
                .findByLongUrlAndActiveTrueAndOwner(
                        "https://example.com",
                        owner
                );

        verify(urlRepository, times(2))
                .save(
                        argThat(url ->
                                url.getOwner() == owner
                        )
                );
    }

    // -------------------------------------------------------------------------
    // RESOLVE
    // -------------------------------------------------------------------------

    @Test
    void resolveLongUrl_returnsCachedDestination() {
        when(valueOperations.get("short:abc"))
                .thenReturn("https://cached.com");

        URL entity =
                URL.builder()
                        .id(1)
                        .shortUrl("abc")
                        .longUrl("https://cached.com")
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusMinutes(10)
                        )
                        .active(true)
                        .build();

        when(urlRepository.findByShortUrlAndActiveTrue("abc"))
                .thenReturn(Optional.of(entity));

        String result =
                urlService.resolveLongUrl("abc");

        assertThat(result)
                .isEqualTo("https://cached.com");
    }

    @Test
    void resolveLongUrl_throwsExpiredWhenPastExpiration() {
        URL entity =
                URL.builder()
                        .id(1)
                        .shortUrl("expired")
                        .longUrl("https://expired.com")
                        .expiresAt(
                                LocalDateTime.now()
                                        .minusMinutes(1)
                        )
                        .active(true)
                        .build();

        when(valueOperations.get("short:expired"))
                .thenReturn(null);

        when(urlRepository.findByShortUrlAndActiveTrue(
                "expired"
        ))
        .thenReturn(Optional.of(entity));

        assertThatThrownBy(
                () ->
                        urlService.resolveLongUrl("expired")
        )
        .isInstanceOf(
                UrlService.UrlExpiredException.class
        );

        verify(urlRepository)
                .save(
                        argThat(url ->
                                !url.isActive()
                        )
                );
    }

    @Test
    void resolveLongUrl_throwsNotFoundWhenMissing() {
        when(valueOperations.get("short:missing"))
                .thenReturn(null);

        when(urlRepository.findByShortUrlAndActiveTrue(
                "missing"
        ))
        .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        urlService.resolveLongUrl("missing")
        )
        .isInstanceOf(
                UrlService.UrlNotFoundException.class
        );
    }


    // -------------------------------------------------------------------------
    // REDIS EVICTION
    // -------------------------------------------------------------------------

    @Test
    void resolveLongUrl_expiredOwnedUrlEvictsBothRedisMappings() {

        URL entity =
                URL.builder()
                        .id(1)
                        .shortUrl("expired")
                        .longUrl("https://expired.com")
                        .owner(owner)
                        .expiresAt(
                                LocalDateTime.now()
                                        .minusMinutes(1)
                        )
                        .active(true)
                        .build();

        when(
                valueOperations.get(
                        "short:expired"
                )
        )
        .thenReturn(null);

        when(
                urlRepository.findByShortUrlAndActiveTrue(
                        "expired"
                )
        )
        .thenReturn(
                Optional.of(entity)
        );

        assertThatThrownBy(
                () ->
                        urlService.resolveLongUrl(
                                "expired"
                        )
        )
        .isInstanceOf(
                UrlService.UrlExpiredException.class
        );

        verify(redisTemplate)
                .delete(
                        "short:expired"
                );

        verify(redisTemplate)
                .delete(
                        "long:user:1:https://expired.com"
                );
    }


    @Test
    void resolveLongUrl_expiredLegacyUrlEvictsOnlyShortMapping() {

        URL entity =
                URL.builder()
                        .id(2)
                        .shortUrl("legacy")
                        .longUrl("https://legacy.example.com")
                        .owner(null)
                        .expiresAt(
                                LocalDateTime.now()
                                        .minusMinutes(1)
                        )
                        .active(true)
                        .build();

        when(
                valueOperations.get(
                        "short:legacy"
                )
        )
        .thenReturn(null);

        when(
                urlRepository.findByShortUrlAndActiveTrue(
                        "legacy"
                )
        )
        .thenReturn(
                Optional.of(entity)
        );

        assertThatThrownBy(
                () ->
                        urlService.resolveLongUrl(
                                "legacy"
                        )
        )
        .isInstanceOf(
                UrlService.UrlExpiredException.class
        );

        verify(redisTemplate)
                .delete(
                        "short:legacy"
                );

        verify(redisTemplate, never())
                .delete(
                        startsWith("long:user:")
                );
    }


    // -------------------------------------------------------------------------
    // RECENT URLS
    // -------------------------------------------------------------------------

    @Test
    void getRecentUrls_returnsOnlyOwnerUrlsWithClickCounts() {
        LocalDateTime createdAt =
                LocalDateTime.now()
                        .minusMinutes(5);

        URL first =
                URL.builder()
                        .id(2)
                        .shortUrl("abc123")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .createdAt(createdAt)
                        .active(true)
                        .build();

        URL second =
                URL.builder()
                        .id(1)
                        .shortUrl("xyz789")
                        .longUrl("https://example.org")
                        .owner(owner)
                        .createdAt(
                                createdAt.minusMinutes(5)
                        )
                        .active(true)
                        .build();

        when(urlRepository
                .findTop20ByOwnerOrderByCreatedAtDesc(owner))
                .thenReturn(
                        List.of(first, second)
                );

        when(clickEventRepository.countClicksByShortUrls(
                List.of("abc123", "xyz789")
        ))
        .thenReturn(
                List.of(
                        new Object[]{"abc123", 5L},
                        new Object[]{"xyz789", 2L}
                )
        );

        List<URLResponse> responses =
                urlService.getRecentUrls(owner);

        assertThat(responses)
                .hasSize(2);

        assertThat(
                responses
                        .get(0)
                        .getShortCode()
        )
        .isEqualTo("abc123");

        assertThat(
                responses
                        .get(0)
                        .getClicks()
        )
        .isEqualTo(5L);

        assertThat(
                responses
                        .get(1)
                        .getShortCode()
        )
        .isEqualTo("xyz789");

        assertThat(
                responses
                        .get(1)
                        .getClicks()
        )
        .isEqualTo(2L);

        verify(urlRepository)
                .findTop20ByOwnerOrderByCreatedAtDesc(
                        owner
                );

        verify(clickEventRepository)
                .countClicksByShortUrls(
                        List.of("abc123", "xyz789")
                );
    }

    @Test
    void getRecentUrls_returnsZeroClicksWhenNoEventsExist() {
        URL entity =
                URL.builder()
                        .id(1)
                        .shortUrl("abc123")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .createdAt(LocalDateTime.now())
                        .active(true)
                        .build();

        when(urlRepository
                .findTop20ByOwnerOrderByCreatedAtDesc(owner))
                .thenReturn(
                        List.of(entity)
                );

        when(clickEventRepository.countClicksByShortUrls(
                List.of("abc123")
        ))
        .thenReturn(List.of());

        List<URLResponse> responses =
                urlService.getRecentUrls(owner);

        assertThat(responses)
                .hasSize(1);

        assertThat(
                responses
                        .get(0)
                        .getClicks()
        )
        .isZero();
    }

    @Test
    void getRecentUrls_returnsEmptyWhenOwnerHasNoUrls() {
        when(urlRepository
                .findTop20ByOwnerOrderByCreatedAtDesc(owner))
                .thenReturn(List.of());

        List<URLResponse> responses =
                urlService.getRecentUrls(owner);

        assertThat(responses)
                .isEmpty();

        verify(clickEventRepository, never())
                .countClicksByShortUrls(anyList());
    }
}
