package com.investmanager.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UserResponse(

        Long id,
        String name,
        String email,
        LocalDateTime createdAt
) {
}
