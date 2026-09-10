package com.example.URLShortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(
            min = 1,
            max = 120,
            message = "Full name must be between 1 and 120 characters"
    )
    private String fullName;
}
