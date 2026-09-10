package com.example.URLShortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorDisableRequest {

    @NotBlank
    private String password;

    @NotBlank
    private String code;
}
