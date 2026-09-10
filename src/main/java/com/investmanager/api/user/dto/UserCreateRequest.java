package com.investmanager.api.user.dto;

import jakarta.persistence.Column;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

public record UserCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 150)
        @Email
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
}
