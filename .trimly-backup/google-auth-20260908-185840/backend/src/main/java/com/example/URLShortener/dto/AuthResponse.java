package com.example.URLShortener.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String token;

    private String tokenType;

    private Integer userId;

    private String email;

    private boolean requiresTwoFactor;

    private String challengeToken;
}
