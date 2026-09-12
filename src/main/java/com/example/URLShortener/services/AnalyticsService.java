//src/main/java/com/example/URLShortener/services/AnalyticsService.java
package com.example.URLShortener.services;

import com.example.URLShortener.dto.AnalyticsDashboardResponse;
import com.example.URLShortener.dto.AnalyticsResponse;
import com.example.URLShortener.models.ClickEvent;
import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.UrlRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter CHART_DATE_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE;

    // -------------------------------------------------------------------------
    // Record click
    // -------------------------------------------------------------------------

    @Transactional
    public void recordClick(
            String shortUrl,
            HttpServletRequest request) {

        String ipAddress =
                request.getHeader("X-Forwarded-For");

        if (ipAddress == null
                || ipAddress.isBlank()
                || "unknown".equalsIgnoreCase(ipAddress.trim())) {

            ipAddress = request.getRemoteAddr();
        } else {
            ipAddress =
                    ipAddress.split(",")[0].trim();

            if (ipAddress.isEmpty()
                    || "unknown".equalsIgnoreCase(ipAddress)) {

                ipAddress = request.getRemoteAddr();
            }
        }

        String userAgent =
                request.getHeader("User-Agent");

        ClickEvent event =
                ClickEvent.builder()
                        .shortUrl(shortUrl)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .clickedAt(LocalDateTime.now())
                        .build();

        clickEventRepository.save(event);
    }

    // -------------------------------------------------------------------------
    // Analytics for one short URL
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AnalyticsResponse getStats(
            String shortUrl,
            User owner) {

        /*
         * IMPORTANT:
         *
         * We first verify that this short URL belongs to the
         * authenticated user.
         *
         * This prevents User A from requesting analytics for
         * User B's short URL.
         */
        urlRepository
                .findByShortUrlAndOwner(
                        shortUrl,
                        owner
                )
                .orElseThrow(
                        () -> new UrlService.UrlNotFoundException(
                                "Short URL not found: " + shortUrl
                        )
                );

        long totalClicks =
                clickEventRepository.countByShortUrl(shortUrl);

        List<ClickEvent> recentEvents =
                clickEventRepository
                        .findByShortUrlOrderByClickedAtDesc(shortUrl);

        List<AnalyticsResponse.ClickDetails> details =
                recentEvents.stream()
                        .limit(50)
                        .map(event ->
                                AnalyticsResponse.ClickDetails.builder()
                                        .ipAddress(
                                                event.getIpAddress()
                                        )
                                        .userAgent(
                                                event.getUserAgent()
                                        )
                                        .clickedAt(
                                                event.getClickedAt()
                                                        .format(FORMATTER)
                                        )
                                        .build()
                        )
                        .collect(Collectors.toList());

        return AnalyticsResponse.builder()
                .shortUrl(shortUrl)
                .totalClicks(totalClicks)
                .recentClicks(details)
                .build();
    }

    // -------------------------------------------------------------------------
    // Dashboard analytics
    // GET /api/analytics
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboardAnalytics(
            User owner) {

        return getDashboardAnalytics(owner, 30);
    }

    /**
     * Builds a dashboard for the requested number of calendar days, including
     * today. All range-sensitive values use the same [start, tomorrow) window.
     */
    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboardAnalytics(
            User owner,
            int days) {

        if (days < 1 || days > 90) {
            throw new IllegalArgumentException(
                    "Analytics days must be between 1 and 90"
            );
        }

        LocalDate today =
                LocalDate.now();

        LocalDateTime start =
                today
                        .minusDays(days - 1L)
                        .atStartOfDay();

        LocalDateTime tomorrow =
                today
                        .plusDays(1)
                        .atStartOfDay();

        // ---------------------------------------------------------------------
        // Get URLs owned by the authenticated user
        // ---------------------------------------------------------------------

        List<URL> urls =
                urlRepository.findByOwner(owner);

        List<String> shortCodes =
                urls.stream()
                        .map(URL::getShortUrl)
                        .filter(Objects::nonNull)
                        .toList();

        // ---------------------------------------------------------------------
        // Click counts
        //
        // Fetch aggregate counts once and reuse them for both:
        //   - total clicks
        //   - top links
        // ---------------------------------------------------------------------

        final Map<String, Long> clickCounts;

        if (shortCodes.isEmpty()) {

            clickCounts =
                    Map.of();

        } else {

            clickCounts =
                    clickEventRepository
                            .countClicksByShortUrlsBetween(
                                    shortCodes,
                                    start,
                                    tomorrow
                            )
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            row ->
                                                    (String) row[0],
                                            row ->
                                                    ((Number) row[1])
                                                            .longValue()
                                    )
                            );
        }

        long totalClicks =
                clickCounts.values()
                        .stream()
                        .mapToLong(Long::longValue)
                        .sum();

        // ---------------------------------------------------------------------
        // Clicks today
        // ---------------------------------------------------------------------

        long clicksToday;

        if (shortCodes.isEmpty()) {

            clicksToday = 0L;

        } else {

            clicksToday =
                    clickEventRepository
                            .countClicksBetweenForShortUrls(
                                    shortCodes,
                                    today.atStartOfDay(),
                                    tomorrow
                            );
        }

        // ---------------------------------------------------------------------
        // Links created
        // ---------------------------------------------------------------------

        long linksCreated =
                urls.stream()
                        .map(URL::getCreatedAt)
                        .filter(Objects::nonNull)
                        .filter(createdAt ->
                                !createdAt.isBefore(start)
                                        && createdAt.isBefore(tomorrow)
                        )
                        .count();

        // ---------------------------------------------------------------------
        // Daily clicks
        // ---------------------------------------------------------------------

        List<Object[]> dailyRows;

        if (shortCodes.isEmpty()) {

            dailyRows = List.of();

        } else {

            dailyRows =
                    clickEventRepository
                            .countClicksByDayBetweenForShortUrls(
                                    shortCodes,
                                    start,
                                    tomorrow
                            );
        }

        Map<LocalDate, Long> clicksByDay =
                new HashMap<>();

        for (Object[] row : dailyRows) {

            Object dateValue =
                    row[0];

            LocalDate date;

            if (dateValue instanceof java.sql.Date sqlDate) {

                date = sqlDate.toLocalDate();

            } else if (dateValue instanceof LocalDate localDate) {

                date = localDate;

            } else {

                date =
                        LocalDate.parse(
                                dateValue.toString()
                        );
            }

            long count =
                    ((Number) row[1]).longValue();

            clicksByDay.put(
                    date,
                    count
            );
        }

        List<AnalyticsDashboardResponse.DailyClick> dailyClicks =
                new ArrayList<>();

        LocalDate current =
                start.toLocalDate();

        while (!current.isAfter(today)) {

            long clicks =
                    clicksByDay.getOrDefault(
                            current,
                            0L
                    );

            dailyClicks.add(
                    AnalyticsDashboardResponse.DailyClick.builder()
                            .date(
                                    current.format(
                                            CHART_DATE_FORMATTER
                                    )
                            )
                            .clicks(clicks)
                            .build()
            );

            current =
                    current.plusDays(1);
        }

        // ---------------------------------------------------------------------
        // Top links
        // ---------------------------------------------------------------------

        List<AnalyticsDashboardResponse.TopLink> topLinks =
                urls.stream()
                        .filter(
                                url ->
                                        url.getShortUrl() != null
                        )
                        .map(
                                url ->
                                        AnalyticsDashboardResponse.TopLink
                                                .builder()
                                                .shortCode(
                                                        url.getShortUrl()
                                                )
                                                .shortUrl(
                                                        buildShortUrl(
                                                                url.getShortUrl()
                                                        )
                                                )
                                                .longUrl(
                                                        url.getLongUrl()
                                                )
                                                .clicks(
                                                        clickCounts
                                                                .getOrDefault(
                                                                        url.getShortUrl(),
                                                                        0L
                                                                )
                                                )
                                                .build()
                        )
                        .sorted(
                                Comparator.comparingLong(
                                        AnalyticsDashboardResponse.TopLink
                                                ::getClicks
                                ).reversed()
                        )
                        .limit(5)
                        .toList();

        // ---------------------------------------------------------------------
        // Dashboard response
        // ---------------------------------------------------------------------

        return AnalyticsDashboardResponse.builder()
                .totalClicks(totalClicks)
                .clicksToday(clicksToday)
                .linksCreated(linksCreated)
                .dailyClicks(dailyClicks)
                .topLinks(topLinks)
                .build();
    }

    // -------------------------------------------------------------------------
    // Short URL builder
    // -------------------------------------------------------------------------

    private String buildShortUrl(
            String shortCode) {

        return publicBaseUrl.replaceAll("/+$", "") + "/" + shortCode;
    }
}
