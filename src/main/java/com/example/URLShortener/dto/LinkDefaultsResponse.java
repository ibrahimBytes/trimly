package com.example.URLShortener.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkDefaultsResponse {

    private String defaultLinkExpiration;
}
