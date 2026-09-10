package com.example.URLShortener.repository;

import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<URL, Integer> {

    // -------------------------------------------------------------------------
    // Public URL resolution
    // -------------------------------------------------------------------------

    Optional<URL> findByShortUrl(String shortUrl);

    Optional<URL> findByShortUrlAndActiveTrue(String shortUrl);

    // -------------------------------------------------------------------------
    // Owner-scoped URL operations
    // -------------------------------------------------------------------------

    Optional<URL> findByShortUrlAndOwner(
            String shortUrl,
            User owner
    );

    Optional<URL> findByLongUrlAndActiveTrueAndOwner(
            String longUrl,
            User owner
    );

    List<URL> findByOwner(
            User owner
    );

    List<URL> findTop20ByOwnerOrderByCreatedAtDesc(
            User owner
    );

    // -------------------------------------------------------------------------
    // Global uniqueness
    // -------------------------------------------------------------------------

    boolean existsByShortUrl(
            String shortUrl
    );

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    List<URL> findByExpiresAtBefore(
            LocalDateTime now
    );
}