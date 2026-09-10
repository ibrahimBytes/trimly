package com.example.URLShortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Integer id;

    private String email;

    private String fullName;

    private String profileImageUrl;

    private LocalDateTime createdAt;
}