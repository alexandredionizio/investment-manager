package com.investmanager.api.b3import;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Limites HTTP compatíveis com o tamanho anunciado nas três abas de importação B3. */
@Configuration
public class B3MultipartUploadConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", 10L * 1024 * 1024, 12L * 1024 * 1024, 0);
    }
}
