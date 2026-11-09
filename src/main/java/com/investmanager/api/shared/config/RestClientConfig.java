package com.investmanager.api.shared.config;

import com.investmanager.api.shared.integration.IntegrationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class RestClientConfig {
    public static RestClient.Builder builder(IntegrationProperties.Policy policy) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(policy.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(policy.getReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory);
    }
    @Bean public RestClient brapiRestClient(IntegrationProperties properties,
            @Value("${brapi.token}") String token,
            @Value("${brapi.base-url:https://brapi.dev}") String url) {
        return builder(properties.getBrapi()).baseUrl(url)
                .defaultHeaders(headers -> headers.setBearerAuth(token)).build();
    }
    @Bean public RestClient cryptoRestClient(IntegrationProperties properties,
            @Value("${coingecko.base-url:https://api.coingecko.com/api/v3}") String url,
            @Value("${coingecko.api-key:}") String key) {
        var builder = builder(properties.getCoingecko()).baseUrl(url);
        if (!key.isBlank()) builder.defaultHeader("x-cg-demo-api-key", key);
        return builder.build();
    }
    @Bean public RestClient treasuryRestClient(IntegrationProperties properties) {
        return builder(properties.getTreasury()).build();
    }
    @Bean public java.time.Clock valuationClock() {
        return java.time.Clock.system(java.time.ZoneId.of("America/Sao_Paulo"));
    }
    @Bean public RestClient bcbRestClient(IntegrationProperties properties,
            @Value("${bcb.base-url:https://api.bcb.gov.br}") String url) {
        return builder(properties.getBcb()).baseUrl(url).build();
    }
}
