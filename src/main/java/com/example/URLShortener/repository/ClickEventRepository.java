package com.example.URLShortener.repository;

import com.example.URLShortener.models.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository
        extends JpaRepository<ClickEvent, Long> {

    // -------------------------------------------------------------------------
    // Individual URL analytics
    // -------------------------------------------------------------------------

    List<ClickEvent> findByShortUrlOrderByClickedAtDesc(
            String shortUrl
    );

    long countByShortUrl(
            String shortUrl
    );

    // -------------------------------------------------------------------------
    // Dashboard analytics
    // -------------------------------------------------------------------------

    /**
     * Returns click counts grouped by short URL.
     *
     * Each row contains:
     *
     *     [0] short URL / short code
     *     [1] click count
     */
    @Query("""
            SELECT c.shortUrl, COUNT(c)
            FROM ClickEvent c
            WHERE c.shortUrl IN :shortUrls
            GROUP BY c.shortUrl
            """)
    List<Object[]> countClicksByShortUrls(
            @Param("shortUrls") List<String> shortUrls
    );

    /**
     * Returns click counts grouped by short URL for a time range.
     *
     * start is inclusive and end is exclusive.
     */
    @Query("""
            SELECT c.shortUrl, COUNT(c)
            FROM ClickEvent c
            WHERE c.shortUrl IN :shortUrls
              AND c.clickedAt >= :start
              AND c.clickedAt < :end
            GROUP BY c.shortUrl
            """)
    List<Object[]> countClicksByShortUrlsBetween(
            @Param("shortUrls") List<String> shortUrls,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Counts clicks for the supplied short URLs inside a time range.
     *
     * start is inclusive.
     * end is exclusive.
     */
    @Query("""
            SELECT COUNT(c)
            FROM ClickEvent c
            WHERE c.shortUrl IN :shortUrls
              AND c.clickedAt >= :start
              AND c.clickedAt < :end
            """)
    long countClicksBetweenForShortUrls(
            @Param("shortUrls") List<String> shortUrls,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Returns click counts grouped by calendar day for the supplied
     * short URLs.
     *
     * PostgreSQL groups the click timestamps by date.
     */
    @Query(value = """
            SELECT CAST(clicked_at AS DATE) AS click_date,
                   COUNT(*) AS click_count
            FROM click_events
            WHERE short_url IN (:shortUrls)
              AND clicked_at >= :start
              AND clicked_at < :end
            GROUP BY CAST(clicked_at AS DATE)
            ORDER BY click_date
            """,
            nativeQuery = true)
    List<Object[]> countClicksByDayBetweenForShortUrls(
            @Param("shortUrls") List<String> shortUrls,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @Modifying
    @Query("""
            DELETE FROM ClickEvent c
            WHERE c.shortUrl = :shortUrl
            """)
    void deleteByShortUrl(
            @Param("shortUrl") String shortUrl
    );
}
