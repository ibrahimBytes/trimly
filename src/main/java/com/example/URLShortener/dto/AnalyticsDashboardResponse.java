package com.example.URLShortener.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyticsDashboardResponse {

    private long totalClicks;

    private long clicksToday;

    private long linksCreated;

    private List<DailyClick> dailyClicks;

    private List<TopLink> topLinks;

    @Data
    @Builder
    public static class DailyClick {

        private String date;

        private long clicks;
    }

    @Data
    @Builder
    public static class TopLink {

        private String shortCode;

        private String shortUrl;

        private String longUrl;

        private long clicks;
    }
}