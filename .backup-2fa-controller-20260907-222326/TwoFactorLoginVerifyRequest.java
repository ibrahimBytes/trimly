package com.example.URLShortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorLoginVerifyRequest {

    @NotBlank
    private String challengeToken;

    @NotBlank
    private String code;
}
