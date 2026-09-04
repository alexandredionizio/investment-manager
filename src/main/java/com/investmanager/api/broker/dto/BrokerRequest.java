package com.investmanager.api.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrokerRequest(

        @NotBlank(message = "O nome da corretora é obrigatório")
        @Size(max = 100, message = "O nome da corretora deve possuir no máximo 100 caracteres")
        String name
) {
}
