package com.example.URLShortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkDefaultsUpdateRequest {

    @NotBlank(message = "Default link expiration is required")
    private String defaultLinkExpiration;
}
