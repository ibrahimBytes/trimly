package com.example.URLShortener.services;

import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class CleanupServiceTest {

    private UrlRepository urlRepository;

    private ClickEventRepository clickEventRepository;

    private StringRedisTemplate redisTemplate;

    private CleanupService cleanupService;

    private User owner;


    @BeforeEach
    void setUp() {

        urlRepository =
                mock(UrlRepository.class);

        clickEventRepository =
                mock(ClickEventRepository.class);

        redisTemplate =
                mock(StringRedisTemplate.class);

        cleanupService =
                new CleanupService(
                        urlRepository,
                        clickEventRepository,
                        redisTemplate
                );

        owner =
                User.builder()
                        .id(1)
                        .email("owner@example.com")
                        .build();
    }


    @Test
    void cleanupExpiredUrls_removesUrlAnalyticsAndBothRedisMappings() {

        URL expiredUrl =
                URL.builder()
                        .id(1)
                        .shortUrl("abc123")
                        .longUrl("https://example.com")
                        .owner(owner)
                        .expiresAt(
                                LocalDateTime.now()
                                        .minusMinutes(10)
                        )
                        .active(false)
                        .build();

        when(
                urlRepository.findByExpiresAtBefore(
                        any(LocalDateTime.class)
                )
        )
        .thenReturn(
                List.of(expiredUrl)
        );


        cleanupService.cleanupExpiredUrls();


        verify(clickEventRepository)
                .deleteByShortUrl(
                        "abc123"
                );

        verify(urlRepository)
                .delete(expiredUrl);

        verify(redisTemplate)
                .delete(
                        "short:abc123"
                );

        verify(redisTemplate)
                .delete(
                        "long:user:1:https://example.com"
                );
    }


    @Test
    void cleanupExpiredUrls_removesOnlyShortCacheForLegacyUrl() {

        URL legacyUrl =
                URL.builder()
                        .id(2)
                        .shortUrl("legacy")
                        .longUrl("https://legacy.example.com")
                        .owner(null)
                        .expiresAt(
                                LocalDateTime.now()
                                        .minusMinutes(10)
                        )
                        .active(false)
                        .build();

        when(
                urlRepository.findByExpiresAtBefore(
                        any(LocalDateTime.class)
                )
        )
        .thenReturn(
                List.of(legacyUrl)
        );


        cleanupService.cleanupExpiredUrls();


        verify(clickEventRepository)
                .deleteByShortUrl(
                        "legacy"
                );

        verify(urlRepository)
                .delete(legacyUrl);

        verify(redisTemplate)
                .delete(
                        "short:legacy"
                );

        verify(redisTemplate, never())
                .delete(
                        startsWith("long:user:")
                );
    }


    @Test
    void cleanupExpiredUrls_doesNothingWhenNoExpiredUrlsExist() {

        when(
                urlRepository.findByExpiresAtBefore(
                        any(LocalDateTime.class)
                )
        )
        .thenReturn(
                List.of()
        );


        cleanupService.cleanupExpiredUrls();


        verify(
                clickEventRepository,
                never()
        )
        .deleteByShortUrl(anyString());

        verify(
                urlRepository,
                never()
        )
        .delete(any(URL.class));

        verify(
                redisTemplate,
                never()
        )
        .delete(anyString());
    }
}
