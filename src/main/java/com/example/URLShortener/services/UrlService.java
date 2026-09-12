//src/main/java/com/example/URLShortener/services/UrlService.java
package com.example.URLShortener.services;

import com.example.URLShortener.dto.URLRequest;
import com.example.URLShortener.dto.URLResponse;
import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UrlService {

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(5);
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "analytics", "settings", "links", "sign-in", "sign-up",
            "help", "about", "terms", "privacy", "assets", "actuator", "error"
    );

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;


    // ============================================================
    // CREATE SHORT URL
    // ============================================================

    @Transactional
    public URLResponse createShortUrl(
            URLRequest request,
            User owner
    ) {

        String longUrl = request.getLongUrl();

        LocalDateTime expirationTime =
                request.getExpirationTime();


        // ========================================================
        // REDIS LOOKUP
        // ========================================================

        /*
         * The long URL cache is user-scoped.
         *
         * This is important because two different users may shorten
         * the same destination URL independently.
         *
         * Example:
         *
         * user 1 + https://example.com → abc
         * user 2 + https://example.com → xyz
         *
         * They must not receive each other's private URL.
         */
        String cachedShortCode =
                redisTemplate.opsForValue()
                        .get(longKey(owner, longUrl));

        if (cachedShortCode != null) {

            Optional<URL> existingOptional =
                    urlRepository.findByShortUrlAndActiveTrue(
                            cachedShortCode
                    );

            if (existingOptional.isPresent()) {

                URL existing =
                        existingOptional.get();

                /*
                 * Verify ownership even when Redis contains a
                 * supposedly valid mapping.
                 */
                if (!owner.getId().equals(
                        existing.getOwner() != null
                                ? existing.getOwner().getId()
                                : null
                )) {

                    redisTemplate.delete(
                            longKey(owner, longUrl)
                    );

                } else if (isExpired(existing)) {

                    deactivate(existing);

                    evictCache(existing);

                } else {

                    return toResponse(existing);
                }

            } else {

                /*
                 * Redis contains stale data.
                 */
                redisTemplate.delete(
                        longKey(owner, longUrl)
                );
            }
        }


        // ========================================================
        // DATABASE LOOKUP
        // ========================================================

        /*
         * PostgreSQL remains the source of truth.
         *
         * The lookup is scoped to the current user.
         */
        Optional<URL> existingByDatabase =
                urlRepository.findByLongUrlAndActiveTrueAndOwner(
                        longUrl,
                        owner
                );

        if (existingByDatabase.isPresent()) {

            URL existing =
                    existingByDatabase.get();

            if (isExpired(existing)) {

                deactivate(existing);

                evictCache(existing);

            } else {

                cacheMapping(existing);

                return toResponse(existing);
            }
        }


        // ========================================================
        // CUSTOM ALIAS VALIDATION
        // ========================================================

        String desiredShortCode =
                request.getCustomAlias();

        if (desiredShortCode != null
                && !desiredShortCode.isBlank()) {

            desiredShortCode =
                    desiredShortCode.trim();

            if (!desiredShortCode.matches("[A-Za-z0-9_-]{1,8}")
                    || RESERVED_ALIASES.contains(desiredShortCode.toLowerCase(Locale.ROOT))) {
                throw new InvalidAliasException("This memorable link ending is unavailable");
            }

            if (urlRepository.existsByShortUrl(
                    desiredShortCode
            )) {

                throw new AliasAlreadyExistsException(
                        "Custom alias already exists: "
                                + desiredShortCode
                );
            }
        }


        boolean hasCustomAlias =
                desiredShortCode != null
                        && !desiredShortCode.isBlank();


        // ========================================================
        // CREATE ENTITY
        // ========================================================

        URL url = URL.builder()
                .longUrl(longUrl)
                .expiresAt(expirationTime)
                .active(true)
                .shortUrl(
                        hasCustomAlias
                                ? desiredShortCode
                                : null
                )
                .owner(owner)
                .build();


        // ========================================================
        // FIRST SAVE
        // ========================================================

        URL saved =
                urlRepository.save(url);


        // ========================================================
        // GENERATE SHORT CODE
        // ========================================================

        String shortCode;

        if (hasCustomAlias) {

            shortCode =
                    desiredShortCode;

        } else {

            if (saved.getId() == null) {

                throw new IllegalStateException(
                        "Database did not generate an ID "
                                + "for the URL"
                );
            }

            shortCode =
                    Base62Encoder.encode(
                            saved.getId()
                    );

            if (shortCode.length() > 8) {

                throw new IllegalStateException(
                        "Generated short code exceeds "
                                + "the maximum length of 8 characters: "
                                + shortCode
                );
            }

            saved.setShortUrl(shortCode);
        }


        // ========================================================
        // FINAL SAVE
        // ========================================================

        URL finalEntity;

        if (hasCustomAlias) {

            finalEntity =
                    saved;

        } else {

            finalEntity =
                    urlRepository.save(saved);
        }


        // ========================================================
        // CACHE
        // ========================================================

        cacheMapping(finalEntity);


        return toResponse(finalEntity);
    }

    public static class InvalidAliasException extends RuntimeException {
        public InvalidAliasException(String message) { super(message); }
    }


    // ============================================================
    // GET RECENT URLS FOR CURRENT USER
    // ============================================================

    @Transactional
    public List<URLResponse> getRecentUrls(
            User owner
    ) {

        List<URL> urls =
                urlRepository
                        .findTop20ByOwnerOrderByCreatedAtDesc(
                                owner
                        );


        if (urls.isEmpty()) {
            return List.of();
        }


        List<String> shortCodes =
                urls.stream()
                        .map(URL::getShortUrl)
                        .toList();


        Map<String, Long> clickCounts =
                new HashMap<>();

        List<Object[]> rows =
                clickEventRepository
                        .countClicksByShortUrls(
                                shortCodes
                        );


        for (Object[] row : rows) {

            String shortCode =
                    (String) row[0];

            long count =
                    ((Number) row[1]).longValue();

            clickCounts.put(
                    shortCode,
                    count
            );
        }


        return urls.stream()
                .map(url -> {

                    boolean expired =
                            isExpired(url);

                    if (expired && url.isActive()) {

                        deactivate(url);
                    }

                    boolean active =
                            url.isActive()
                                    && !expired;

                    long clicks =
                            clickCounts.getOrDefault(
                                    url.getShortUrl(),
                                    0L
                            );

                    return URLResponse.builder()
                            .shortUrl(
                                    buildShortUrl(
                                            url.getShortUrl()
                                    )
                            )
                            .shortCode(
                                    url.getShortUrl()
                            )
                            .longUrl(
                                    url.getLongUrl()
                            )
                            .expirationTime(
                                    url.getExpiresAt()
                            )
                            .createdAt(
                                    url.getCreatedAt()
                            )
                            .active(active)
                            .clicks(clicks)
                            .build();
                })
                .toList();
    }


    // ============================================================
    // RESOLVE SHORT URL
    // ============================================================

    /*
     * IMPORTANT:
     *
     * This method remains public and owner-independent.
     *
     * Anyone who possesses a valid short URL must be able to
     * follow it.
     */
    @Transactional
    public String resolveLongUrl(
            String shortCode
    ) {

        String cachedLong =
                redisTemplate.opsForValue()
                        .get(shortKey(shortCode));


        if (cachedLong != null) {

            Optional<URL> optionalUrl =
                    urlRepository
                            .findByShortUrlAndActiveTrue(
                                    shortCode
                            );


            if (optionalUrl.isPresent()) {

                URL url =
                        optionalUrl.get();

                if (isExpired(url)) {

                    deactivate(url);

                    evictCache(url);

                    throw new UrlExpiredException(
                            "Short URL has expired: "
                                    + shortCode
                    );
                }

            } else {

                redisTemplate.delete(
                        shortKey(shortCode)
                );

                throw new UrlNotFoundException(
                        "Short URL not found: "
                                + shortCode
                );
            }


            return cachedLong;
        }


        Optional<URL> optionalUrl =
                urlRepository
                        .findByShortUrlAndActiveTrue(
                                shortCode
                        );


        URL url =
                optionalUrl.orElseThrow(
                        () -> new UrlNotFoundException(
                                "Short URL not found: "
                                        + shortCode
                        )
                );


        if (isExpired(url)) {

            deactivate(url);

            evictCache(url);

            throw new UrlExpiredException(
                    "Short URL has expired: "
                            + shortCode
            );
        }


        cacheMapping(url);


        return url.getLongUrl();
    }


    // ============================================================
    // GET URL DETAILS FOR CURRENT USER
    // ============================================================

    @Transactional
    public URLResponse getUrlDetails(
            String shortCode,
            User owner
    ) {

        /*
         * Ownership is part of the database lookup.
         *
         * If the URL belongs to another user, it appears as
         * "not found" rather than revealing its existence.
         */
        URL url =
                urlRepository
                        .findByShortUrlAndOwner(
                                shortCode,
                                owner
                        )
                        .orElseThrow(
                                () -> new UrlNotFoundException(
                                        "Short URL not found: "
                                                + shortCode
                                )
                        );


        boolean expired =
                isExpired(url);


        if (expired && url.isActive()) {

            deactivate(url);
        }


        long clicks =
                clickEventRepository
                        .countByShortUrl(shortCode);


        return URLResponse.builder()
                .shortUrl(
                        buildShortUrl(shortCode)
                )
                .shortCode(shortCode)
                .longUrl(url.getLongUrl())
                .expirationTime(
                        url.getExpiresAt()
                )
                .createdAt(
                        url.getCreatedAt()
                )
                .active(
                        url.isActive()
                                && !expired
                )
                .clicks(clicks)
                .build();
    }


    // ============================================================
    // RESPONSE MAPPING
    // ============================================================

    private URLResponse toResponse(
            URL url
    ) {

        String shortCode =
                url.getShortUrl();


        return URLResponse.builder()
                .shortUrl(
                        buildShortUrl(shortCode)
                )
                .shortCode(shortCode)
                .longUrl(url.getLongUrl())
                .expirationTime(
                        url.getExpiresAt()
                )
                .createdAt(
                        url.getCreatedAt()
                )
                .active(
                        url.isActive()
                )
                .clicks(0)
                .build();
    }


    // ============================================================
    // BUILD FULL SHORT URL
    // ============================================================

    private String buildShortUrl(
            String shortCode
    ) {

        if (publicBaseUrl.endsWith("/")) {

            return publicBaseUrl + shortCode;
        }

        return publicBaseUrl + "/" + shortCode;
    }


    // ============================================================
    // REDIS SHORT KEY
    // ============================================================

    private String shortKey(
            String shortCode
    ) {

        return "short:" + shortCode;
    }


    // ============================================================
    // REDIS LONG URL KEY
    // ============================================================

    /*
     * IMPORTANT:
     *
     * Long URL cache is scoped to the owner.
     */
    private String longKey(
            User owner,
            String longUrl
    ) {

        return "long:user:"
                + owner.getId()
                + ":"
                + longUrl;
    }


    // ============================================================
    // CACHE MAPPING
    // ============================================================

    private void cacheMapping(
            URL url
    ) {

        if (!url.isActive()) {
            return;
        }


        String shortCode =
                url.getShortUrl();

        String longUrl =
                url.getLongUrl();


        redisTemplate.opsForValue().set(
                shortKey(shortCode),
                longUrl,
                CACHE_TTL
        );


        /*
         * Only create the owner-scoped long URL mapping when
         * ownership exists.
         *
         * Legacy URLs have owner = null.
         */
        if (url.getOwner() != null
                && url.getOwner().getId() != null) {

            redisTemplate.opsForValue().set(
                    longKey(
                            url.getOwner(),
                            longUrl
                    ),
                    shortCode,
                    CACHE_TTL
            );
        }
    }


    // ============================================================
    // EXPIRATION
    // ============================================================

    private boolean isExpired(
            URL url
    ) {

        LocalDateTime expiresAt =
                url.getExpiresAt();


        return expiresAt != null
                && LocalDateTime.now()
                .isAfter(expiresAt);
    }


    // ============================================================
    // DEACTIVATE
    // ============================================================

    private void deactivate(
            URL url
    ) {

        if (url.isActive()) {

            url.setActive(false);

            urlRepository.save(url);
        }
    }


    // ============================================================
    // EVICT REDIS CACHE
    // ============================================================
    private void evictCache(
            URL url
    ) {

        redisTemplate.delete(
                shortKey(url.getShortUrl())
        );

        if (url.getOwner() != null
                && url.getOwner().getId() != null) {

            redisTemplate.delete(
                    longKey(
                            url.getOwner(),
                            url.getLongUrl()
                    )
            );
        }
    }


    // ============================================================
    // EXCEPTIONS
    // ============================================================

    public static class AliasAlreadyExistsException
            extends RuntimeException {

        public AliasAlreadyExistsException(
                String message
        ) {
            super(message);
        }
    }


    public static class UrlNotFoundException
            extends RuntimeException {

        public UrlNotFoundException(
                String message
        ) {
            super(message);
        }
    }


    public static class UrlExpiredException
            extends RuntimeException {

        public UrlExpiredException(
                String message
        ) {
            super(message);
        }
    }
}
