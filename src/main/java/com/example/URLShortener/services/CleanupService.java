package com.example.URLShortener.services;

import com.example.URLShortener.models.URL;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Remove URLs whose expiration time has passed.
     *
     * Runs once every hour.
     *
     * Cleanup removes:
     *
     * 1. Click events belonging to the URL.
     * 2. The URL itself.
     * 3. The global short-code Redis mapping.
     * 4. The owner-scoped long-URL Redis mapping.
     *
     * Legacy URLs with no owner only have the global short-code
     * mapping removed.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredUrls() {

        log.info(
                "Starting background cleanup of expired URLs"
        );

        LocalDateTime now =
                LocalDateTime.now();

        List<URL> expiredUrls =
                urlRepository.findByExpiresAtBefore(now);

        if (expiredUrls.isEmpty()) {

            log.info(
                    "No expired URLs found to clean up."
            );

            return;
        }

        int cleanedCount = 0;

        for (URL url : expiredUrls) {

            String shortCode =
                    url.getShortUrl();

            String longUrl =
                    url.getLongUrl();

            /*
             * Delete analytics first.
             *
             * ClickEvent stores the short code rather than a direct
             * foreign-key relationship to URL.
             */
            clickEventRepository.deleteByShortUrl(
                    shortCode
            );

            /*
             * Delete the URL itself.
             */
            urlRepository.delete(url);

            /*
             * Remove global short-code cache.
             */
            redisTemplate.delete(
                    "short:" + shortCode
            );

            /*
             * Remove owner-scoped long-url cache.
             *
             * Legacy URLs have owner == null and therefore do not
             * have an owner-scoped long-url mapping.
             */
            if (url.getOwner() != null
                    && url.getOwner().getId() != null) {

                redisTemplate.delete(
                        "long:user:"
                                + url.getOwner().getId()
                                + ":"
                                + longUrl
                );
            }

            cleanedCount++;
        }

        log.info(
                "Successfully cleaned up {} expired URLs and their associated analytics.",
                cleanedCount
        );
    }
}
