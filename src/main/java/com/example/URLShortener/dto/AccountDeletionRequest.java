package com.example.URLShortener.dto;

import lombok.Data;

@Data
public class AccountDeletionRequest {

    private String confirmation;
    private String currentPassword;
    private String twoFactorCode;
}
