package com.investmanager.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 100, message = "O nome deve possuir no máximo 100 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Size(max = 150, message = "O e-mail deve possuir no máximo 150 caracteres")
        @Email(message = "O e-mail deve possuir um formato válido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(
                min = 8,
                max = 72,
                message = "A senha deve possuir entre 8 e 72 caracteres"
        )
        String password
) {
}