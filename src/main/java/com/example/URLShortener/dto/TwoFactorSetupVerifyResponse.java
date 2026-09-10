package com.example.URLShortener.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TwoFactorSetupVerifyResponse {
    private boolean enabled;
    private List<String> recoveryCodes;
}
