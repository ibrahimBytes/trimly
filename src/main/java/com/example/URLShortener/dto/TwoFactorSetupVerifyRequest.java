package com.example.URLShortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorSetupVerifyRequest {

    @NotBlank
    private String code;
}
